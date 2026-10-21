package com.ruoyi.course.service;

import java.util.List;
import com.ruoyi.course.domain.Banner;

/**
 * 课程轮播图 服务层
 *
 * @author edu
 */
public interface IBannerService
{
    /**
     * 查询轮播图列表（后台用，含停用的）
     *
     * 返回的每一行 categoryName 都已归一化：categoryId = 0 的行显示为「全部」。
     *
     * @param banner 轮播图信息（可选过滤：categoryId、status、title 模糊）
     * @return 轮播图集合
     */
    public List<Banner> selectBannerList(Banner banner);

    /**
     * 根据轮播图ID查询轮播图
     *
     * @param bannerId 轮播图ID
     * @return 轮播图信息
     */
    public Banner selectBannerById(Long bannerId);

    /**
     * 查询某类别窗口下对客户端可见的轮播图（App 只读入口）
     *
     * 只返回正常的轮播，且目标课程必须可见（未删除、未停用）。
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
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteBannerByIds(String ids);
}
