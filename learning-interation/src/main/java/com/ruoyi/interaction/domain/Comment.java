package com.ruoyi.interaction.domain;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 评论表 learning_comment
 *
 * 一张表管三种内容（帖子 / 课程 / 短视频），target_type 编码与 {@link Interaction} 完全相同，
 * 改动时要一起改。
 *
 * 课程评论挂 learning_course.course_id（课程级），不是 learning_course_video.video_id。
 *
 * 删除是【逻辑删除】（del_flag 0存在 2删除）：本表没有唯一索引要维护，用户本来就可以重复发
 * 同样的话，因此不存在 learning_interaction 那种「硬删除才能建唯一索引」的约束。
 *
 * 【二级评论】是扁平两级（微信/微博形态），不是无限嵌套树：
 *   一级评论：parent_id = null，children 挂它的全部二级回复（无回复时是空数组）；
 *   二级评论：parent_id = 所属【一级】评论的 comment_id（【不是】直接父级 ——
 *             回复另一条二级评论时，根会上溯到它们共同的一级评论），
 *             children 恒为 null（序列化时被 NON_NULL 抹掉，客户端不用判）。
 *   reply_user_id = 被回复人，由服务端从被回复评论的作者推导，不接受前端传入。
 *
 * children 是【纯内存字段】，本表没有这一列，由 CommentServiceImpl 组装（一次 in 查询，非 N+1）。
 *
 * 分页口径：列表只分页一级评论（TableDataInfo.total = 一级评论数），
 * 而接口返回的 count 与内容表 comment_count 含二级评论 —— 两者口径不同是刻意的。
 *
 * 计数字段（learning_post.comment_count 等）只由本模块维护，后台编辑内容时不再改动它们；
 * 内容被删除时由内容模块在删除语句里连同 del_flag 一起清零
 * （见 ShortVideoMapper.deleteShortVideoByIds 等，以及 sql/learning_orphan_cleanup.sql）。
 *
 * content 是【纯文本】，原样存 trim 后的用户输入，不做转义；渲染端只能以文本节点输出
 * （th:text / 小程序 <text> / textContent），禁止 innerHTML。
 *
 * 故意不继承 BaseEntity：本表没有 create_by / update_by / remark，
 * 继承了反而会让 getParams() 往每个返回行里多塞一个空的 params。
 *
 * @author edu
 */
public class Comment implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 目标类型：社区帖子（与 Interaction.TARGET_POST 同值） */
    public static final String TARGET_POST = "1";

    /** 目标类型：课程（与 Interaction.TARGET_COURSE 同值） */
    public static final String TARGET_COURSE = "2";

    /** 目标类型：短视频（与 Interaction.TARGET_VIDEO 同值） */
    public static final String TARGET_VIDEO = "3";

    /** 删除标志：存在 */
    public static final String DEL_FLAG_NORMAL = "0";

    /** 删除标志：已删除 */
    public static final String DEL_FLAG_DELETED = "2";

    /** 评论内容最大长度，必须与 learning_comment.content 的 varchar(500) 保持一致 */
    public static final int MAX_CONTENT_LENGTH = 500;

    private Long commentId;

    /** 评论人用户ID（sys_user.user_id） */
    private Long userId;

    /** 目标类型（1帖子 2课程 3短视频） */
    private String targetType;

    /** 目标ID */
    private Long targetId;

    /** 父评论ID（一级评论为 null；二级评论存所属【一级】评论的 comment_id，不是直接父级） */
    private Long parentId;

    /** 被回复人用户ID（仅二级评论有值；服务端从被回复评论的作者推导，不接受前端传入） */
    private Long replyUserId;

    /** 评论内容（纯文本，最长500字） */
    private String content;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    /** 评论时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 更新时间（逻辑删除时写入） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /** 评论人登录名（关联 sys_user 查出，非本表列） */
    private String userName;

    /** 被回复人登录名（关联 sys_user 查出，非本表列） */
    private String replyUserName;

    /** 该一级评论的二级回复（纯内存字段，非本表列；二级评论恒为 null） */
    private List<Comment> children;

    public Long getCommentId()
    {
        return commentId;
    }

    public void setCommentId(Long commentId)
    {
        this.commentId = commentId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getTargetType()
    {
        return targetType;
    }

    public void setTargetType(String targetType)
    {
        this.targetType = targetType;
    }

    public Long getTargetId()
    {
        return targetId;
    }

    public void setTargetId(Long targetId)
    {
        this.targetId = targetId;
    }

    public Long getParentId()
    {
        return parentId;
    }

    public void setParentId(Long parentId)
    {
        this.parentId = parentId;
    }

    public Long getReplyUserId()
    {
        return replyUserId;
    }

    public void setReplyUserId(Long replyUserId)
    {
        this.replyUserId = replyUserId;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getReplyUserName()
    {
        return replyUserName;
    }

    public void setReplyUserName(String replyUserName)
    {
        this.replyUserName = replyUserName;
    }

    /**
     * 二级评论不带 children 字段（NON_NULL）：一级评论恒带（没回复就是空数组），
     * 客户端拿到的响应里「有 children = 是一级评论」，不用额外判 null。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<Comment> getChildren()
    {
        return children;
    }

    public void setChildren(List<Comment> children)
    {
        this.children = children;
    }

    /** 目标类型是否合法 */
    public static boolean isValidTargetType(String t)
    {
        return TARGET_POST.equals(t) || TARGET_COURSE.equals(t) || TARGET_VIDEO.equals(t);
    }
}
