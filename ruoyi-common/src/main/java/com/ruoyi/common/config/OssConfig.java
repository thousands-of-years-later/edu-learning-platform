package com.ruoyi.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置（前缀 file.oss）。
 *
 * 与 RuoYiConfig 同款写法：@Component + 静态字段，
 * 这样纯静态工具类 FileUploadUtils / AliyunOssUtils 不用注入就能读到配置。
 *
 * 密钥一律走环境变量注入，不要写进 application.yml：
 *   OSS_ENABLED / OSS_ENDPOINT / OSS_BUCKET / OSS_ACCESS_KEY_ID / OSS_ACCESS_KEY_SECRET / OSS_URL_PREFIX
 *
 * @author edu
 */
@Component
@ConfigurationProperties(prefix = "file.oss")
public class OssConfig
{
    /** 总开关：false 时完全退回本地存储 */
    private static boolean enabled = false;

    /** 地域节点，如 oss-cn-hangzhou.aliyuncs.com */
    private static String endpoint;

    /** Bucket 名称 */
    private static String bucketName;

    private static String accessKeyId;

    private static String accessKeySecret;

    /**
     * 公共读时的访问前缀，如 https://my-bucket.oss-cn-hangzhou.aliyuncs.com
     * 留空则按 https://{bucket}.{endpoint} 自动推导。
     */
    private static String urlPrefix;

    /**
     * 配置是否齐备。开了开关但缺配置，应用仍可启动，
     * 首次上传时由 AliyunOssUtils 抛出明确错误（不静默回落本地）。
     */
    public static boolean isReady()
    {
        return enabled
                && endpoint != null && !endpoint.isEmpty()
                && bucketName != null && !bucketName.isEmpty()
                && accessKeyId != null && !accessKeyId.isEmpty()
                && accessKeySecret != null && !accessKeySecret.isEmpty();
    }

    /** 配置缺失时给出明确的缺项说明，便于定位 */
    public static String describeMissing()
    {
        StringBuilder sb = new StringBuilder();
        if (endpoint == null || endpoint.isEmpty()) sb.append("file.oss.endpoint(OSS_ENDPOINT) ");
        if (bucketName == null || bucketName.isEmpty()) sb.append("file.oss.bucketName(OSS_BUCKET) ");
        if (accessKeyId == null || accessKeyId.isEmpty()) sb.append("file.oss.accessKeyId(OSS_ACCESS_KEY_ID) ");
        if (accessKeySecret == null || accessKeySecret.isEmpty()) sb.append("file.oss.accessKeySecret(OSS_ACCESS_KEY_SECRET) ");
        return sb.toString().trim();
    }

    /** 拼可访问地址用的前缀（不带结尾斜杠） */
    public static String resolveUrlPrefix()
    {
        if (urlPrefix != null && !urlPrefix.isEmpty())
        {
            return urlPrefix.endsWith("/") ? urlPrefix.substring(0, urlPrefix.length() - 1) : urlPrefix;
        }
        return "https://" + bucketName + "." + endpoint;
    }

    /** bucket 直连前缀（配置了 CDN 时，历史直连地址仍需被识别） */
    private static String resolveDirectUrlPrefix()
    {
        if (bucketName == null || bucketName.isEmpty() || endpoint == null || endpoint.isEmpty())
        {
            return null;
        }
        return "https://" + bucketName + "." + endpoint;
    }

    /**
     * 是否是本系统 OSS 上已有对象的地址。
     *
     * 与 enabled 无关：开关只决定新上传写到哪里，不决定历史地址是否合法。
     * 同时认 urlPrefix（可能是 CDN）和 bucket 直连前缀，不限制对象路径
     * （历史数据里存在 /edu/short/... 这类非 /upload/ 的对象）。
     */
    public static boolean isOssUrl(String url)
    {
        if (url == null || url.isEmpty())
        {
            return false;
        }
        if (urlPrefix != null && !urlPrefix.isEmpty()
                && url.startsWith(resolveUrlPrefix() + "/"))
        {
            return true;
        }
        String direct = resolveDirectUrlPrefix();
        return direct != null && url.startsWith(direct + "/");
    }

    public static boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        OssConfig.enabled = enabled;
    }

    public static String getEndpoint()
    {
        return endpoint;
    }

    public void setEndpoint(String endpoint)
    {
        OssConfig.endpoint = endpoint;
    }

    public static String getBucketName()
    {
        return bucketName;
    }

    public void setBucketName(String bucketName)
    {
        OssConfig.bucketName = bucketName;
    }

    public static String getAccessKeyId()
    {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId)
    {
        OssConfig.accessKeyId = accessKeyId;
    }

    public static String getAccessKeySecret()
    {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret)
    {
        OssConfig.accessKeySecret = accessKeySecret;
    }

    public static String getUrlPrefix()
    {
        return urlPrefix;
    }

    public void setUrlPrefix(String urlPrefix)
    {
        OssConfig.urlPrefix = urlPrefix;
    }
}