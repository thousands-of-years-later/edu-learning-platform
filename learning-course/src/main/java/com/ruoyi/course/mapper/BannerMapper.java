package com.ruoyi.course.mapper;

import java.util.List;
import com.ruoyi.course.domain.Banner;

/**
 * 课程轮播图 数据层
 *
 * @author edu
 */
public interface BannerMapper
{
    /**
     * 查询轮播图列表（后台用，含停用的）
     *
     * @param banner 轮播图信息（可选过滤：categoryId、status、title 模糊）
     * @return 轮播图集合
     */
    public List<Banner> selectBannerList(Banner banner);

    /**
     * 根据轮播图ID查询（只查未删除的）
     *
     * @param bannerId 轮播图ID
     * @return 轮播图信息
     */
    public Banner selectBannerById(Long bannerId);

    /**
     * 查询某类别窗口下对客户端可见的轮播图（App 用）
     *
     * 只返回 status='0' 的，并且目标课程必须存在、未删除且未停用 ——
     * 课程后来被停用/软删时，指向它的轮播会【静默消失】而不是变成点不开的死链。
     *
     * @param categoryId 类别ID（Banner.ALL_CATEGORY_ID 表示「全部」窗口）
     * @return 轮播图集合
     */
    public List<Banner> selectVisibleBanners(Long categoryId);

    /**
     * 新增轮播图
     *
     * @param banner 轮播图信息
     * @return 结果
     */
    public int insertBanner(Banner banner);

    /**
     * 修改轮播图
     *
     * @param banner 轮播图信息
     * @return 结果
     */
    public int updateBanner(Banner banner);

    /**
     * 批量逻辑删除轮播图
     *
     * @param bannerIds 需要删除的轮播图ID
     * @return 结果
     */
    public int deleteBannerByIds(Long[] bannerIds);
}
