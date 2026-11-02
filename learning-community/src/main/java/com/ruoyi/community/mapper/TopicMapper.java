package com.ruoyi.community.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.community.domain.Topic;

/**
 * 话题 数据层
 *
 * 话题文章数实时用 learning_post_topic + learning_post（只算已发布）count 出来，
 * 不在话题表上放冗余计数字段 —— 文章发布/删除/审核状态变化都会改这个数，
 * 冗余字段要维护四个入口，容易漂移。
 *
 * @author edu
 */
public interface TopicMapper
{
    /**
     * 话题列表（分页由控制器驱动），按文章数倒序 + 话题ID 兜底
     *
     * @param keyword 可选：按话题名模糊搜索
     */
    public List<Topic> selectTopicList(@Param("keyword") String keyword);

    /**
     * 话题详情；不存在/停用/已删除返回 null
     */
    public Topic selectTopicById(@Param("topicId") Long topicId);

    /**
     * 某篇文章挂的话题（文章详情显示 #标签 用），按话题ID升序
     */
    public List<Topic> selectTopicsByPostId(@Param("postId") Long postId);

    /**
     * 批量给文章挂话题（重复挂同一话题由唯一索引忽略，不报错）
     *
     * @param postId   文章ID
     * @param topicIds 话题ID集合（调用方已去重、已校验存在）
     */
    public int insertPostTopics(@Param("postId") Long postId, @Param("topicIds") List<Long> topicIds);

    /**
     * 统计给定话题ID里"存在且可用"的数量（用于校验客户端传的话题ID）
     */
    public int countValidTopics(@Param("topicIds") List<Long> topicIds);

    /**
     * 文章被删除时清掉它的全部话题关联（硬删除）
     *
     * 不清的话 learning_post_topic 会留下指向已删文章的孤儿记录；
     * 话题页的"文章数"虽然只统计已发布文章、不会算错，但表里的垃圾会一直累积。
     */
    public int deletePostTopicsByPostIds(Long[] postIds);
}
