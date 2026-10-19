package com.ruoyi.interaction.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.interaction.domain.Note;

/**
 * 笔记 数据层
 *
 * 来源标题（帖子标题 / 课程名·小节名）与来源是否已删除，都在 SQL 里用【标量子查询】取，
 * 不 join 内容表：join 之后 PageHelper 的客户排序字段会撞上同名列报 1052，
 * 而且本模块只依赖 ruoyi-common，反向依赖 learning-course / learning-community 会造成循环依赖
 * （与 InteractionMapper / CommentMapper 同一思路）。
 *
 * @author edu
 */
public interface NoteMapper
{
    /**
     * 插入一条笔记（noteId 由 useGeneratedKeys 回填）
     *
     * @param note 笔记（userId / targetType / targetId / title / content / progress 由服务层填好）
     * @return 影响行数
     */
    public int insertNote(Note note);

    /**
     * 按ID查询一条笔记（未逻辑删除的），带来源标题 / 课程ID / 来源删除标志
     *
     * @param noteId 笔记ID
     * @return 笔记，不存在返回 null
     */
    public Note selectNoteById(@Param("noteId") Long noteId);

    /**
     * 查询某个用户的笔记列表（分页由上层 PageHelper 驱动）
     *
     * @param userId     作者用户ID（必填，服务层保证非空）
     * @param targetType 可选过滤：post / video（空 = 全部）
     * @param targetId   可选过滤：某个小节/帖子下的笔记（需同时给 targetType）
     * @param keyword    可选搜索：标题或正文模糊匹配
     * @return 笔记集合，按最近编辑倒序
     */
    public List<Note> selectNoteList(@Param("userId") Long userId,
                                     @Param("targetType") String targetType,
                                     @Param("targetId") Long targetId,
                                     @Param("keyword") String keyword);

    /**
     * 修改自己的笔记（CAS）
     *
     * where 里带上 user_id 与 del_flag = '0'，把「只能改自己的」和「删过的不再改」
     * 交给数据库判定：并发下只有一次能影响 1 行。
     * 只改标题与正文：progress 是「记笔记那一刻的位置」，事后编辑正文不该改它。
     *
     * @param note 笔记（noteId / userId / noteTitle / noteContent）
     * @return 影响行数（1 = 修改成功）
     */
    public int updateNote(Note note);

    /**
     * 逻辑删除自己的笔记（CAS）
     *
     * @param noteId 笔记ID
     * @param userId 作者用户ID
     * @return 影响行数（1 = 删除成功）
     */
    public int softDeleteById(@Param("noteId") Long noteId, @Param("userId") Long userId);

    /**
     * 帖子是否存在且可记笔记（未逻辑删除 + 已发布）
     *
     * 光判 del_flag 不够：草稿(1)/已下架(2)的帖子不该能被记笔记，
     * 否则等于让客户端能反推哪些 ID 是未发布内容。
     */
    public int existsPost(@Param("targetId") Long targetId);

    /**
     * 课程小节是否存在且可记笔记（小节未删 + 所属课程未删且状态正常）
     *
     * @param targetId 课程小节的 video_id（不是短视频的 video_id）
     */
    public int existsCourseVideo(@Param("targetId") Long targetId);
}
