package com.ruoyi.interaction.mapper;

import java.util.List;

/**
 * 违禁词 数据层（只读）
 *
 * 类名不能叫 SensitiveWordMapper：learning-community 里已有同名 Mapper，
 * MyBatis 按简单类名注册 Bean，两个同名类会直接导致启动冲突
 * （ConflictingBeanDefinitionException）。
 *
 * learning-community 依赖本模块（帖子要读互动计数/通知），本模块不能反向依赖它，
 * 否则成环。所以评论侧直接查同一张 learning_sensitive_word 表，只取启用词。
 *
 * @author edu
 */
public interface CommentSensitiveWordMapper
{
    /**
     * 查询启用中的违禁词（status = '0'）
     *
     * @return 违禁词列表
     */
    List<String> selectEnabledWords();
}
