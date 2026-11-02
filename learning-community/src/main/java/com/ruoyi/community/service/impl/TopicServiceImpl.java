package com.ruoyi.community.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
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
}
