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
 * 审核链路：普通用户提交「发布」落「3 待审核」，由审核人（user_id==1 或持有
 * community:post:audit 的账号）在「帖子审核」页通过(0)/驳回(4)，通过时可指定精华。
 * 状态机与校验在 PostServiceImpl（insertPost/resolveStatus/auditPost），
 * 页面模板位于 templates/community/post/ 下，菜单与权限见
 * sql/learning_post_menu.sql 与 sql/learning_post_audit.sql。
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
     * 可见性：审核人看全部（要读待审帖的正文才能审）；作者看自己的（含草稿/已驳回）；
     * 其他人的只放已发布，否则猜个 id 就能绕过列表读到别人的草稿或已下架内容。
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
        mmap.put("canSeeAuditRemark", canAudit() || post.getUserId().equals(getUserId()));
        return prefix + "/detail";
    }

    /**
     * 帖子审核列表页（默认筛「待审核」）
     */
    @RequiresPermissions("community:post:audit")
    @GetMapping("/audit")
    public String audit(ModelMap mmap)
    {
        mmap.put("categories", selectArticleCategories());
        return prefix + "/audit";
    }

    /**
     * 帖子审核表单页（弹出层打开）
     *
     * 只有待审核的帖子能审核，已发布/草稿/已驳回/已下架一律回无权限页
     * （与 PostServiceImpl.auditPost 的校验保持一致，双保险）。
     */
    @RequiresPermissions("community:post:audit")
    @GetMapping("/audit/{postId}")
    public String auditForm(@PathVariable("postId") Long postId, ModelMap mmap)
    {
        Post post = postService.selectPostById(postId);
        if (post == null || !canAudit() || !Post.STATUS_PENDING.equals(post.getStatus()))
        {
            return "error/unauth";
        }
        mmap.put("post", post);
        return prefix + "/auditForm";
    }

    /**
     * 查询审核列表
     *
     * 与 /list 的区别：要求审核权限，且不做 ownerId 收敛——审核人要看所有人提交的待审帖。
     * /list 的社区广场可见范围不受影响。
     */
    @RequiresPermissions("community:post:audit")
    @PostMapping("/auditList")
    @ResponseBody
    public TableDataInfo auditList(Post post)
    {
        startPage();
        List<Post> list = postService.selectPostList(post);
        return getDataTable(list);
    }

    /**
     * 审核保存（通过 / 驳回，可同时指定精华）
     *
     * 刻意不加 @Validated：审核表单只有 postId/status/isFeatured/auditRemark，
     * Post 上的 @NotBlank title 之类会误伤整张表单；需要校验的字段由 service 收敛。
     */
    @RequiresPermissions("community:post:audit")
    @Log(title = "社区帖子", businessType = BusinessType.UPDATE)
    @PostMapping("/audit")
    @ResponseBody
    public AjaxResult auditSave(Post post)
    {
        post.setUpdateBy(getLoginName());
        return toAjax(postService.auditPost(post, canAudit()));
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
        regulateAuditFields(post);
        // 作者永远取当前登录用户，不接受表单传入的 userId
        post.setUserId(getUserId());
        post.setCreateBy(getLoginName());
        return toAjax(postService.insertPost(post, ShiroUtils.isAdmin()));
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
        regulateAuditFields(post);
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
     * 审核相关字段不由普通编辑表单维护：
     *  · audit_remark：只在 /audit 审核接口写入；updatePost 的 SQL 里也不含该列（双保险，防表单伪造）
     *  · is_featured：非管理员置空（mapper 的 &lt;if&gt; 会跳过，保持库里的值不变）；
     *    管理员按复选框语义归档为 0/1 —— 勾选时浏览器提交 "1"，未勾选不提交，
     *    这里把「未提交」统一成 "0"，否则管理员永远取消不了精华
     */
    private void regulateAuditFields(Post post)
    {
        post.setAuditRemark(null);
        if (!ShiroUtils.isAdmin())
        {
            post.setIsFeatured(null);
        }
        else
        {
            post.setIsFeatured(Post.FEATURED_YES.equals(post.getIsFeatured())
                    ? Post.FEATURED_YES : Post.FEATURED_NO);
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
     * 当前登录用户能否审核帖子
     *
     * 不能只认 ShiroUtils.isAdmin()（user_id==1）：平台另建了 eduadmin 管理员账号
     * （user_id != 1，role_key=admin，见 sql/learning_admin_account.sql），
     * 它拿到 community:post:audit 授权后也该能审核，否则审核页永远是空的。
     * 计数维护、编辑他人帖子等其它管理动作仍按 isAdmin() 判定，不在本次放宽范围。
     */
    private boolean canAudit()
    {
        return ShiroUtils.isAdmin() || ShiroUtils.getSubject().isPermitted("community:post:audit");
    }

    /**
     * 当前登录用户能否查看这条帖子（审核人、本人，或别人已发布的）
     *
     * 审核人必须能读到别人待审核/已驳回帖子的正文，审核动作才有意义。
     */
    private boolean canView(Post post)
    {
        return canAudit()
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
