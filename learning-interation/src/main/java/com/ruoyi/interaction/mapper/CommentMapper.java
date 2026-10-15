package com.ruoyi.interaction.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.interaction.domain.Comment;

/**
 * 评论 数据层
 *
 * 目标存在性校验与计数更新都直接写 SQL，不反向依赖 learning-community /
 * learning-course / learning-video，避免模块循环依赖
 * （与 InteractionMapper 同一思路）。
 *
 * @author edu
 */
public interface CommentMapper
{
    /**
     * 插入一条评论
     *
     * @param comment 评论（commentId 由 useGeneratedKeys 回填）
     * @return 影响行数
     */
    public int insertComment(Comment comment);

    /**
     * 查询某个目标下的评论列表（分页由上层 PageHelper 驱动）
     *
     * @param comment 查询条件（只用 targetType / targetId）
     * @return 评论集合
     */
    public List<Comment> selectCommentList(Comment comment);

    /**
     * 查询某目标的真实评论数（唯一真相，接口返回的就是它）
     *
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @return 数量（只算未逻辑删除的）
     */
    public int countByTarget(@Param("targetType") String targetType,
                             @Param("targetId") Long targetId);

    /**
     * 按ID查询评论（未逻辑删除的）
     *
     * @param commentId 评论ID
     * @return 评论，不存在返回 null
     */
    public Comment selectCommentById(@Param("commentId") Long commentId);

    /**
     * 逻辑删除一条评论（CAS）
     *
     * where 里带上 user_id 与 del_flag = '0'，把「只能删自己的」和「已删过的不再删」
     * 交给数据库判定：并发双击时只有一次能影响 1 行，调用方据此决定要不要减计数。
     *
     * @param commentId 评论ID
     * @param userId    评论人用户ID
     * @return 影响行数（1 = 本次删除成功）
     */
    public int softDeleteById(@Param("commentId") Long commentId,
                              @Param("userId") Long userId);

    /**
     * 目标是否存在且可评论（未逻辑删除 + 状态正常）
     *
     * 光判 del_flag 不够：帖子的草稿(1)/已下架(2)、课程与短视频的停用(1)
     * 都不该再产生新评论，否则会出现「给看不见的内容评论」，
     * 还能拿列表接口反推哪些 ID 是未发布内容。
     */
    public int existsPost(@Param("targetId") Long targetId);

    public int existsCourse(@Param("targetId") Long targetId);

    public int existsVideo(@Param("targetId") Long targetId);

    /**
     * 按增量更新评论数（原子写法，并发安全）
     *
     * table / idColumn 由服务层从常量映射而来（只可能是三张表名 + 三个主键列名），
     * 不接受任何外部输入；计数列就 comment_count 一个，直接写死在 XML 里。
     *
     * @param table    内容表名（learning_post / learning_course / learning_short_video）
     * @param idColumn 内容表主键列名（post_id / course_id / video_id）
     * @param targetId 目标ID
     * @param delta    增量（+1 / -1）
     * @return 影响行数
     */
    public int updateCount(@Param("table") String table,
                           @Param("idColumn") String idColumn,
                           @Param("targetId") Long targetId,
                           @Param("delta") int delta);
}
