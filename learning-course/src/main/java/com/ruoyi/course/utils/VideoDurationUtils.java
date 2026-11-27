package com.ruoyi.course.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.utils.file.AliyunOssUtils;

/**
 * 视频时长探测：只处理 MP4，纯 Java，不依赖 ffmpeg。
 *
 * 用于后台上传视频后自动回填 course_video.duration；
 * AVI/RMVB 等非 MP4 容器返回 null，保留人工填写或客户端上报。
 */
public final class VideoDurationUtils
{
    private static final long MAX_DOWNLOAD_BYTES = 500L * 1024 * 1024;

    private VideoDurationUtils()
    {
    }

    /** 从上传临时文件读 MP4 时长；非 MP4 或解析失败返回 null */
    public static Integer fromMultipart(MultipartFile file)
    {
        if (file == null || file.isEmpty() || !looksLikeMp4(file.getOriginalFilename(), file.getContentType()))
        {
            return null;
        }
        File temp = null;
        try
        {
            temp = File.createTempFile("video-duration-", ".mp4");
            // 不用 transferTo：MultipartFile 的临时文件可能已经被上传逻辑移动/消费
            try (InputStream in = file.getInputStream();
                 FileOutputStream out = new FileOutputStream(temp))
            {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1)
                {
                    out.write(buffer, 0, len);
                }
            }
            return fromFile(temp);
        }
        catch (Exception e)
        {
            return null;
        }
        finally
        {
            if (temp != null && temp.exists())
            {
                temp.delete();
            }
        }
    }

    /** 从 HTTP(S) 地址下载后读 MP4 时长；失败返回 null */
    public static Integer fromUrl(String url)
    {
        if (url == null || url.isBlank() || !url.startsWith("http"))
        {
            return null;
        }
        File temp = null;
        try
        {
            temp = File.createTempFile("video-duration-", ".mp4");
            String previewKey = previewObjectKey(url);
            if (previewKey != null)
            {
                // /oss-preview/ 是后端代理地址，直接从 OSS 拉对象，避免代理下载失败
                AliyunOssUtils.download(previewKey, temp);
                return fromFile(temp);
            }
            URL raw = new URL(url);
            // 中文文件名必须重新构造 URI，否则 Java 会按原始字节发请求，服务端 404
            URI encoded = new URI(raw.getProtocol(), raw.getUserInfo(), raw.getHost(), raw.getPort(),
                    raw.getPath(), raw.getQuery(), raw.getRef());
            HttpURLConnection connection = (HttpURLConnection) encoded.toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(temp))
            {
                byte[] buffer = new byte[8192];
                long total = 0;
                int len;
                while ((len = in.read(buffer)) != -1)
                {
                    total += len;
                    if (total > MAX_DOWNLOAD_BYTES)
                    {
                        return null;
                    }
                    out.write(buffer, 0, len);
                }
            }
            return fromFile(temp);
        }
        catch (Exception ignored)
        {
            return null;
        }
        finally
        {
            if (temp != null && temp.exists())
            {
                temp.delete();
            }
        }
    }

    /** 解析本地 MP4 文件时长（秒）：直接读 moov/mvhd，不依赖第三方库 */
    public static Integer fromFile(File file)
    {
        if (file == null || !file.exists() || file.length() == 0)
        {
            return null;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r"))
        {
            long moov = findTopLevelBox(raf, "moov");
            if (moov < 0)
            {
                return null;
            }
            long moovHeader = boxHeaderSize(raf, moov);
            long moovEnd = moov + readBoxSize(raf, moov);
            long mvhd = findChildBox(raf, moov + moovHeader, moovEnd, "mvhd");
            if (mvhd < 0)
            {
                return null;
            }
            long mvhdHeader = boxHeaderSize(raf, mvhd);
            raf.seek(mvhd + mvhdHeader);
            int version = raf.readUnsignedByte();
            raf.skipBytes(3);
            if (version == 1)
            {
                raf.skipBytes(16);
            }
            else
            {
                raf.skipBytes(8);
            }
            long timescale = readUInt32(raf);
            long duration = version == 1 ? readUInt64(raf) : readUInt32(raf);
            if (timescale <= 0 || duration <= 0)
            {
                return null;
            }
            long seconds = Math.round(duration * 1.0 / timescale);
            return seconds > 0 ? (int) seconds : null;
        }
        catch (Exception e)
        {
            // 非 MP4 / moov 损坏 / 加密视频：交给人工或客户端上报
            return null;
        }
    }

    private static long findTopLevelBox(RandomAccessFile raf, String target) throws IOException
    {
        long length = raf.length();
        long pos = 0;
        while (pos + 8 <= length)
        {
            long size = readBoxSize(raf, pos);
            if (size < 8)
            {
                break;
            }
            raf.seek(pos + 4);
            String type = readFourCC(raf);
            if (target.equals(type))
            {
                return pos;
            }
            pos += size;
        }
        return -1;
    }

    private static long findChildBox(RandomAccessFile raf, long start, long end, String target) throws IOException
    {
        long pos = start;
        while (pos + 8 <= end)
        {
            long size = readBoxSize(raf, pos);
            if (size < 8)
            {
                break;
            }
            raf.seek(pos + 4);
            String type = readFourCC(raf);
            if (target.equals(type))
            {
                return pos;
            }
            pos += size;
        }
        return -1;
    }

    /** 返回 box 总大小（含 8/16 字节头） */
    private static long readBoxSize(RandomAccessFile raf, long pos) throws IOException
    {
        raf.seek(pos);
        long size = readUInt32(raf);
        String type = readFourCC(raf);
        if ("uuid".equals(type))
        {
            return size;
        }
        if (size == 1)
        {
            return readUInt64(raf);
        }
        if (size == 0)
        {
            return raf.length() - pos;
        }
        return size;
    }

    /** box 头长度：普通 8 字节，size=1 时 16 字节 */
    private static long boxHeaderSize(RandomAccessFile raf, long pos) throws IOException
    {
        raf.seek(pos);
        long size = readUInt32(raf);
        return size == 1 ? 16 : 8;
    }

    private static String readFourCC(RandomAccessFile raf) throws IOException
    {
        byte[] bytes = new byte[4];
        raf.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private static long readUInt32(RandomAccessFile raf) throws IOException
    {
        return raf.readInt() & 0xFFFFFFFFL;
    }

    private static long readUInt64(RandomAccessFile raf) throws IOException
    {
        long high = readUInt32(raf);
        long low = readUInt32(raf);
        return (high << 32) | low;
    }

    private static boolean looksLikeMp4(String filename, String contentType)
    {
        if (contentType != null && contentType.toLowerCase().contains("mp4"))
        {
            return true;
        }
        return filename != null && filename.toLowerCase().endsWith(".mp4");
    }

    /** 从 /oss-preview/{key} 中取出 OSS 对象 key；不是该前缀返回 null */
    private static String previewObjectKey(String url)
    {
        int index = url.indexOf("/oss-preview/");
        if (index < 0)
        {
            return null;
        }
        String key = url.substring(index + "/oss-preview/".length());
        int query = key.indexOf('?');
        if (query >= 0)
        {
            key = key.substring(0, query);
        }
        return URLDecoder.decode(key, StandardCharsets.UTF_8);
    }
}
