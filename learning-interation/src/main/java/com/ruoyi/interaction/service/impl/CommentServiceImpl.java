package com.ruoyi.interaction.service.impl;

import java.util.ArrayList;
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
 * 二级评论（回复）是【扁平两级】：所有回复都挂在同一条一级评论下，parent_id 存的是
 * 所属一级评论的 comment_id（不是直接父级），被回复人 reply_user_id 由服务端从被回复
 * 评论的作者推导、不接受前端传入。列表按一级评论分页，回复一次 in 查询取回后挂进
 * children（见 selectCommentList 里的两条硬纪律）。
 * 回复【计入】comment_count 与接口返回的 count，因此删一级评论要级联软删其回复、
 * 计数按实际删除行数减 —— 否则展示数与列表能翻到的行数会永久对不上。
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
    public Map<String, Object> insertComment(Long userId, String targetType, Long targetId, String content, Long parentId)
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
        // 归一：客户端常把「没有父评论」写成 0（数值型字段发不出 null 是常见写法）。
        // 本表 comment_id 是 auto_increment 从 100 起，0 永远不可能是合法评论，
        // 所以直接当「没传」处理 —— 否则这种写法会被下面的存在性校验判成
        // 「评论不存在或已被删除」，把一次正常的一级评论请求整个打回。
        if (parentId != null && parentId.longValue() <= 0L)
        {
            parentId = null;
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

        // 二级评论：先取被回复的那条评论，再推导出【根】与【被回复人】
        Long rootId = null;
        Long replyUserId = null;
        if (parentId != null)
        {
            Comment parent = commentMapper.selectCommentById(parentId);
            // 必须与本次请求的目标一致：否则可以把回复挂到【别的内容】的评论树下
            // ——列表按 parent_id 取回复，它会出现在别人的内容里，
            // 而它计入的却是本条内容的 comment_count，既是跨内容串数据也是计数漂移
            if (parent == null || !targetType.equals(parent.getTargetType())
                    || !targetId.equals(parent.getTargetId()))
            {
                throw new ServiceException("评论不存在或已被删除");
            }
            // 扁平两级：回复二级评论时，根是它的一级评论，不往下嵌套（parent_id 存的是根，不是直接父级）
            rootId = parent.getParentId() == null ? parent.getCommentId() : parent.getParentId();
            // 被回复人从被回复评论的作者推导，【不接受前端传入】——
            // 否则任何人都能伪造一条「xxx 回复了你」的通知
            replyUserId = parent.getUserId();
        }

        Comment comment = new Comment();
        comment.setUserId(userId);
        comment.setTargetType(targetType);
        comment.setTargetId(targetId);
        comment.setContent(text);
        comment.setParentId(rootId);
        comment.setReplyUserId(replyUserId);
        commentMapper.insertComment(comment);

        int updated = commentMapper.updateCount(tableOf(targetType), idColumnOf(targetType), targetId, 1);
        // 影响 0 行 = 内容在「存在性校验」之后又被删了。必须报错回滚，
        // 否则评论表多一条挂在已删内容上的孤儿记录、计数永久漂移。
        if (updated == 0)
        {
            throw new ServiceException("内容不存在或已被删除");
        }

        // 评论成功才通知（课程无归属人、自评自论由通知服务内部跳过）。
        // 与评论插入同一个事务：通知写失败一并回滚
        if (parentId == null)
        {
            // 一级评论：通知内容归属人（与加回复功能之前完全一样）
            notificationService.notifyComment(userId, targetType, targetId, text);
        }
        else
        {
            // 二级评论：通知被回复人；内容归属人由通知服务内部去重后决定要不要一起通知
            notificationService.notifyReply(userId, targetType, targetId, replyUserId, text);
        }

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
        // XML 里写死了 parent_id is null：这里拿到的是本级页的【一级评论】，
        // 且 PageHelper 自动生成的 count 只数一级评论（= TableDataInfo.total）
        List<Comment> roots = commentMapper.selectCommentList(query);

        // 二级评论：一条 in 查询取回【本页】所有一级评论的回复，再按 parent_id 分桶挂上去（避免 N+1）。
        //
        // 两条硬纪律，改动时别踩：
        // 1、【必须排在列表查询之后】。PageHelper 的分页参数挂在 ThreadLocal 上、会被其后第一条
        //    查询消费 —— 排在这里它就是条普通查询（不会被 LIMIT 截断，也不会把参数泄漏给下一次分页）；
        //    挪到 selectCommentList 前面就会吃掉分页参数，表现是列表分页静默失效、一次返回全量。
        // 2、【绝不能】用新 List 包一层再返回。BaseController.getDataTable 取总数用的是
        //    new PageInfo(list).getTotal()，只有 PageHelper 返回的同一个 Page 实例才带 total，
        //    换成普通 List 会让 total 静默变成「当页条数」。
        if (!roots.isEmpty())
        {
            List<Long> rootIds = new ArrayList<Long>(roots.size());
            for (Comment root : roots)
            {
                rootIds.add(root.getCommentId());
            }
            // rootIds 已在上面挡掉空集合：MyBatis 的 foreach 会为空集合生成 in ()，是语法错误
            List<Comment> replies = commentMapper.selectRepliesByParentIds(rootIds);

            Map<Long, List<Comment>> grouped = new HashMap<Long, List<Comment>>();
            for (Comment reply : replies)
            {
                List<Comment> bucket = grouped.get(reply.getParentId());
                if (bucket == null)
                {
                    bucket = new ArrayList<Comment>();
                    grouped.put(reply.getParentId(), bucket);
                }
                bucket.add(reply);
            }
            for (Comment root : roots)
            {
                // 一级评论恒带 children（没回复就是空数组），客户端不用判 null；
                // 二级评论不设 children，序列化时被 NON_NULL 抹掉
                List<Comment> children = grouped.get(root.getCommentId());
                root.setChildren(children == null ? new ArrayList<Comment>() : children);
            }
        }
        return roots;
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

        // 删一级评论时【级联软删】它名下的全部二级评论：列表只展示 del_flag='0' 的一级评论，
        // 留着二级评论会永远无法展示，而 countByTarget 又会把它们算进去，
        // 展示数与列表能翻到的行数会永久对不上。回复的作者可能是别人，
        // 但它们的容器（一级评论）没了，语义上就不该再存在。
        // 删二级评论（old.getParentId() != null）只删自己那一条，不动根。
        int childDeleted = 0;
        if (old.getParentId() == null)
        {
            childDeleted = commentMapper.softDeleteRepliesByParentId(commentId);
        }

        // 目标类型/ID 取自上面查出来的那一行，不接受前端传入 ——
        // 否则客户端能删自己的评论、却去减别人的计数。
        // 计数按【实际删掉的行数】减：二级评论 -1，一级评论 -(1 + 名下回复数)；
        // childDeleted 取自级联 UPDATE 的影响行数（where 带 del_flag='0'），并发下也准确。
        // 这里【忽略】返回值：影响 0 行有两种正常情况（内容已被删、计数已被 greatest 兜到 0），
        // 不该让用户的删除失败
        commentMapper.updateCount(tableOf(old.getTargetType()), idColumnOf(old.getTargetType()),
                old.getTargetId(), -(1 + childDeleted));

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
