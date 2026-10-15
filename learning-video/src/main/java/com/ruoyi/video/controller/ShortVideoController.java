package com.ruoyi.video.controller;

import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.video.domain.ShortVideo;
import com.ruoyi.video.service.IShortVideoService;

/**
 * 短视频 控制器
 *
 * 短视频由管理员在后台录入，没有分类、没有章节，也不区分「谁的」，
 * 因此写操作全部由菜单权限把关，不额外做归属校验。
 *
 * 列表接口按约定不加权限注解：所有登录用户都能读到视频列表。
 *
 * 页面模板位于 templates/video/shortvideo/ 下，菜单与按钮权限见 sql/learning_short_video_menu.sql。
 *
 * @author edu
 */
@Controller
@RequestMapping("/video/shortvideo")
public class ShortVideoController extends BaseController
{
    /** 页面根路径，模板位于 templates/<prefix>/ 下 */
    private String prefix = "video/shortvideo";

    @Autowired
    private IShortVideoService shortVideoService;

    /**
     * 短视频列表页
     */
    @RequiresPermissions("video:shortvideo:view")
    @GetMapping()
    public String shortvideo()
    {
        return prefix + "/shortvideo";
    }

    /**
     * 新增短视频页
     */
    @RequiresPermissions("video:shortvideo:add")
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 修改短视频页
     */
    @RequiresPermissions("video:shortvideo:edit")
    @GetMapping("/edit/{videoId}")
    public String edit(@PathVariable("videoId") Long videoId, ModelMap mmap)
    {
        mmap.put("shortVideo", shortVideoService.selectShortVideoById(videoId));
        return prefix + "/edit";
    }

    /**
     * 查询短视频列表
     */
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(ShortVideo shortVideo)
    {
        startPage();
        List<ShortVideo> list = shortVideoService.selectShortVideoList(shortVideo);
        return getDataTable(list);
    }

    /**
     * 导出短视频列表
     */
    @Log(title = "短视频", businessType = BusinessType.EXPORT)
    @RequiresPermissions("video:shortvideo:export")
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(ShortVideo shortVideo)
    {
        List<ShortVideo> list = shortVideoService.selectShortVideoList(shortVideo);
        ExcelUtil<ShortVideo> util = new ExcelUtil<ShortVideo>(ShortVideo.class);
        return util.exportExcel(list, "短视频数据");
    }

    /**
     * 删除短视频
     */
    @RequiresPermissions("video:shortvideo:remove")
    @Log(title = "短视频", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(shortVideoService.deleteShortVideoByIds(ids));
    }

    /**
     * 新增保存短视频
     */
    @RequiresPermissions("video:shortvideo:add")
    @Log(title = "短视频", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Validated ShortVideo shortVideo)
    {
        if (!shortVideoService.checkVideoTitleUnique(shortVideo))
        {
            return error("新增短视频失败，视频标题已存在");
        }
        // 上传者永远取当前登录用户，不接受表单传入的 userId
        shortVideo.setUserId(getUserId());
        shortVideo.setCreateBy(getLoginName());
        return toAjax(shortVideoService.insertShortVideo(shortVideo));
    }

    /**
     * 修改保存短视频
     */
    @RequiresPermissions("video:shortvideo:edit")
    @Log(title = "短视频", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Validated ShortVideo shortVideo)
    {
        if (!shortVideoService.checkVideoTitleUnique(shortVideo))
        {
            return error("修改短视频失败，视频标题已存在");
        }
        shortVideo.setUpdateBy(getLoginName());
        return toAjax(shortVideoService.updateShortVideo(shortVideo));
    }

    /**
     * 校验视频标题唯一性
     */
    @PostMapping("/checkVideoTitleUnique")
    @ResponseBody
    public boolean checkVideoTitleUnique(ShortVideo shortVideo)
    {
        return shortVideoService.checkVideoTitleUnique(shortVideo);
    }
}