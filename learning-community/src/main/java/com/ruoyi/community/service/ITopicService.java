package com.ruoyi.community.service;

import java.util.List;
import com.ruoyi.community.domain.Topic;

/**
 * 话题 服务层（设计稿 17_话题页 / 16_文章详情的话题标签）
 *
 * @author edu
 */
public interface ITopicService
{
    /**
     * 话题列表（分页由控制器驱动）
     *
     * @param keyword 可选：话题名模糊搜索
     */
    public List<Topic> selectTopicList(String keyword);

    /**
     * 话题详情（不存在/停用返回 null，由控制器决定文案）
     */
    public Topic selectTopicById(Long topicId);

    /**
     * 校验话题可访问（存在 + 未删除 + 状态正常），供"按话题查文章"在 startPage 之前调用
     */
    public void checkTopic(Long topicId);

    /**
     * 某篇文章挂的话题（文章详情返回 #标签）
     */
    public List<Topic> selectTopicsByPostId(Long postId);

    /**
     * 发帖时绑定话题
     *
     * @param postId   刚发布的文章ID
     * @param topicIds 客户端传的逗号分隔话题ID（可为空字符串 = 不选话题）
     * @return 实际绑定的条数
     */
    public int bindTopics(Long postId, String topicIds);

    /**
     * 校验话题ID串是否合法（存在且可用、数量不超过上限），发帖前调用
     *
     * @return 解析后的去重话题ID列表（空表示没选话题）
     */
    public List<Long> parseAndCheckTopicIds(String topicIds);
}
