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
     * 插入一条评论（一级评论或二级评论）
     *
     * @param comment 评论（commentId 由 useGeneratedKeys 回填；
     *                一级评论 parentId / replyUserId 为 null，二级评论由服务层填好）
     * @return 影响行数
     */
    public int insertComment(Comment comment);

    /**
     * 查询某个目标下的【一级评论】列表（分页由上层 PageHelper 驱动）
     *
     * SQL 里写死了 parent_id is null：二级评论不在这里返回，它们由
     * selectRepliesByParentIds 一次 in 查询取回后挂到 rows[].children。
     * 这个谓词同时让 PageHelper 自动生成的 count 只数一级评论（= TableDataInfo.total），
     * 客户端按 total 算页数才对得上；而接口返回的 count / comment_count 仍然包含回复。
     *
     * @param comment 查询条件（只用 targetType / targetId）
     * @return 一级评论集合
     */
    public List<Comment> selectCommentList(Comment comment);

    /**
     * 查询一批【一级评论】名下的全部二级评论（一条 in 查询，避免 N+1）
     *
     * 调用方【必须保证 parentIds 非空】：MyBatis 的空集合 foreach 会生成 in ()，是语法错误。
     * 排序 comment_id asc（对话正序），与 idx_parent 的索引序一致，免 filesort。
     *
     * @param parentIds 一级评论ID集合（非空）
     * @return 二级评论集合（parentId 已填充，调用方据此分桶挂到各自的根上）
     */
    public List<Comment> selectRepliesByParentIds(@Param("parentIds") List<Long> parentIds);

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
     * 级联软删除某条一级评论名下的全部二级评论（删一级评论时调用）
     *
     * where 带 del_flag = '0'：返回的影响行数 = 本次真正删掉的行数，
     * 调用方据此减计数（删一级评论的计数增量是 -(1 + 本方法返回值)），并发下也准确。
     *
     * 【刻意不做 owner 校验】：二级评论可能是别人的，但它们的容器（一级评论）没了，
     * 语义上就不该再存在 —— 随根一起消失是产品语义。列表只展示存活的一级评论，
     * 留着的回复永远无法展示、却会被 countByTarget 计入，展示数会永久对不上。
     *
     * @param parentId 一级评论ID
     * @return 影响行数
     */
    public int softDeleteRepliesByParentId(@Param("parentId") Long parentId);

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
