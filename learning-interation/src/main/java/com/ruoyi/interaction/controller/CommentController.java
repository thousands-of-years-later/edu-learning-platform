package com.ruoyi.interaction.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.interaction.domain.Comment;
import com.ruoyi.interaction.service.ICommentService;

/**
 * 内容评论查看（后台）
 *
 * 不再单独设「评论管理」菜单：评论从课程管理、社区帖子、短视频管理的行内「评论」按钮进入，
 * 页面顶部展示内容信息（标题、阅读/点赞/收藏/评论数），下面按一级评论 + 回复的方式展开。
 *
 * 参数：targetType（1文章 2课程 3短视频）、targetId（内容ID）。
 * 权限：后台页面统一登录后访问；能进哪个内容页，就能看这个内容的评论。
 *
 * @author edu
 */
@Controller
@RequestMapping("/community/comment")
public class CommentController extends BaseController
{
    /** 页面路径前缀 */
    private static final String PREFIX = "community/comment";

    @Autowired
    private ICommentService commentService;

    /**
     * 评论查看页
     *
     * @param targetType 目标类型（1文章 2课程 3短视频）
     * @param targetId   目标ID
     */
    @GetMapping("/view")
    public String view(String targetType, Long targetId, ModelMap mmap)
    {
        mmap.put("targetType", targetType);
        mmap.put("targetId", targetId);

        if (!Comment.isValidTargetType(targetType) || targetId == null)
        {
            mmap.put("error", "参数不合法，无法查看评论");
            return PREFIX + "/view";
        }

        Map<String, Object> brief = commentService.selectTargetBrief(targetType, targetId);
        if (brief == null)
        {
            mmap.put("error", "内容不存在或已被删除");
            return PREFIX + "/view";
        }
        normalizeCreateTime(brief);

        mmap.put("brief", brief);
        mmap.put("typeLabel", typeLabel(targetType));
        // 一级评论（children 里是各自的回复），按评论ID倒序
        mmap.put("comments", commentService.selectCommentList(targetType, targetId));
        // 真实评论数（含回复），不读冗余列
        mmap.put("commentCount", commentService.countByTarget(targetType, targetId));
        return PREFIX + "/view";
    }

    /**
     * 删除一条评论（管理员）
     *
     * 一级评论会级联删除它下面的回复，并修正内容的评论数。
     *
     * @param commentId 评论ID
     */
    @Log(title = "评论查看", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(Long commentId)
    {
        if (commentId == null)
        {
            throw new ServiceException("评论ID不能为空");
        }
        // 菜单已取消，这里用"是否管理员"兜底：业务后台菜单只授给 admin
        if (!ShiroUtils.isAdmin())
        {
            throw new ServiceException("只有管理员可以删除评论");
        }
        commentService.deleteCommentByAdmin(commentId);
        return success();
    }

    /**
     * 内容简介里的发布时间是数据库 DATETIME，Map 查询会映射成 LocalDateTime；
     * Thymeleaf 的 #dates 只认 java.util.Date，所以这里统一格式化成字符串再交给页面。
     */
    private void normalizeCreateTime(Map<String, Object> brief)
    {
        Object value = brief.get("createTime");
        if (value instanceof LocalDateTime)
        {
            brief.put("createTime", ((LocalDateTime) value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        }
    }

    /**
     * 目标类型 -> 中文标签
     */
    private String typeLabel(String targetType)
    {
        if (Comment.TARGET_POST.equals(targetType))
        {
            return "文章";
        }
        if (Comment.TARGET_COURSE.equals(targetType))
        {
            return "课程";
        }
        return "短视频";
    }
}
