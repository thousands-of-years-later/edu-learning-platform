package com.ruoyi.course.controller;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.course.domain.Category;
import com.ruoyi.course.domain.CategorySummary;
import com.ruoyi.course.service.ICategoryService;

/**
 * 类别 接口 v1（Token 认证，给小程序用）
 *
 * 课程与社区文章共用 learning_category，用 categoryType 区分：course / article。
 * 客户端按分类浏览内容前先调本接口拿分类列表，再把 categoryId 传给：
 *   /api/v1/course/list?categoryId=...
 *   /api/v1/post/list?categoryId=...
 *
 * 只返回 status='0' 且未删除的类别，按 order_num 升序；不分页（类别是有限集合），
 * 但仍用 getDataTable 保持 {code,rows,total} 结构，客户端列表解析可直接复用。
 *
 * 这里【不加】@Anonymous 注解 —— /api/v1/** 在 ShiroConfig 里整体放行，
 * 真正的鉴权由 ApiTokenFilter 做，标上反而会让人误以为这个接口不需要登录。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/category")
public class ApiV1CategoryController extends BaseController
{
    @Autowired
    private ICategoryService categoryService;

    /**
     * 类别列表（不分页，按显示顺序升序）
     *
     * @param categoryType 类别类型：course=课程、article=社区文章（必传）
     * @return 成功：{code:0, rows:[{categoryId, categoryName}], total}；
     *         类型缺失/非法：{code:500, msg:"..."}
     */
    @GetMapping("/list")
    public Object list(String categoryType)
    {
        // 不能抛 ServiceException：GlobalExceptionHandler 对普通 GET 会返回 error/service.html，
        // 而 /api/v1 只服务程序客户端，必须始终拿到 JSON。这里直接返回 AjaxResult。
        String type = StringUtils.trim(categoryType);
        if (StringUtils.isEmpty(type))
        {
            return AjaxResult.error("类别类型不能为空（course/article）");
        }
        if (!Category.TYPE_COURSE.equals(type) && !Category.TYPE_ARTICLE.equals(type))
        {
            return AjaxResult.error("类别类型不合法（仅支持 course/article）");
        }

        // 手动构造查询对象：客户端不能通过 status / categoryName / delFlag 改可见性条件
        Category query = new Category();
        query.setCategoryType(type);
        query.setStatus("0");

        List<Category> categories = categoryService.selectCategoryList(query);
        List<CategorySummary> rows = new ArrayList<>(categories.size());
        for (Category category : categories)
        {
            CategorySummary item = new CategorySummary();
            item.setCategoryId(category.getCategoryId());
            item.setCategoryName(category.getCategoryName());
            rows.add(item);
        }
        return getDataTable(rows);
    }
}
