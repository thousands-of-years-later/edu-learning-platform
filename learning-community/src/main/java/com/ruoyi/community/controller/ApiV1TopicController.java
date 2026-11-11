package com.ruoyi.community.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.community.domain.Topic;
import com.ruoyi.community.service.ITopicService;

/**
 * 话题 接口 v1（Token 认证）
 *
 * 对应设计稿：
 *   17_话题页  → 话题详情（名称 + 简介 + 文章数）+ 该话题下的文章列表
 *   16_文章详情 → 标题下方的 #Android #Kotlin 标签（由 /api/v1/post/info 返回）
 *
 * 话题由管理员创建，客户端只读；发帖时从话题列表里选（POST /api/v1/post/add 的 topicIds 参数）。
 *
 * 话题下的文章列表复用文章接口：GET /api/v1/post/list?topicId=xxx
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/topic")
public class ApiV1TopicController extends BaseController
{
    @Autowired
    private ITopicService topicService;

    /**
     * 话题列表（按文章数倒序）
     *
     * @param keyword 可选：话题名模糊搜索
     *        分页参数：pageNum / pageSize
     * @return {total, rows:[{topicId, topicName, topicDesc, postCount, createTime}]}
     */
    @GetMapping("/list")
    public TableDataInfo list(String keyword)
    {
        startPage();
        List<Topic> list = topicService.selectTopicList(keyword);
        return getDataTable(list);
    }

    /**
     * 话题详情（话题页顶部：话题名 + 简介 + 文章数）
     *
     * @param topicId 话题ID
     * @return {code, msg, data:{topicId, topicName, topicDesc, postCount}}；
     *         文章列表调 GET /api/v1/post/list?topicId=xxx
     */
    @GetMapping("/{topicId}")
    public AjaxResult detail(@PathVariable("topicId") Long topicId)
    {
        Topic topic = topicService.selectTopicById(topicId);
        if (topic == null)
        {
            throw new ServiceException("话题不存在或已下架");
        }
        return AjaxResult.success(topic);
    }
}
