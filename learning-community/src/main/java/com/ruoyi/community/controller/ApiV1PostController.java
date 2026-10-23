package com.ruoyi.community.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.community.domain.Post;
import com.ruoyi.community.mapper.MyPostMapper;
import com.ruoyi.community.service.IPostService;

/**
 * 文章（帖子）接口 v1 —— 客户端「我的主页 / 文章详情」用
 *
 * 只有三个接口，且都围绕"我自己"或"已发布"：
 *   /myList  我的已发布文章（设计稿 18_我的主页「我的文章」）
 *   /info    文章详情（设计稿 16_文章详情；只返回已发布，草稿/待审核/已下架查不到）
 *   /remove  删除自己的文章（复用后台服务层：它已经做了作者校验 + 级联清理互动/评论）
 *
 * 为什么另起一个控制器而不是改 PostController：
 *   后台那套是「页面 + 权限注解 + 会话」，客户端这套是「Token + 只碰自己的数据」，
 *   混在一起容易把后台的权限模型带进来（也容易误改后台行为）。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/post")
public class ApiV1PostController extends BaseController
{
    @Autowired
    private MyPostMapper myPostMapper;

    @Autowired
    private IPostService postService;

    /**
     * 我的文章（只含已发布，分页，按发布时间倒序）
     *
     * 列表不返回正文（正文是 longtext，列表用不到）。
     *
     * @return {total, rows:[{postId, postTitle, categoryName, likeCount, favoriteCount,
     *          commentCount, createTime}]}
     */
    @GetMapping("/myList")
    public TableDataInfo myList()
    {
        startPage();
        List<Post> list = myPostMapper.selectMyPublishedList(ApiContext.getUserId());
        return getDataTable(list);
    }

    /**
     * 文章详情（设计稿 16_文章详情）
     *
     * 只返回已发布且未删除的文章；其它状态一律按"不存在"处理，不泄露草稿/待审核的存在性。
     * content 是 Markdown 原文，由前端渲染（后端不转 HTML）。
     *
     * @param postId 文章ID
     * @return {code, msg, data:{postId, postTitle, categoryName, userName, content,
     *          likeCount, favoriteCount, commentCount, isFeatured, createTime}}
     */
    @GetMapping("/info")
    public AjaxResult info(Long postId)
    {
        if (postId == null)
        {
            throw new ServiceException("文章ID不能为空");
        }
        Post post = myPostMapper.selectPublishedById(postId);
        if (post == null)
        {
            throw new ServiceException("文章不存在或已下架");
        }
        return AjaxResult.success(post);
    }

    /**
     * 删除自己的文章（软删除）
     *
     * 复用 PostServiceImpl：它会逐个校验作者（canManageAll=false 时不是本人就报错），
     * 并在同一事务里清掉挂在帖子上的点赞收藏与评论——不自己写一份删除逻辑，
     * 免得以后忘记级联、又造出孤儿数据。
     *
     * @param postId 文章ID
     */
    @PostMapping("/remove")
    public AjaxResult remove(Long postId)
    {
        if (postId == null)
        {
            throw new ServiceException("文章ID不能为空");
        }
        return toAjax(postService.deletePostByIds(String.valueOf(postId), ApiContext.getUserId(), false));
    }
}
