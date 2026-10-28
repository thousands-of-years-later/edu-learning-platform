package com.ruoyi.community.controller;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.PageDomain;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.page.TableSupport;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.community.domain.Post;
import com.ruoyi.community.domain.PostDetail;
import com.ruoyi.community.domain.PostListSummary;
import com.ruoyi.community.domain.PostSummary;
import com.ruoyi.community.mapper.MyPostMapper;
import com.ruoyi.community.service.IPostService;
import com.ruoyi.course.domain.Category;
import com.ruoyi.course.service.ICategoryService;

/**
 * 文章（帖子）接口 v1 —— 客户端「社区列表 / 我的主页 / 文章发布 / 文章详情」用
 *
 * 五个接口：
 *   /add     发布文章（普通用户提交后落「3 待审核」，管理员审核通过才公开）
 *   /list    已发布文章列表（社区首页 / 分类页，可按 categoryId 筛选）
 *   /myList  我的文章（设计稿 18_我的主页；含待审核/已驳回，返回 status）
 *   /info    文章详情（设计稿 16_文章详情；已发布所有人可见，作者可看自己的全部状态）
 *   /remove  删除自己的文章（复用后台服务层：作者校验 + 级联清理互动/评论）
 *
 * 返回字段按设计稿裁剪过：
 *   /list    标题 + 作者（昵称/头像）+ 阅读数 + 评论数 + 点赞数 + 收藏数；
 *   /myList  标题 + 状态 + 审核备注 + 点赞数 + 收藏数；
 *   /info    标题 + 状态 + 作者（昵称/头像）+ 时间 + 分类标签 + 正文 + 点赞数 + 评论数。
 *
 * 为什么另起控制器而不是改 PostController：后台那套是「页面 + 权限注解 + 会话」，
 * 客户端是「Token + 只碰自己的数据」，混在一起容易把后台权限模型带进来。
 * 发布复用同一个 PostServiceImpl：状态机、正文校验、作者归属只维护一份。
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

    @Autowired
    private ICategoryService categoryService;

    /**
     * 发布文章（普通用户提交后进入待审核）
     *
     * 只接收标题/分类/正文三个字段；作者、状态、计数等一律服务端赋值。
     * 复用 PostServiceImpl.insertPost(post, false)：它会把「发布」改写为「待审核」，
     * 并校验标题与正文；App 端统一先审后发，管理员直接发布请走后台发帖入口。
     *
     * @param postTitle 标题（≤200 字）
     * @param categoryId 文章类别ID（category_type=article 且启用）
     * @param content Markdown 正文（≤65535 字；图片先调 /api/v1/upload，再内联）
     * @return {code:0, msg:"提交成功，等待审核", postId:新文章ID, status:"3"}
     */
    @PostMapping("/add")
    public AjaxResult add(String postTitle, Long categoryId, String content)
    {
        return publish(postTitle, categoryId, content);
    }

    /**
     * 发布文章（JSON 请求体）
     *
     * @param body 请求体，字段同表单：postTitle、categoryId、content
     */
    @PostMapping(value = "/add", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult addJson(@RequestBody Map<String, String> body)
    {
        String rawCategoryId = body.get("categoryId");
        Long categoryId = Convert.toLong(rawCategoryId);
        // Convert.toLong 对「有值但转不动」是无声返回 null 的，不挡掉会和「没传分类」混在一起
        if (categoryId == null && StringUtils.isNotEmpty(rawCategoryId))
        {
            return AjaxResult.error("文章分类不合法");
        }
        return publish(body.get("postTitle"), categoryId, body.get("content"));
    }

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
     * 我的文章（分页，按发布时间倒序；含草稿/待审核/已驳回/已下架）
     *
     * @return {total, rows:[{postId, postTitle, status, auditRemark, likeCount, favoriteCount}]}
     */
    @GetMapping("/myList")
    public TableDataInfo myList()
    {
        // 与 /list 一样只取分页参数，避免客户端 orderByColumn 覆盖 Mapper 里的固定排序
        PageDomain page = TableSupport.buildPageRequest();
        PageHelper.startPage(page.getPageNum(), page.getPageSize()).setReasonable(page.getReasonable());
        List<PostSummary> list = myPostMapper.selectMyList(ApiContext.getUserId());
        return getDataTable(list);
    }

    /**
     * 文章详情（设计稿 16_文章详情）
     *
     * 已发布所有人可见；作者还能查看自己的待审核/已驳回/草稿/已下架文章。
     * 其他用户查非公开文章与查不存在都返回同一提示，避免探测。
     *
     * @param postId 文章ID
     * @return {code, msg, data:{postId, postTitle, status, auditRemark, content, userId,
     *          userName, avatar, categoryName, createTime, likeCount, commentCount}}
     *         点赞/收藏的按钮状态用 /api/v1/interaction/status 查（targetType=1）
     */
    @GetMapping("/info")
    public AjaxResult info(Long postId)
    {
        if (postId == null)
        {
            // 直接返回 AjaxResult：/api/v1 只服务程序客户端，任何情况都要是 JSON
            return AjaxResult.error("文章ID不能为空");
        }
        PostDetail detail = myPostMapper.selectVisibleById(postId, ApiContext.getUserId());
        if (detail == null)
        {
            return AjaxResult.error("文章不存在或已下架");
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

    /**
     * 发布文章的共同实现（表单与 JSON 出口共用，保证校验和赋值一致）
     *
     * 不直接绑定 Post：实体里的 userId / status / 计数 / isFeatured / auditRemark
     * 都可能被客户端伪造，这里只接收三个具名参数，其余全部服务端赋值。
     */
    private AjaxResult publish(String postTitle, Long categoryId, String content)
    {
        checkArticleCategory(categoryId);

        String title = StringUtils.trim(postTitle);
        if (StringUtils.isEmpty(title))
        {
            throw new ServiceException("帖子标题不能为空");
        }
        if (title.length() > 200)
        {
            throw new ServiceException("帖子标题长度不能超过200个字符");
        }
        if (StringUtils.isBlank(content))
        {
            throw new ServiceException("帖子内容不能为空");
        }
        if (content.length() > 65535)
        {
            throw new ServiceException("帖子内容长度不能超过65535个字符");
        }

        Post post = new Post();
        post.setPostTitle(title);
        post.setCategoryId(categoryId);
        post.setContent(content);
        // 必须提交「已发布」：insertPost 只有看到它才会把普通用户改写为「3 待审核」；
        // 直接传 3 会走 else 分支落成草稿。
        post.setStatus(Post.STATUS_PUBLISHED);
        post.setUserId(ApiContext.getUserId());
        post.setCreateBy(ApiContext.getLoginName());

        // 固定 false：App 端统一先审后发，管理员直接发布走后台发帖入口
        int rows = postService.insertPost(post, false);
        if (rows <= 0)
        {
            return AjaxResult.error("发布失败，请稍后重试");
        }
        AjaxResult ajax = AjaxResult.success("提交成功，等待审核");
        ajax.put("postId", post.getPostId());
        ajax.put("status", post.getStatus());
        return ajax;
    }

    /**
     * 校验文章类别：必须存在、是 article 类型、且处于启用状态
     *
     * selectCategoryById 只过滤 del_flag，不校验类型与状态；课程类别和文章类别共用
     * learning_category，必须显式拦住课程类别，否则文章会挂到课程分类下。
     */
    private void checkArticleCategory(Long categoryId)
    {
        if (categoryId == null)
        {
            throw new ServiceException("请选择文章分类");
        }
        Category category = categoryService.selectCategoryById(categoryId);
        if (category == null
                || !Category.TYPE_ARTICLE.equals(category.getCategoryType())
                || !"0".equals(category.getStatus()))
        {
            throw new ServiceException("请选择有效的文章分类");
        }
    }
}
