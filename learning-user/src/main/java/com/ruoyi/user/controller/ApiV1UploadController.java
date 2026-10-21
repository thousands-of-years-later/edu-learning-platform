package com.ruoyi.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.config.ServerConfig;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.utils.file.MimeTypeUtils;

/**
 * 文件上传 接口 v1（Token 认证，给小程序用）
 *
 * 为什么单独做一个：后台的 /common/upload 认的是 Shiro 会话（Cookie），小程序没有那套会话，
 * 调了会被当成未登录。本接口走 /api/v1/** 的 Token 鉴权（ApiTokenFilter），
 * 上传逻辑与后台完全一致（存本地或 OSS 由 file.oss.enabled 决定）。
 *
 * 只收【图片】（头像等用），上限 5MB：
 *   - 白名单复用 MimeTypeUtils.IMAGE_EXTENSION（bmp/gif/jpg/jpeg/png），
 *     校验逻辑与后台一致，不接受 html/svg/jsp 这类可执行内容；
 *   - 大小在 Controller 先卡 5MB，比 FileUploadUtils 的 200MB 更严（那是给课程视频用的）。
 *
 * 返回的 url 是完整可访问地址：开了 OSS 就是 https://bucket.oss-.../upload/xxx.png，
 * 本地模式是 服务器地址 + /profile/upload/xxx.png。
 * 这个地址可以直接存进 sys_user.avatar（见 /api/v1/user/profile）。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1")
public class ApiV1UploadController extends BaseController
{
    /** 图片上传大小上限：5MB（头像/封面这类图片够用；课程视频走后台 /common/upload） */
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024L;

    @Autowired
    private ServerConfig serverConfig;

    /**
     * 上传图片（头像用）
     *
     * 表单方式（multipart/form-data）：字段名 file
     *
     * @param file 图片文件
     * @return {url: 完整可访问地址, fileName: 存储的相对路径或完整 OSS 地址}
     */
    @PostMapping("/upload")
    public AjaxResult upload(@RequestParam("file") MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_SIZE)
        {
            throw new ServiceException("图片不能超过 5MB");
        }
        try
        {
            // 传 IMAGE_EXTENSION 这个常量本身（FileUploadUtils 里用 == 判断，会给出"图片格式"的专用报错）
            String fileName = FileUploadUtils.upload(RuoYiConfig.getUploadPath(), file, MimeTypeUtils.IMAGE_EXTENSION);
            // OSS 模式下 fileName 已经是完整地址，再拼一次域名会得到 http://hosthttps://... 的坏地址
            String url = fileName.startsWith("http") ? fileName : serverConfig.getUrl() + fileName;
            AjaxResult ajax = AjaxResult.success();
            ajax.put("url", url);
            ajax.put("fileName", fileName);
            return ajax;
        }
        catch (Exception e)
        {
            // 类型/文件名长度/读写异常统一翻译成业务异常，客户端只会看到一句人话
            throw new ServiceException(e.getMessage());
        }
    }
}
