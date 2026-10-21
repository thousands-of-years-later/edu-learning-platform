package com.ruoyi.course.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.course.domain.Banner;
import com.ruoyi.course.domain.Category;
import com.ruoyi.course.domain.Course;
import com.ruoyi.course.mapper.BannerMapper;
import com.ruoyi.course.service.IBannerService;
import com.ruoyi.course.service.ICategoryService;
import com.ruoyi.course.service.ICourseService;

/**
 * 轮播图信息 服务层处理
 *
 * 写操作都是单条语句，因此不加 @Transactional（本仓库的规则是「多条语句才需要事务」，
 * 与 CategoryServiceImpl 一致）。
 *
 * @author edu
 */
@Service
public class BannerServiceImpl implements IBannerService
{
    @Autowired
    private BannerMapper bannerMapper;

    /** 校验「所属类别」用（0=全部不查库）；报错信息也要用它的名字 */
    @Autowired
    private ICategoryService categoryService;

    /** 校验「跳转课程」是否存在、是否属于所选类别 */
    @Autowired
    private ICourseService courseService;

    /**
     * 查询轮播图列表
     *
     * @param banner 轮播图信息
     * @return 轮播图集合
     */
    @Override
    public List<Banner> selectBannerList(Banner banner)
    {
        List<Banner> list = bannerMapper.selectBannerList(banner);
        fillCategoryName(list);
        return list;
    }

    /**
     * 根据轮播图ID查询轮播图
     *
     * @param bannerId 轮播图ID
     * @return 轮播图信息
     */
    @Override
    public Banner selectBannerById(Long bannerId)
    {
        Banner banner = bannerMapper.selectBannerById(bannerId);
        fillCategoryName(banner);
        return banner;
    }

    /**
     * 查询某类别窗口下对客户端可见的轮播图
     *
     * @param categoryId 类别ID（null 用调用方归一化成 Banner.ALL_CATEGORY_ID）
     * @return 轮播图集合
     */
    @Override
    public List<Banner> selectVisibleBanners(Long categoryId)
    {
        List<Banner> list = bannerMapper.selectVisibleBanners(categoryId);
        fillCategoryName(list);
        return list;
    }

    /**
     * 新增轮播图
     *
     * @param banner 轮播图信息
     * @return 结果
     */
    @Override
    public int insertBanner(Banner banner)
    {
        banner.setTitle(trimTitle(banner.getTitle()));
        validate(banner);
        return bannerMapper.insertBanner(banner);
    }

    /**
     * 修改轮播图
     *
     * @param banner 轮播图信息
     * @return 结果
     */
    @Override
    public int updateBanner(Banner banner)
    {
        banner.setTitle(trimTitle(banner.getTitle()));
        if (StringUtils.isNull(bannerMapper.selectBannerById(banner.getBannerId())))
        {
            throw new ServiceException("轮播图不存在或已被删除");
        }
        validate(banner);
        return bannerMapper.updateBanner(banner);
    }

    /**
     * 批量逻辑删除轮播图
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deleteBannerByIds(String ids)
    {
        Long[] bannerIds = Convert.toLongArray(ids);
        // 空串会被 Convert 转成空数组，<foreach> 将拼出 in () 造成 SQL 语法错误，必须拦截
        if (bannerIds == null || bannerIds.length == 0)
        {
            throw new ServiceException("未选择要删除的轮播图数据");
        }
        for (Long bannerId : bannerIds)
        {
            if (StringUtils.isNull(bannerMapper.selectBannerById(bannerId)))
            {
                throw new ServiceException(String.format("轮播图ID[%s]不存在或已被删除", bannerId));
            }
        }
        return bannerMapper.deleteBannerByIds(bannerIds);
    }

    /**
     * 新增/修改共用的业务校验
     *
     * @param banner 轮播图信息
     */
    private void validate(Banner banner)
    {
        // 1、所属类别：0=「全部」是约定值、不在 learning_category 里，不查库；
        //    其余必须是本模块（course）的未删除类别，防止把社区文章的类别挂到课程轮播上。
        String targetCategoryName = Banner.ALL_CATEGORY_NAME;
        if (!Banner.ALL_CATEGORY_ID.equals(banner.getCategoryId()))
        {
            Category category = categoryService.selectCategoryById(banner.getCategoryId());
            if (StringUtils.isNull(category) || !Category.TYPE_COURSE.equals(category.getCategoryType()))
            {
                throw new ServiceException("所属类别不存在或不属于课程模块");
            }
            targetCategoryName = category.getCategoryName();
        }

        // 2、跳转课程必须存在且未删除（selectCourseById 已排除 del_flag='2'）。
        //    已停用的课程【允许】挂 —— 它是可逆状态，App 端的 exists 过滤会自动隐藏，
        //    后台下拉框也会标注「已停用」；在这里硬拦反而会让运营没法先配好、之后再生效。
        Course course = courseService.selectCourseById(banner.getCourseId());
        if (StringUtils.isNull(course))
        {
            throw new ServiceException("跳转课程不存在或已被删除");
        }

        // 3、每个类别的窗口只能挂该类别下的课程：
        //    功能本身就是「每个类一个窗口」，java 窗口推 mysql 课程会直接破坏这个心智模型。
        //    只在写时校验、不在读时再过滤一遍 —— 否则「后台显示正常、App 就是不显示」
        //    没法从后台列表诊断出来。课程事后被改到别的类别会让轮播变陈旧，MVP 接受。
        //    「全部」窗口不受此限；未归类（category_id 为空）的课程只能在「全部」推。
        if (!Banner.ALL_CATEGORY_ID.equals(banner.getCategoryId())
                && !banner.getCategoryId().equals(course.getCategoryId()))
        {
            String courseCategoryName = StringUtils.isNotEmpty(course.getCategoryName())
                    ? course.getCategoryName() : "未归类";
            throw new ServiceException(String.format(
                    "课程「%s」属于类别「%s」，不能放入类别「%s」的轮播；请改选「全部」或更换课程",
                    course.getCourseName(), courseCategoryName, targetCategoryName));
        }
    }

    /**
     * 把 categoryId = 0 的行的 categoryName 填成「全部」
     *
     * 为什么不重写 getCategoryName() 或者把 '全部' 硬编码进 SQL：
     *   1) ExcelUtil.getTargetValue 是 field.setAccessible(true) + field.get(vo)，
     *      【按字段反射取值】，走 getter 会让导出的「所属类别」列变成空白；
     *   2) 硬编码进 SQL 会把展示文案塞进数据层，App 端也得跟着维护一份。
     * 就地改元素、不替换集合本身（与 CourseServiceImpl.selectCourseList 一致），
     * 保证 PageHelper 返回的 Page 对象原样传下去。
     *
     * @param banner 轮播图信息
     */
    private void fillCategoryName(Banner banner)
    {
        if (banner != null && Banner.ALL_CATEGORY_ID.equals(banner.getCategoryId()))
        {
            banner.setCategoryName(Banner.ALL_CATEGORY_NAME);
        }
    }

    /**
     * 逐行归一化「全部」的类别名称
     *
     * @param list 轮播图集合
     */
    private void fillCategoryName(List<Banner> list)
    {
        if (list == null || list.isEmpty())
        {
            return;
        }
        for (Banner banner : list)
        {
            fillCategoryName(banner);
        }
    }

    /**
     * 标题去空白，并兜底拦截空值
     *
     * @param title 轮播标题
     * @return 处理后的标题
     */
    private String trimTitle(String title)
    {
        String trimmed = StringUtils.trim(title);
        if (StringUtils.isEmpty(trimmed))
        {
            throw new ServiceException("轮播标题不能为空");
        }
        return trimmed;
    }
}
