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

    // ==================== 后台管理 ====================

    /**
     * 后台话题列表（分页由控制器驱动）：含停用话题，可按名称/状态筛选
     */
    public List<Topic> selectAdminList(Topic query);

    /**
     * 后台按ID查话题（含停用），不存在返回 null
     */
    public Topic selectTopicByIdForAdmin(Long topicId);

    /**
     * 新增话题
     */
    public int insertTopic(Topic topic);

    /**
     * 修改话题
     */
    public int updateTopic(Topic topic);

    /**
     * 批量删除话题（逻辑删除）
     *
     * 话题下还有文章时拒绝删除：文章的关联会变孤儿、话题页也打不开。
     *
     * @param ids 逗号分隔的话题ID
     */
    public int deleteTopicByIds(String ids);

    /**
     * 话题名唯一性校验（同名返回 false）
     *
     * @param topicName 话题名
     * @param topicId   编辑时排除自己；新增传 null
     */
    public boolean checkTopicNameUnique(String topicName, Long topicId);
}
