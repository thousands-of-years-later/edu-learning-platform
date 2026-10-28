package com.ruoyi.video.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.PageDomain;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.page.TableSupport;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.video.domain.ShortVideo;
import com.ruoyi.video.service.IShortVideoService;

/**
 * 短视频 接口 v1（Token 认证，给小程序用）
 *
 * 只做「上下滑」需要的两个只读接口：
 *   /list       全站信息流（分页，最新在前）
 *   /{videoId}  视频详情（直接打开 / 分享回跳）
 *
 * 短视频只在后台由管理员维护，客户端没有发布 / 编辑 / 删除接口。
 * 点赞、收藏、评论、观看历史继续复用已有接口：
 *   /api/v1/interaction/*、/api/v1/comment/*、/api/v1/history/*（targetType=3 / shortVideo）。
 *
 * 出口在 /api/v1/** 下：ShiroConfig 里整体放行，鉴权由 ApiTokenFilter 完成，
 * 所以这里【不加】@Anonymous 注解 —— 标了反而会被误读成这个接口不需要登录。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/video")
public class ApiV1ShortVideoController extends BaseController
{
    @Autowired
    private IShortVideoService shortVideoService;

    /**
     * 短视频信息流（上下滑，分页，最新在前）
     *
     * 不用 BaseController.startPage()：它会读取客户端传的 orderByColumn / isAsc，
     * PageHelper 会用客户端排序替换 Mapper 里固定的 order by。这里只取分页参数。
     *
     * @return {code:0, rows:[短视频（含 videoUrl / coverUrl / 各类计数）], total}
     *         （分页参数 pageNum / pageSize）
     */
    @GetMapping("/list")
    public TableDataInfo list()
    {
        PageDomain page = TableSupport.buildPageRequest();
        PageHelper.startPage(page.getPageNum(), page.getPageSize()).setReasonable(page.getReasonable());
        ShortVideo query = new ShortVideo();
        // 客户端传什么 status 都不算数：停用视频对 App 不可见
        query.setStatus(ShortVideo.STATUS_NORMAL);
        List<ShortVideo> list = shortVideoService.selectShortVideoList(query);
        return getDataTable(list);
    }

    /**
     * 短视频详情（播放页）
     *
     * 只返回未删除且状态正常的视频，停用 / 删除与不存在一样返回错误，避免探测。
     * 点赞 / 收藏的按钮状态用 /api/v1/interaction/status 查（targetType=3）。
     *
     * @param videoId 视频ID
     * @return {code:0, data:{videoId, videoTitle, videoUrl, coverUrl, intro, duration,
     *         viewCount, likeCount, favoriteCount, commentCount, userName, createTime}}
     */
    @GetMapping("/{videoId}")
    public AjaxResult detail(@PathVariable("videoId") String videoId)
    {
        // 路径参数用 String 接、手动转 Long：非法 ID 也要返回 JSON，
        // 交给 Spring 做 Long 绑定会抛类型转换异常、走到 HTML 错误页
        Long id = Convert.toLong(videoId);
        if (StringUtils.isNull(id))
        {
            return AjaxResult.error("视频ID不合法");
        }
        ShortVideo video = shortVideoService.selectShortVideoById(id);
        // selectShortVideoById 只过滤 del_flag，停用状态要在这里挡住
        if (StringUtils.isNull(video) || !ShortVideo.STATUS_NORMAL.equals(video.getStatus()))
        {
            return AjaxResult.error("视频不存在或已下架");
        }
        return AjaxResult.success(video);
    }
}
