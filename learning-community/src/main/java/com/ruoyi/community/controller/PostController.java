package com.ruoyi.community.controller;

import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.community.domain.Post;
import com.ruoyi.community.service.IPostService;
import com.ruoyi.course.domain.Category;
import com.ruoyi.course.service.ICategoryService;

/**
 * 社区帖子 控制器
 *
 * 与课程的区别：帖子的写操作 common 角色也要能做（普通用户要发/改/删自己的帖子），
 * 所以这里【不做】课程那种「只有超级管理员可以维护」的统一拦截，
 * 「只能动自己的帖子」由 PostServiceImpl 逐条校验 user_id 兜底。
 *
 * 页面模板位于 templates/community/post/ 下，菜单与按钮权限见 sql/learning_post_menu.sql。
 *
 * @author edu
 */
@Controller
@RequestMapping("/community/post")
public class PostController extends BaseController
{
    /** 页面根路径，模板位于 templates/<prefix>/ 下 */
    private String prefix = "community/post";

    @Autowired
    private IPostService postService;

    /** 类别领域对象与服务来自依赖的 learning-course 模块 */
    @Autowired
    private ICategoryService categoryService;

    /**
     * 帖子列表页
     *
     * 列表是「社区广场」：普通用户看到所有人已发布的帖子 + 自己的全部（含草稿），
     * 管理员看到全部。前端按 isAdmin / currentUserId 决定每行显示不显示编辑、删除按钮。
     */
    @RequiresPermissions("community:post:view")
    @GetMapping()
    public String post(ModelMap mmap)
    {
        mmap.put("categories", selectArticleCategories());
        mmap.put("isAdmin", ShiroUtils.isAdmin());
        mmap.put("currentUserId", getUserId());
        return prefix + "/post";
    }

    /**
     * 新增帖子页
     */
    @RequiresPermissions("community:post:add")
    @GetMapping("/add")
    public String add(ModelMap mmap)
    {
        mmap.put("categories", selectArticleCategories());
        mmap.put("isAdmin", ShiroUtils.isAdmin());
        return prefix + "/add";
    }

    /**
     * 修改帖子页
     *
     * 这里必须自己校验归属：@RequiresPermissions 只说明有编辑权限，
     * common 角色都持有该权限，光靠注解谁都能打开 /edit/{别人的帖子id}。
     */
    @RequiresPermissions("community:post:edit")
    @GetMapping("/edit/{postId}")
    public String edit(@PathVariable("postId") Long postId, ModelMap mmap)
    {
        Post post = postService.selectPostById(postId);
        if (post == null || !canManage(post))
        {
            return "error/unauth";
        }
        mmap.put("post", post);
        mmap.put("categories", selectArticleCategories());
        mmap.put("isAdmin", ShiroUtils.isAdmin());
        return prefix + "/edit";
    }

    /**
     * 帖子详情页（只读，同时是 Markdown 的实际渲染处）
     *
     * 可见性：管理员看全部；作者看自己的（含草稿）；其他人的只放已发布，
     * 否则猜个 id 就能绕过列表读到别人的草稿或已下架内容。
     */
    @RequiresPermissions("community:post:view")
    @GetMapping("/detail/{postId}")
    public String detail(@PathVariable("postId") Long postId, ModelMap mmap)
    {
        Post post = postService.selectPostById(postId);
        if (post == null || !canView(post))
        {
            return "error/unauth";
        }
        mmap.put("post", post);
        return prefix + "/detail";
    }

    /**
     * 查询帖子列表
     *
     * 按约定不加权限注解：帖子列表要能被所有登录用户读到（社区广场），
     * 可见性范围由 ownerId 兜底。
     */
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Post post)
    {
        startPage();
        applyOwnerScope(post);
        List<Post> list = postService.selectPostList(post);
        return getDataTable(list);
    }

    /**
     * 导出帖子列表
     *
     * 该权限只授给 admin；这里仍按当前用户收敛范围，避免以后误把 export 授给 common 时越界。
     */
    @Log(title = "社区帖子", businessType = BusinessType.EXPORT)
    @RequiresPermissions("community:post:export")
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Post post)
    {
        applyOwnerScope(post);
        List<Post> list = postService.selectPostList(post);
        ExcelUtil<Post> util = new ExcelUtil<Post>(Post.class);
        return util.exportExcel(list, "社区帖子数据");
    }

    /**
     * 删除帖子
     */
    @RequiresPermissions("community:post:remove")
    @Log(title = "社区帖子", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(postService.deletePostByIds(ids, getUserId(), ShiroUtils.isAdmin()));
    }

    /**
     * 新增保存帖子
     */
    @RequiresPermissions("community:post:add")
    @Log(title = "社区帖子", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Validated Post post)
    {
        stripCountersIfNotAdmin(post);
        // 作者永远取当前登录用户，不接受表单传入的 userId
        post.setUserId(getUserId());
        post.setCreateBy(getLoginName());
        return toAjax(postService.insertPost(post));
    }

    /**
     * 修改保存帖子
     */
    @RequiresPermissions("community:post:edit")
    @Log(title = "社区帖子", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Validated Post post)
    {
        stripCountersIfNotAdmin(post);
        post.setUpdateBy(getLoginName());
        return toAjax(postService.updatePost(post, getUserId(), ShiroUtils.isAdmin()));
    }

    /**
     * 列表可见性范围：非管理员只放「已发布 + 自己的」
     *
     * 每次强制覆盖，不是「为空才设」——Spring 会把请求里的 ownerId 也绑进来，
     * 不覆盖的话普通用户传个 ownerId=别人的ID 就能读到别人的全部帖子。
     */
    private void applyOwnerScope(Post post)
    {
        post.setOwnerId(ShiroUtils.isAdmin() ? null : getUserId());
    }

    /**
     * 点赞/收藏/评论三个计数只允许管理员维护（与 course 一致），
     * 普通用户提交时置空，配合 mapper 的 &lt;if test="... != null"&gt; 就不会被改动。
     */
    private void stripCountersIfNotAdmin(Post post)
    {
        if (!ShiroUtils.isAdmin())
        {
            post.setLikeCount(null);
            post.setFavoriteCount(null);
            post.setCommentCount(null);
        }
    }

    /**
     * 当前登录用户能否维护这条帖子（管理员，或本人发布的）
     */
    private boolean canManage(Post post)
    {
        return ShiroUtils.isAdmin() || post.getUserId().equals(getUserId());
    }

    /**
     * 当前登录用户能否查看这条帖子（管理员、本人，或别人已发布的）
     */
    private boolean canView(Post post)
    {
        return ShiroUtils.isAdmin()
                || post.getUserId().equals(getUserId())
                || Post.STATUS_PUBLISHED.equals(post.getStatus());
    }

    /**
     * 帖子表单的类别下拉：只取社区模块下状态正常的类别
     */
    private List<Category> selectArticleCategories()
    {
        Category category = new Category();
        category.setCategoryType(Category.TYPE_ARTICLE);
        category.setStatus("0");
        return categoryService.selectCategoryList(category);
    }
}
