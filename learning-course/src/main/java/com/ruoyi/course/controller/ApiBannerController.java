package com.ruoyi.course.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.course.domain.Banner;
import com.ruoyi.course.service.IBannerService;

/**
 * 课程轮播图 接口（给小程序用）
 *
 * 路径放在 /api 下，因此【默认就要求登录】—— ShiroConfig 只放行了
 * /api/login 与 /api/register，这个前缀天然满足「看轮播必须登录」，不用改过滤链。
 *
 * 与 ApiV1BannerController（/api/v1/banner，走 Token）功能一一对应：
 * 两个入口同时存在，老客户端不用改，新客户端用 /api/v1。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/banner")
public class ApiBannerController extends BaseController
{
    @Autowired
    private IBannerService bannerService;

    /**
     * 轮播图列表（不分页）
     *
     * @param categoryId 类别ID；不传 = 「全部」窗口（category_id = 0）
     * @return {rows: [轮播图], total}（成功码 0）
     *
     * 不调用 startPage()：轮播是有限集合，一次返回完。
     * 只返回 status='0' 且目标课程可见（未删除、未停用）的行。
     */
    @GetMapping("/list")
    public TableDataInfo list(Long categoryId)
    {
        return getDataTable(bannerService.selectVisibleBanners(
                categoryId == null ? Banner.ALL_CATEGORY_ID : categoryId));
    }
}
