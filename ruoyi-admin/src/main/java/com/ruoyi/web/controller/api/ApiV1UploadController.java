package com.ruoyi.web.controller.api;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.config.OssConfig;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.file.AliyunOssUtils;

/**
 * 客户端图片上传（/api/v1/upload/**）
 *
 * 只服务聊天图片：存到 OSS 的 agent/chat/{userId}/ 下，
 * 供 /agent/chat、/agent/chat/stream 的 imageUrls 引用。
 * 后台管理用的通用上传仍走 /common/upload，两边不要混用。
 */
@RestController
@RequestMapping("/api/v1/upload")
public class ApiV1UploadController
{
    private static final Logger log = LoggerFactory.getLogger(ApiV1UploadController.class);

    /** 单张上限 5MB：远低于模型侧 32MB，控制上传耗时 */
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    /** 单用户每天最多 20 张 */
    private static final int DAILY_IMAGE_LIMIT = 20;

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 当日计数。只在本实例内存里，容器重启会清零；
     * 这是防刷的软限制，不追求跨实例精确。
     */
    private static final Map<String, AtomicInteger> DAILY_COUNTER = new ConcurrentHashMap<>();

    @PostMapping("/image")
    public AjaxResult uploadImage(@RequestParam("file") MultipartFile file)
    {
        Long userId = ApiContext.getUserId();
        if (userId == null)
        {
            return AjaxResult.error("请先登录");
        }
        if (file == null || file.isEmpty())
        {
            return AjaxResult.error("文件不能为空");
        }
        if (file.getSize() > MAX_IMAGE_SIZE)
        {
            return AjaxResult.error("图片不能超过 5MB");
        }
        if (!OssConfig.isReady())
        {
            return AjaxResult.error("OSS 未配置，无法上传");
        }

        // 按文件头判断真实类型，不信任扩展名和 Content-Type
        String ext = sniffImageType(file);
        if (ext == null)
        {
            return AjaxResult.error("只支持 JPEG、PNG、GIF、WebP 图片");
        }

        String counterKey = userId + ":" + LocalDate.now().format(DAY);
        AtomicInteger counter = DAILY_COUNTER.computeIfAbsent(counterKey, key -> new AtomicInteger());
        if (counter.incrementAndGet() > DAILY_IMAGE_LIMIT)
        {
            counter.decrementAndGet();
            return AjaxResult.error("今日上传图片已达上限（" + DAILY_IMAGE_LIMIT + " 张）");
        }

        String objectKey = "agent/chat/" + userId + "/" + LocalDate.now().format(MONTH) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try
        {
            String url = AliyunOssUtils.upload(file, objectKey);
            AjaxResult ajax = AjaxResult.success();
            ajax.put("url", url);
            ajax.put("objectKey", objectKey);
            return ajax;
        }
        catch (Exception e)
        {
            // 上传失败要把计数退回去，否则用户重试几次就被误限流
            counter.decrementAndGet();
            log.error("聊天图片上传失败: objectKey={}", objectKey, e);
            return AjaxResult.error("图片上传失败，请稍后重试");
        }
    }

    private String sniffImageType(MultipartFile file)
    {
        try (InputStream in = file.getInputStream())
        {
            byte[] head = in.readNBytes(12);
            if (head.length >= 3
                    && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF)
            {
                return "jpg";
            }
            if (head.length >= 8
                    && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G')
            {
                return "png";
            }
            if (head.length >= 6
                    && head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8')
            {
                return "gif";
            }
            if (head.length >= 12
                    && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P')
            {
                return "webp";
            }
            return null;
        }
        catch (IOException e)
        {
            log.warn("读取图片文件头失败", e);
            return null;
        }
    }
}
