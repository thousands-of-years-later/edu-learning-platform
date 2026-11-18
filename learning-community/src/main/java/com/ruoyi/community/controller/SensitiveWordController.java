package com.ruoyi.community.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
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
import com.ruoyi.community.domain.SensitiveWord;
import com.ruoyi.community.service.ISensitiveWordService;

/**
 * 违禁词管理（后台）
 *
 * 只用于帖子标题/正文的发布校验；后台可增删改词库，保存后立即刷新内存缓存。
 *
 * @author edu
 */
@Controller
@RequestMapping("/community/sensitive-word")
public class SensitiveWordController extends BaseController
{
    private static final String PREFIX = "community/sensitiveWord";

    @Autowired
    private ISensitiveWordService sensitiveWordService;

    @RequiresPermissions("community:sensitive:view")
    @GetMapping()
    public String word()
    {
        return PREFIX + "/word";
    }

    @RequiresPermissions("community:sensitive:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(SensitiveWord query)
    {
        startPage();
        return getDataTable(sensitiveWordService.selectSensitiveWordList(query));
    }

    @RequiresPermissions("community:sensitive:add")
    @GetMapping("/add")
    public String add()
    {
        return PREFIX + "/add";
    }

    @Log(title = "违禁词", businessType = BusinessType.INSERT)
    @RequiresPermissions("community:sensitive:add")
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(SensitiveWord word)
    {
        word.setCreateBy(getLoginName());
        return toAjax(sensitiveWordService.insertSensitiveWord(word));
    }

    @RequiresPermissions("community:sensitive:edit")
    @GetMapping("/edit/{wordId}")
    public String edit(@PathVariable("wordId") Long wordId, ModelMap mmap)
    {
        mmap.put("word", sensitiveWordService.selectSensitiveWordById(wordId));
        return PREFIX + "/edit";
    }

    @Log(title = "违禁词", businessType = BusinessType.UPDATE)
    @RequiresPermissions("community:sensitive:edit")
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(SensitiveWord word)
    {
        word.setUpdateBy(getLoginName());
        return toAjax(sensitiveWordService.updateSensitiveWord(word));
    }

    @Log(title = "违禁词", businessType = BusinessType.DELETE)
    @RequiresPermissions("community:sensitive:remove")
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(sensitiveWordService.deleteSensitiveWordByIds(ids));
    }
}
