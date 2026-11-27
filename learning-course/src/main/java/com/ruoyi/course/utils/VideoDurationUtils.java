package com.ruoyi.course.utils;

import java.io.ByteArrayOutputStream;
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

    /** 头部探测范围：覆盖 faststart（moov 在文件头）的 MP4 */
    private static final long HEAD_PROBE_BYTES = 1L * 1024 * 1024;
    /** 尾部探测范围：mvhd 在 moov 开头，读到 moov 起点即可；4MB 覆盖绝大多数索引 */
    private static final long TAIL_PROBE_BYTES = 4L * 1024 * 1024;

    /**
     * 从 HTTP(S) 地址读 MP4 时长。
     *
     * 先按 Range 读文件尾部（常规 MP4 的 moov 在末尾），没有再读头部（faststart），
     * 都不行才回退整文件下载。每步只发一个请求，避免 HEAD + 两次 GET 的往返开销。
     */
    public static Integer fromUrl(String url)
    {
        if (url == null || url.isBlank() || !url.startsWith("http"))
        {
            return null;
        }
        try
        {
            String previewKey = previewObjectKey(url);
            if (previewKey != null)
            {
                // /oss-preview/ 是后端代理地址，直接从 OSS 分段拉对象，避免代理下载失败
                long size = AliyunOssUtils.size(previewKey);
                if (size > 0)
                {
                    Integer detected = parseMoov(AliyunOssUtils.readRange(
                            previewKey, Math.max(0, size - TAIL_PROBE_BYTES), size - 1));
                    if (detected == null)
                    {
                        detected = parseMoov(AliyunOssUtils.readRange(
                                previewKey, 0, Math.min(HEAD_PROBE_BYTES, size) - 1));
                    }
                    if (detected != null)
                    {
                        return detected;
                    }
                }
                return downloadAndParse(null, previewKey);
            }

            Integer detected = parseMoov(readTail(url, TAIL_PROBE_BYTES));
            if (detected == null)
            {
                detected = parseMoov(readHttpRange(url, 0, HEAD_PROBE_BYTES - 1, true));
            }
            if (detected != null)
            {
                return detected;
            }
            return downloadAndParse(url, null);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    /** Range 后缀读文件末尾若干字节；服务端不支持 Range 时退化为读前 N 字节 */
    private static byte[] readTail(String url, long bytes) throws IOException
    {
        HttpURLConnection connection = open(url, "GET");
        try
        {
            connection.setRequestProperty("Range", "bytes=-" + bytes);
            connection.setRequestProperty("Accept-Encoding", "identity");
            int code = connection.getResponseCode();
            if (code == 206 || code == 200)
            {
                return readLimited(connection, bytes);
            }
            return null;
        }
        finally
        {
            connection.disconnect();
        }
    }

    /** 整文件下载兜底：Range 不可用、moov 超出探测范围时才走这里 */
    private static Integer downloadAndParse(String url, String previewKey) throws IOException
    {
        File temp = File.createTempFile("video-duration-", ".mp4");
        try
        {
            if (previewKey != null)
            {
                AliyunOssUtils.download(previewKey, temp);
                return fromFile(temp);
            }
            HttpURLConnection connection = open(url, "GET");
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
        finally
        {
            if (temp.exists())
            {
                temp.delete();
            }
        }
    }

    /** HEAD 拿文件大小；服务端不支持 HEAD 返回 -1，交给整文件兜底 */
    private static long contentLength(String url) throws IOException
    {
        HttpURLConnection connection = open(url, "HEAD");
        try
        {
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (connection.getResponseCode() >= 400)
            {
                return -1;
            }
            long length = connection.getContentLengthLong();
            return length > 0 ? length : -1;
        }
        finally
        {
            connection.disconnect();
        }
    }

    /**
     * 按 Range 读取指定字节区间（end 含）。
     * 服务端返回 200 且请求不是从 0 开始时视为不支持 Range，返回 null。
     */
    private static byte[] readHttpRange(String url, long start, long end, boolean fromZero) throws IOException
    {
        if (end < start)
        {
            return null;
        }
        HttpURLConnection connection = open(url, "GET");
        try
        {
            connection.setRequestProperty("Range", "bytes=" + start + "-" + end);
            connection.setRequestProperty("Accept-Encoding", "identity");
            int code = connection.getResponseCode();
            if (code == 206 || (code == 200 && fromZero))
            {
                return readLimited(connection, end - start + 1);
            }
            return null;
        }
        finally
        {
            connection.disconnect();
        }
    }

    private static byte[] readLimited(HttpURLConnection connection, long limit) throws IOException
    {
        try (InputStream in = connection.getInputStream())
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            int len;
            while ((len = in.read(buffer)) != -1)
            {
                total += len;
                if (total > limit)
                {
                    int keep = (int) (limit - (total - len));
                    if (keep > 0)
                    {
                        out.write(buffer, 0, keep);
                    }
                    break;
                }
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        }
    }

    private static HttpURLConnection open(String url, String method) throws IOException
    {
        try
        {
            URL raw = new URL(url);
            // 中文文件名必须重新构造 URI，否则 Java 会按原始字节发请求，服务端 404
            URI encoded = new URI(raw.getProtocol(), raw.getUserInfo(), raw.getHost(), raw.getPort(),
                    raw.getPath(), raw.getQuery(), raw.getRef());
            HttpURLConnection connection = (HttpURLConnection) encoded.toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setRequestMethod(method);
            return connection;
        }
        catch (java.net.URISyntaxException e)
        {
            throw new IOException("非法视频地址: " + url, e);
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

    /** 在字节块里找 moov 并解析 mvhd：头部块按 top-level 对齐扫描，尾部块直接搜 moov 标记 */
    private static Integer parseMoov(byte[] data)
    {
        if (data == null || data.length < 16)
        {
            return null;
        }
        int pos = 0;
        while (pos + 8 <= data.length)
        {
            long size = readUInt32(data, pos);
            long boxSize = size == 1 ? readUInt64(data, pos + 8) : (size == 0 ? data.length - pos : size);
            if ("moov".equals(fourCC(data, pos + 4)))
            {
                return parseMvhd(data, pos + (size == 1 ? 16 : 8), (int) Math.min(data.length, pos + boxSize));
            }
            if (boxSize < 8 || pos + boxSize > data.length)
            {
                break;
            }
            pos += (int) boxSize;
        }
        // 尾部数据不是 top-level 对齐的，直接找 moov 四字符
        int index = indexOfFourCC(data, "moov");
        return index >= 4 ? parseMvhd(data, index + 4, data.length) : null;
    }

    /** 在 moov 的子 box 中找 mvhd，读出 timescale/duration 换算成秒 */
    private static Integer parseMvhd(byte[] data, int start, int end)
    {
        int pos = start;
        while (pos + 8 <= end && pos + 8 <= data.length)
        {
            long size = readUInt32(data, pos);
            if (size < 8 || size > end - pos)
            {
                break;
            }
            int header = size == 1 ? 16 : 8;
            if (pos + header > data.length)
            {
                return null;
            }
            if ("mvhd".equals(fourCC(data, pos + 4)))
            {
                int payload = pos + header;
                if (payload + 4 > data.length)
                {
                    return null;
                }
                int version = data[payload] & 0xFF;
                int tsPos = payload + 4 + (version == 1 ? 16 : 8);
                int durPos = tsPos + 4;
                if (durPos + (version == 1 ? 8 : 4) > data.length)
                {
                    return null;
                }
                long timescale = readUInt32(data, tsPos);
                long duration = version == 1 ? readUInt64(data, durPos) : readUInt32(data, durPos);
                if (timescale <= 0 || duration <= 0)
                {
                    return null;
                }
                long seconds = Math.round(duration * 1.0 / timescale);
                return seconds > 0 ? (int) seconds : null;
            }
            pos += (int) size;
        }
        return null;
    }

    private static long readUInt32(byte[] data, int pos)
    {
        return ((long) (data[pos] & 0xFF) << 24)
                | ((data[pos + 1] & 0xFF) << 16)
                | ((data[pos + 2] & 0xFF) << 8)
                | (data[pos + 3] & 0xFF);
    }

    private static long readUInt64(byte[] data, int pos)
    {
        return (readUInt32(data, pos) << 32) | readUInt32(data, pos + 4);
    }

    private static String fourCC(byte[] data, int pos)
    {
        if (pos + 4 > data.length)
        {
            return "";
        }
        return new String(data, pos, 4, StandardCharsets.ISO_8859_1);
    }

    private static int indexOfFourCC(byte[] data, String fourCC)
    {
        byte[] target = fourCC.getBytes(StandardCharsets.ISO_8859_1);
        for (int i = 0; i + target.length <= data.length; i++)
        {
            boolean matched = true;
            for (int j = 0; j < target.length; j++)
            {
                if (data[i + j] != target[j])
                {
                    matched = false;
                    break;
                }
            }
            if (matched)
            {
                return i;
            }
        }
        return -1;
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
