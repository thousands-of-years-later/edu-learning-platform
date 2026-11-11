package com.ruoyi.community.controller;

import java.util.List;
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
import com.ruoyi.community.domain.Topic;
import com.ruoyi.community.service.ITopicService;

/**
 * 话题管理（后台）
 *
 * 话题由管理员维护，客户端只读（GET /api/v1/topic/list）；用户发帖时从已有话题里选。
 * 页面模板位于 templates/community/topic/，菜单与按钮权限见 sql/learning_topic_menu.sql。
 *
 * @author edu
 */
@Controller
@RequestMapping("/community/topic")
public class TopicController extends BaseController
{
    /** 页面根路径，模板位于 templates/<prefix>/ 下 */
    private String prefix = "community/topic";

    @Autowired
    private ITopicService topicService;

    /**
     * 话题列表页
     */
    @RequiresPermissions("community:topic:view")
    @GetMapping()
    public String topic()
    {
        return prefix + "/topic";
    }

    /**
     * 话题列表数据（分页）
     *
     * @param topic 查询条件：topicName（模糊）、status
     */
    @RequiresPermissions("community:topic:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Topic topic)
    {
        startPage();
        List<Topic> list = topicService.selectAdminList(topic);
        return getDataTable(list);
    }

    /**
     * 新增话题页
     */
    @RequiresPermissions("community:topic:add")
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 新增话题
     */
    @RequiresPermissions("community:topic:add")
    @Log(title = "话题管理", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(Topic topic)
    {
        topic.setCreateBy(getLoginName());
        return toAjax(topicService.insertTopic(topic));
    }

    /**
     * 编辑话题页
     */
    @RequiresPermissions("community:topic:edit")
    @GetMapping("/edit/{topicId}")
    public String edit(@PathVariable("topicId") Long topicId, ModelMap mmap)
    {
        mmap.put("topic", topicService.selectTopicByIdForAdmin(topicId));
        return prefix + "/edit";
    }

    /**
     * 保存话题修改
     */
    @RequiresPermissions("community:topic:edit")
    @Log(title = "话题管理", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(Topic topic)
    {
        topic.setUpdateBy(getLoginName());
        return toAjax(topicService.updateTopic(topic));
    }

    /**
     * 删除话题（批量，逗号分隔；话题下还有文章时会拒绝）
     */
    @RequiresPermissions("community:topic:remove")
    @Log(title = "话题管理", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(topicService.deleteTopicByIds(ids));
    }

    /**
     * 校验话题名是否唯一（表单 remote 校验用，返回 true/false 字符串）
     */
    @PostMapping("/checkTopicNameUnique")
    @ResponseBody
    public String checkTopicNameUnique(String topicName, Long topicId)
    {
        return topicService.checkTopicNameUnique(topicName, topicId) ? "true" : "false";
    }
}
