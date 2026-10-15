package com.ruoyi.interaction.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.interaction.domain.Interaction;

/**
 * 点赞收藏 数据层
 *
 * 目标存在性校验与计数更新都直接写 SQL，不反向依赖 learning-community /
 * learning-course / learning-video，避免模块循环依赖
 * （与删分类时的 CategoryMapper.countPostByCategoryId 同一思路）。
 *
 * 反向也一样：三个内容模块删除内容时，直接在本模块 Mapper 里写纯 SQL 级联清理
 * learning_interaction / learning_comment，不走服务注入。
 *
 * @author edu
 */
public interface InteractionMapper
{
    /**
     * 插入一条互动记录
     *
     * 用普通 insert，不用 insert ignore：insert ignore 会把「字段超长、非空违反」
     * 这类真错误也降级成 warning 并返回 0 行，调用方就没法区分「已经点过」和「插入失败」，
     * 会把失败当成用户在取消点赞。重复由唯一索引 uk_user_target 报出来，
     * 服务层捕获 DuplicateKeyException 判定「已经点过」。
     *
     * @param interaction 互动记录
     * @return 影响行数
     */
    public int insertOne(Interaction interaction);

    /**
     * 删除一条互动记录（取消点赞/收藏）
     *
     * @param interaction 互动记录
     * @return 影响行数
     */
    public int deleteOne(Interaction interaction);

    /**
     * 批量查询当前用户已点赞/已收藏的目标ID
     *
     * @param userId     用户ID
     * @param targetType 目标类型
     * @param type       行为类型
     * @param ids        目标ID数组
     * @return 目标ID集合
     */
    public List<Long> selectActiveTargetIds(@Param("userId") Long userId,
                                            @Param("targetType") String targetType,
                                            @Param("type") String type,
                                            @Param("ids") Long[] ids);

    /**
     * 查询某目标的真实互动数
     *
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @param type       行为类型
     * @return 数量
     */
    public int countByTarget(@Param("targetType") String targetType,
                             @Param("targetId") Long targetId,
                             @Param("type") String type);

    /**
     * 目标是否存在且可互动（未逻辑删除 + 状态正常）
     *
     * 光判 del_flag 不够：帖子的草稿(1)/已下架(2)、课程与短视频的停用(1)
     * 都不该再产生新的点赞收藏，否则会出现「给看不见的内容点赞」，
     * 还能拿 count 接口反推哪些 ID 是未发布内容。
     */
    public int existsPost(@Param("targetId") Long targetId);

    public int existsCourse(@Param("targetId") Long targetId);

    public int existsVideo(@Param("targetId") Long targetId);

    /**
     * 按增量更新计数（原子写法，并发安全）
     *
     * @param targetId 目标ID
     * @param column   计数列名（like_count / favorite_count，只允许这两个值）
     * @param delta    增量（+1 / -1）
     * @return 影响行数
     */
    public int updateCount(@Param("table") String table,
                           @Param("idColumn") String idColumn,
                           @Param("targetId") Long targetId,
                           @Param("column") String column,
                           @Param("delta") int delta);
}