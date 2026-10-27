package com.ruoyi.community.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.PageDomain;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.page.TableSupport;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.community.domain.PostDetail;
import com.ruoyi.community.domain.PostListSummary;
import com.ruoyi.community.domain.PostSummary;
import com.ruoyi.community.mapper.MyPostMapper;
import com.ruoyi.community.service.IPostService;

/**
 * 文章（帖子）接口 v1 —— 客户端「社区列表 / 我的主页 / 文章详情」用
 *
 * 四个接口，都围绕"我自己"或"已发布"：
 *   /list    已发布文章列表（社区首页 / 分类页，可按 categoryId 筛选）
 *   /myList  我的已发布文章（设计稿 18_我的主页「我的文章」）
 *   /info    文章详情（设计稿 16_文章详情；只返回已发布）
 *   /remove  删除自己的文章（复用后台服务层：作者校验 + 级联清理互动/评论）
 *
 * 返回字段按设计稿裁剪过：
 *   /list    标题 + 作者（昵称/头像）+ 阅读数 + 评论数 + 点赞数 + 收藏数；
 *   /myList  只有标题 + 点赞数 + 收藏数；
 *   /info    标题 + 作者（昵称/头像）+ 时间 + 分类标签 + 正文 + 点赞数 + 评论数。
 * "已发布"只是查询条件，不作为字段返回。
 *
 * 为什么另起控制器而不是改 PostController：后台那套是「页面 + 权限注解 + 会话」，
 * 客户端是「Token + 只碰自己的数据」，混在一起容易把后台权限模型带进来。
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
     * 已发布文章列表（分页，按发布时间倒序；可按社区类别筛选）
     *
     * categoryId 不传 = 全部分类（社区信息流）；传了只返回该分类的文章。
     * 类别ID全局唯一，传成课程类别只会查不到数据，不会串到课程内容。
     *
     * @param categoryId 类别ID（可选）；分页参数 pageNum / pageSize
     * @return {code:0, rows:[{postId, postTitle, userName, avatar, viewCount,
     *         likeCount, favoriteCount, commentCount}], total}
     */
    @GetMapping("/list")
    public TableDataInfo list(Long categoryId)
    {
        // 不用 BaseController.startPage()：它会读取请求里的 orderByColumn / isAsc 交给 PageHelper，
        // PageHelper 会用客户端排序替换 Mapper 里固定的 ORDER BY。这里只取分页参数。
        PageDomain page = TableSupport.buildPageRequest();
        PageHelper.startPage(page.getPageNum(), page.getPageSize()).setReasonable(page.getReasonable());
        List<PostListSummary> list = myPostMapper.selectPublishedList(categoryId);
        return getDataTable(list);
    }

    /**
     * 我的文章（分页，按发布时间倒序）
     *
     * @return {total, rows:[{postId, postTitle, likeCount, favoriteCount}]}
     */
    @GetMapping("/myList")
    public TableDataInfo myList()
    {
        startPage();
        List<PostSummary> list = myPostMapper.selectMyPublishedList(ApiContext.getUserId());
        return getDataTable(list);
    }

    /**
     * 文章详情（设计稿 16_文章详情）
     *
     * @param postId 文章ID
     * @return {code, msg, data:{postId, postTitle, content, userId, userName, avatar,
     *          categoryName, createTime, likeCount, commentCount}}
     *         点赞/收藏的按钮状态用 /api/v1/interaction/status 查（targetType=1）
     */
    @GetMapping("/info")
    public AjaxResult info(Long postId)
    {
        if (postId == null)
        {
            throw new ServiceException("文章ID不能为空");
        }
        PostDetail detail = myPostMapper.selectPublishedById(postId);
        if (detail == null)
        {
            throw new ServiceException("文章不存在或已下架");
        }
        return AjaxResult.success(detail);
    }

    /**
     * 删除自己的文章（软删除）
     *
     * 复用 PostServiceImpl：它逐个校验作者（canManageAll=false 时不是本人就报错），
     * 并在同一事务里清掉挂在帖子上的点赞收藏与评论——不自己写一份删除逻辑，免得漏掉级联。
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
