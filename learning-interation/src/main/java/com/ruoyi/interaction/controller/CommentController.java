package com.ruoyi.interaction.controller;

import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.interaction.domain.Comment;
import com.ruoyi.interaction.service.ICommentService;

/**
 * 评论管理（后台）
 *
 * 客户端（小程序/App）的评论读写在 /api/v1/comment，这里是给管理员用的：
 * 看全站评论（一级评论 + 回复），删违规评论。
 *
 * 权限：
 *   community:comment:view    页面访问
 *   community:comment:list    列表数据
 *   community:comment:remove  删除
 *
 * 删除语义与客户端一致：一级评论会级联软删它的回复，并修正内容表的评论数；
 * 区别只在于管理员可以删任何人的评论。
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
     * 评论管理页面
     */
    @RequiresPermissions("community:comment:view")
    @GetMapping()
    public String comment()
    {
        return PREFIX + "/comment";
    }

    /**
     * 评论列表（分页）
     *
     * @param comment 查询条件：targetType（1帖子 2课程 3短视频）、content（模糊）
     */
    @RequiresPermissions("community:comment:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Comment comment)
    {
        startPage();
        List<Comment> list = commentService.selectAdminList(comment);
        return getDataTable(list);
    }

    /**
     * 删除评论（支持批量，逗号分隔）
     *
     * @param ids 评论ID串
     */
    @RequiresPermissions("community:comment:remove")
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        Long[] commentIds = Convert.toLongArray(ids);
        if (commentIds == null || commentIds.length == 0)
        {
            throw new ServiceException("未选择要删除的评论");
        }
        for (Long commentId : commentIds)
        {
            commentService.deleteCommentByAdmin(commentId);
        }
        return success();
    }
}
