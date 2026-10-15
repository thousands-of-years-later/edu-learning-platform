package com.ruoyi.interaction.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.interaction.domain.Comment;
import com.ruoyi.interaction.mapper.CommentMapper;
import com.ruoyi.interaction.service.ICommentService;
import com.ruoyi.interaction.service.INotificationService;

/**
 * 评论 服务层实现
 *
 * 计数维护的关键点（与 InteractionServiceImpl 同一套规矩）：
 *   1. 先写评论表，再按【影响行数】决定要不要动计数
 *   2. 计数用 comment_count = comment_count + 1 的原子写法，不做"读出来加一再写回"
 *   3. 整个操作在一个事务里
 *
 * 接口返回的评论数一律取 count(*) 这个【唯一真相】，不读 comment_count 列 ——
 * 列只是个给后台列表看的冗余统计，漂移了有 learning_comment_fix_count.sql 兜底，
 * 绝不能反过来把它当成真相透出去。
 *
 * 内容处理约定：content 原样存 trim 后的用户输入，【不做转义】。
 * 帖子的 Markdown 正文也是这么处理的（存原文，渲染时前端 sanitize）。
 * 评论是纯文本，渲染端只能用文本节点（th:text / 小程序 <text> / textContent），
 * 禁止 innerHTML。在这里自作主张转义，用户会看到 &lt; 这种鬼东西。
 *
 * @author edu
 */
@Service
public class CommentServiceImpl implements ICommentService
{
    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private INotificationService notificationService;

    @Override
    @Transactional
    public Map<String, Object> insertComment(Long userId, String targetType, Long targetId, String content)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
        if (!Comment.isValidTargetType(targetType))
        {
            throw new ServiceException("目标类型不合法");
        }
        if (targetId == null)
        {
            throw new ServiceException("目标ID不能为空");
        }

        // StringUtils.trim 是空安全的（null 进来返回 ""）
        String text = StringUtils.trim(content);
        if (StringUtils.isEmpty(text))
        {
            throw new ServiceException("评论内容不能为空");
        }
        // String.length() 按 UTF-16 计（emoji 算 2），比 MySQL varchar(500) 的按字符计更严，
        // 所以过了这里的值不可能被数据库截断
        if (text.length() > Comment.MAX_CONTENT_LENGTH)
        {
            throw new ServiceException("评论内容不能超过" + Comment.MAX_CONTENT_LENGTH + "个字");
        }

        if (!targetExists(targetType, targetId))
        {
            throw new ServiceException("内容不存在或已被删除");
        }

        Comment comment = new Comment();
        comment.setUserId(userId);
        comment.setTargetType(targetType);
        comment.setTargetId(targetId);
        comment.setContent(text);
        commentMapper.insertComment(comment);

        int updated = commentMapper.updateCount(tableOf(targetType), idColumnOf(targetType), targetId, 1);
        // 影响 0 行 = 内容在「存在性校验」之后又被删了。必须报错回滚，
        // 否则评论表多一条挂在已删内容上的孤儿记录、计数永久漂移。
        if (updated == 0)
        {
            throw new ServiceException("内容不存在或已被删除");
        }

        // 评论成功才通知内容归属人（课程无归属人、自评自论由通知服务内部跳过）。
        // 与评论插入同一个事务：通知写失败一并回滚
        notificationService.notifyComment(userId, targetType, targetId, text);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("commentId", comment.getCommentId());
        result.put("count", commentMapper.countByTarget(targetType, targetId));
        return result;
    }

    @Override
    public void checkTarget(String targetType, Long targetId)
    {
        if (!Comment.isValidTargetType(targetType) || targetId == null)
        {
            throw new ServiceException("参数不合法");
        }
        // 不可见的内容连评论列表都不给看，否则能拿列表接口反推哪些 ID 是草稿/已下架内容
        if (!targetExists(targetType, targetId))
        {
            throw new ServiceException("内容不存在或已被删除");
        }
    }

    @Override
    public List<Comment> selectCommentList(String targetType, Long targetId)
    {
        // 这里【只做参数校验，不查库】：控制器是在 startPage() 之后调本方法的，
        // 而 PageHelper 的分页参数会被它之后的第一条查询消费掉。
        // 若在这里查一次库（比如查目标是否存在），分页就会作用在那条校验语句上，
        // 真正的列表查询反而没有 LIMIT —— 表现是分页静默失效、一次返回全部数据。
        // 存在性校验因此拆到了 checkTarget()，由控制器在 startPage() 之前调用。
        if (!Comment.isValidTargetType(targetType) || targetId == null)
        {
            throw new ServiceException("参数不合法");
        }

        Comment query = new Comment();
        query.setTargetType(targetType);
        query.setTargetId(targetId);
        return commentMapper.selectCommentList(query);
    }

    @Override
    @Transactional
    public Map<String, Object> deleteComment(Long userId, Long commentId)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
        if (commentId == null)
        {
            throw new ServiceException("评论ID不能为空");
        }

        Comment old = commentMapper.selectCommentById(commentId);
        if (old == null)
        {
            throw new ServiceException("评论不存在或已被删除");
        }
        if (!old.getUserId().equals(userId))
        {
            throw new ServiceException("只能删除自己发表的评论");
        }

        int deleted = commentMapper.softDeleteById(commentId, userId);
        // 影响 0 行 = 并发下已被删掉。必须报错回滚，绝不能在这里减计数，
        // 否则双击删除会减两次
        if (deleted == 0)
        {
            throw new ServiceException("评论已被删除");
        }

        // 目标类型/ID 取自上面查出来的那一行，不接受前端传入 ——
        // 否则客户端能删自己的评论、却去减别人的计数。
        // 这里【忽略】返回值：影响 0 行有两种正常情况（内容已被删、计数已被 greatest 兜到 0），
        // 不该让用户的删除失败
        commentMapper.updateCount(tableOf(old.getTargetType()), idColumnOf(old.getTargetType()),
                old.getTargetId(), -1);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("count", commentMapper.countByTarget(old.getTargetType(), old.getTargetId()));
        return result;
    }

    /** 目标是否存在且可评论（未逻辑删除 + 状态正常） */
    private boolean targetExists(String targetType, Long targetId)
    {
        if (Comment.TARGET_POST.equals(targetType))
        {
            return commentMapper.existsPost(targetId) > 0;
        }
        if (Comment.TARGET_COURSE.equals(targetType))
        {
            return commentMapper.existsCourse(targetId) > 0;
        }
        return commentMapper.existsVideo(targetId) > 0;
    }

    /**
     * 目标类型 -> 计数所在的表
     * 只返回常量，不接受外部输入
     */
    private String tableOf(String targetType)
    {
        if (Comment.TARGET_POST.equals(targetType))
        {
            return "learning_post";
        }
        if (Comment.TARGET_COURSE.equals(targetType))
        {
            return "learning_course";
        }
        return "learning_short_video";
    }

    /**
     * 目标类型 -> 主键列名（三张表的主键名都不一样）
     */
    private String idColumnOf(String targetType)
    {
        if (Comment.TARGET_POST.equals(targetType))
        {
            return "post_id";
        }
        if (Comment.TARGET_COURSE.equals(targetType))
        {
            return "course_id";
        }
        return "video_id";
    }
}
