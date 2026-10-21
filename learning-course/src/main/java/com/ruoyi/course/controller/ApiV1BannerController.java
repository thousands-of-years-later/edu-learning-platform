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
 * 课程轮播图 接口 v1（Token 认证，给小程序用）
 *
 * 与 ApiBannerController（/api/banner）功能一一对应，差别只在鉴权：
 * 老的走 Shiro 会话（/api/** 默认要求登录）；这里走 Token：/api/v1/** 在 ShiroConfig 里是 anon，
 * Shiro 没有登录态，鉴权由 ApiTokenFilter 完成。
 *
 * 这里【不加】@Anonymous 注解 —— /api/v1/** 是整体放行的，真正的鉴权由 ApiTokenFilter 做，
 * 标上 @Anonymous 反而会让人误以为这个接口不需要登录。
 * 轮播是纯展示数据、用不到用户身份，但接口本身仍然要求带 Token（没往白名单里加）。
 *
 * 只读：普通用户对这个模块只有这一个入口，增删改查全在后台 /course/banner（见 BannerController）。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/banner")
public class ApiV1BannerController extends BaseController
{
    @Autowired
    private IBannerService bannerService;

    /**
     * 轮播图列表（不分页）
     *
     * @param categoryId 类别ID；不传 = 「全部」窗口（category_id = 0）
     * @return {rows: [轮播图], total}（成功码 0，与 /api/v1/course/list 同形，
     *         客户端的列表解析逻辑可直接复用）
     *
     * 不调用 startPage()：轮播是有限集合，一次返回完。
     * 只返回 status='0' 且目标课程可见（未删除、未停用）的行 ——
     * 课程被停用/删除后，指向它的轮播会从本接口【静默消失】，避免点不开的死链。
     */
    @GetMapping("/list")
    public TableDataInfo list(Long categoryId)
    {
        return getDataTable(bannerService.selectVisibleBanners(
                categoryId == null ? Banner.ALL_CATEGORY_ID : categoryId));
    }
}
