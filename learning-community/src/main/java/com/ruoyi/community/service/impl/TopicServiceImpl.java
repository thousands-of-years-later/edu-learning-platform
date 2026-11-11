package com.ruoyi.community.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.community.domain.Topic;
import com.ruoyi.community.mapper.TopicMapper;
import com.ruoyi.community.service.ITopicService;

/**
 * 话题 服务层实现
 *
 * 几条规矩：
 *   1. 话题只能管理员建，用户发帖时"选"（客户端传 topicId 列表）；
 *   2. 一篇文章最多选 5 个话题（多余直接报错，不静默截断，避免用户以为都选上了）；
 *   3. 绑定用 insert ignore：同一话题重复提交由唯一索引吃掉，不会报错；
 *   4. 话题文章数实时算（只算已发布文章），不在话题表上放冗余计数。
 *
 * @author edu
 */
@Service
public class TopicServiceImpl implements ITopicService
{
    @Autowired
    private TopicMapper topicMapper;

    @Override
    public List<Topic> selectTopicList(String keyword)
    {
        return topicMapper.selectTopicList(StringUtils.trim(keyword));
    }

    @Override
    public Topic selectTopicById(Long topicId)
    {
        if (topicId == null)
        {
            throw new ServiceException("话题ID不能为空");
        }
        return topicMapper.selectTopicById(topicId);
    }

    @Override
    public void checkTopic(Long topicId)
    {
        if (selectTopicById(topicId) == null)
        {
            throw new ServiceException("话题不存在或已下架");
        }
    }

    @Override
    public List<Topic> selectTopicsByPostId(Long postId)
    {
        if (postId == null)
        {
            return new ArrayList<>();
        }
        return topicMapper.selectTopicsByPostId(postId);
    }

    @Override
    @Transactional
    public int bindTopics(Long postId, String topicIds)
    {
        List<Long> ids = parseAndCheckTopicIds(topicIds);
        if (ids.isEmpty())
        {
            return 0;
        }
        return topicMapper.insertPostTopics(postId, ids);
    }

    @Override
    public List<Long> parseAndCheckTopicIds(String topicIds)
    {
        // 客户端可能传 "1,2,3"；空白/空串 = 没选话题
        String raw = StringUtils.trim(topicIds);
        if (StringUtils.isEmpty(raw))
        {
            return new ArrayList<>();
        }
        // LinkedHashSet 去重且保留顺序：用户选了两次同一个话题不算错
        Set<Long> unique = new LinkedHashSet<>();
        for (String part : raw.split(","))
        {
            String text = StringUtils.trim(part);
            if (StringUtils.isEmpty(text))
            {
                continue;
            }
            Long id;
            try
            {
                id = Long.valueOf(text);
            }
            catch (NumberFormatException e)
            {
                throw new ServiceException("话题参数不合法");
            }
            unique.add(id);
        }
        if (unique.isEmpty())
        {
            return new ArrayList<>();
        }
        if (unique.size() > Topic.MAX_TOPICS_PER_POST)
        {
            throw new ServiceException("一篇文章最多选择 " + Topic.MAX_TOPICS_PER_POST + " 个话题");
        }
        List<Long> ids = new ArrayList<>(unique);
        // 校验全部存在且可用：少一个就报错，不静默丢掉（否则用户以为挂上了）
        if (topicMapper.countValidTopics(ids) != ids.size())
        {
            throw new ServiceException("存在无效的话题，请重新选择");
        }
        return ids;
    }

    // ==================== 后台管理 ====================

    @Override
    public List<Topic> selectAdminList(Topic query)
    {
        Topic condition = query == null ? new Topic() : query;
        condition.setTopicName(StringUtils.trim(condition.getTopicName()));
        return topicMapper.selectAdminList(condition);
    }

    @Override
    public Topic selectTopicByIdForAdmin(Long topicId)
    {
        if (topicId == null)
        {
            throw new ServiceException("话题ID不能为空");
        }
        return topicMapper.selectTopicByIdForAdmin(topicId);
    }

    @Override
    @Transactional
    public int insertTopic(Topic topic)
    {
        checkTopic(topic, null);
        return topicMapper.insertTopic(topic);
    }

    @Override
    @Transactional
    public int updateTopic(Topic topic)
    {
        if (topic.getTopicId() == null)
        {
            throw new ServiceException("话题ID不能为空");
        }
        if (topicMapper.selectTopicByIdForAdmin(topic.getTopicId()) == null)
        {
            throw new ServiceException("话题不存在或已被删除");
        }
        checkTopic(topic, topic.getTopicId());
        return topicMapper.updateTopic(topic);
    }

    @Override
    @Transactional
    public int deleteTopicByIds(String ids)
    {
        Long[] topicIds = Convert.toLongArray(ids);
        if (topicIds == null || topicIds.length == 0)
        {
            throw new ServiceException("未选择要删除的话题");
        }
        // 还有文章的话题不允许删：删了之后文章的关联变孤儿、话题页也打不开
        if (topicMapper.countPostRelations(topicIds) > 0)
        {
            throw new ServiceException("话题下还有文章，不能删除");
        }
        return topicMapper.softDeleteByIds(topicIds);
    }

    @Override
    public boolean checkTopicNameUnique(String topicName, Long topicId)
    {
        String name = StringUtils.trim(topicName);
        if (StringUtils.isEmpty(name))
        {
            return false;
        }
        return topicMapper.countByName(name, topicId) == 0;
    }

    /**
     * 新增/编辑的公共校验：名称必填、长度、唯一性，状态只认 0/1
     */
    private void checkTopic(Topic topic, Long excludeId)
    {
        String name = StringUtils.trim(topic.getTopicName());
        if (StringUtils.isEmpty(name))
        {
            throw new ServiceException("话题名称不能为空");
        }
        if (name.length() > Topic.MAX_NAME_LENGTH)
        {
            throw new ServiceException("话题名称不能超过" + Topic.MAX_NAME_LENGTH + "个字");
        }
        // 去掉用户输入的 # 前缀（设计稿显示为 #Android，库里存 Android）
        if (name.startsWith("#"))
        {
            name = name.substring(1).trim();
            if (StringUtils.isEmpty(name))
            {
                throw new ServiceException("话题名称不能为空");
            }
        }
        if (!checkTopicNameUnique(name, excludeId))
        {
            throw new ServiceException("话题名称已存在");
        }
        topic.setTopicName(name);
        String desc = StringUtils.trim(topic.getTopicDesc());
        if (desc != null && desc.length() > 200)
        {
            throw new ServiceException("话题简介不能超过200个字");
        }
        topic.setTopicDesc(desc);
        if (!"0".equals(topic.getStatus()) && !"1".equals(topic.getStatus()))
        {
            topic.setStatus("0");
        }
    }
}
