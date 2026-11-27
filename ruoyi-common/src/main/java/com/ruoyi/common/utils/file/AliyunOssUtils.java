package com.ruoyi.common.utils.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.config.OssConfig;
import com.ruoyi.common.utils.StringUtils;

/**
 * 阿里云 OSS 上传工具
 *
 * 由 FileUploadUtils 在 OSS 开关打开时调用。
 * 只负责「把流转成对象」，扩展名白名单、大小上限等校验仍在 FileUploadUtils 里，
 * 不要在这里重复做，也不要绕过。
 *
 * @author edu
 */
public class AliyunOssUtils
{
    /** OSS 客户端，整个应用共用一个（OSSClient 是线程安全的） */
    private static volatile OSS client;

    /**
     * 上传文件到 OSS
     *
     * @param file 上传的文件
     * @param key  对象名（形如 upload/2026/10/14/xxx.mp4）
     * @return 可直接访问的完整 URL
     * @throws IOException 上传失败
     */
    public static String upload(MultipartFile file, String key) throws IOException
    {
        if (!OssConfig.isReady())
        {
            throw new IOException("OSS 配置不完整，缺少：" + OssConfig.describeMissing());
        }

        try (InputStream in = file.getInputStream())
        {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(file.getSize());
            if (StringUtils.isNotEmpty(file.getContentType()))
            {
                meta.setContentType(file.getContentType());
            }
            getClient().putObject(OssConfig.getBucketName(), key, in, meta);
        }
        catch (Exception e)
        {
            // 不做静默降级到本地：否则文件一半在 OSS、一半在本地，排查成本极高
            throw new IOException("上传到 OSS 失败：" + e.getMessage(), e);
        }

        return OssConfig.resolveUrlPrefix() + "/" + key;
    }

    /**
     * 下载 OSS 对象到本地临时文件（供视频时长探测使用）
     */
    public static void download(String key, File target) throws IOException
    {
        if (!OssConfig.isReady())
        {
            throw new IOException("OSS 配置不完整，缺少：" + OssConfig.describeMissing());
        }
        try (OSSObject object = getClient().getObject(OssConfig.getBucketName(), key);
             InputStream in = object.getObjectContent();
             OutputStream out = new FileOutputStream(target))
        {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1)
            {
                out.write(buffer, 0, len);
            }
        }
        catch (Exception e)
        {
            throw new IOException("从 OSS 下载失败：" + e.getMessage(), e);
        }
    }

    /**
     * 删除 OSS 对象（供后续「删记录时一起删文件」使用，本期未接入）
     *
     * @param key 对象名
     */
    public static void delete(String key)
    {
        if (OssConfig.isReady())
        {
            getClient().deleteObject(OssConfig.getBucketName(), key);
        }
    }

    private static OSS getClient()
    {
        if (client == null)
        {
            synchronized (AliyunOssUtils.class)
            {
                if (client == null)
                {
                    client = new OSSClientBuilder().build(
                            OssConfig.getEndpoint(),
                            OssConfig.getAccessKeyId(),
                            OssConfig.getAccessKeySecret());
                }
            }
        }
        return client;
    }
}