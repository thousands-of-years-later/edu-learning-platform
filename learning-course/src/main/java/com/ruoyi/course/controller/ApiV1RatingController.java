package com.ruoyi.course.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.course.service.ICourseRatingService;

/**
 * 课程评分 接口 v1（Token 认证，给小程序用）
 *
 * 与 ApiRatingController（/api/rating）功能一一对应，
 * 差别只在【用户ID从哪来】：
 *   老的走 Shiro 会话：/api/** 默认要求登录，用户ID取 ShiroUtils.getUserId()；
 *   这里走 Token：/api/v1/** 在 ShiroConfig 里是 anon，Shiro 没有登录态，
 *   用户ID取自 ApiContext（由 ApiTokenFilter 校验 Token 后写入）。
 *
 * 两个入口同时存在：老客户端不用改，新客户端用 /api/v1。
 * 这里【不加】@Anonymous 注解 —— /api/v1/** 是整体放行的，真正的鉴权由 ApiTokenFilter 做，
 * 标上 @Anonymous 反而会让人误以为这个接口不需要登录。
 *
 * 用户ID一律取上下文，绝不接受前端传入。
 *
 * 每个写接口都提供【表单】与【JSON】两个出口，靠 consumes 区分（与 ApiV1CommentController 同一写法）。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/rating")
public class ApiV1RatingController extends BaseController
{
    @Autowired
    private ICourseRatingService courseRatingService;

    /**
     * 新增评分（每人每课只能评一次，已评过请用 /update）
     *
     * @param courseId 课程ID
     * @param score    评分（1-5 的整数；用字符串接收，非法值由服务层统一报错）
     * @return {myScore: 我的评分, avgScore: 课程平均分, ratingCount: 评分人数}
     */
    @PostMapping("/add")
    public AjaxResult add(Long courseId, String score)
    {
        return toResult(courseRatingService.addRating(ApiContext.getUserId(), courseId, Convert.toInt(score)));
    }

    /**
     * 新增评分（JSON 请求体）
     *
     * @param body 请求体，字段同表单：courseId、score
     */
    @PostMapping(value = "/add", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult addJson(@RequestBody Map<String, String> body)
    {
        return add(Convert.toLong(body.get("courseId")), body.get("score"));
    }

    /**
     * 修改评分（未评过请先调 /add）
     *
     * @param courseId 课程ID
     * @param score    新评分（1-5 的整数）
     * @return {myScore, avgScore, ratingCount}
     */
    @PostMapping("/update")
    public AjaxResult update(Long courseId, String score)
    {
        return toResult(courseRatingService.updateRating(ApiContext.getUserId(), courseId, Convert.toInt(score)));
    }

    /**
     * 修改评分（JSON 请求体）
     *
     * @param body 请求体，字段同表单：courseId、score
     */
    @PostMapping(value = "/update", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult updateJson(@RequestBody Map<String, String> body)
    {
        return update(Convert.toLong(body.get("courseId")), body.get("score"));
    }

    /**
     * 查询评分：我的评分 + 课程平均分 + 评分人数
     *
     * @param courseId 课程ID
     * @return {myScore: 我的评分（未评为 null）, avgScore: 平均分（无人评分为 null）, ratingCount: 评分人数}
     */
    @GetMapping("/info")
    public AjaxResult info(Long courseId)
    {
        return toResult(courseRatingService.getRatingInfo(ApiContext.getUserId(), courseId));
    }

    /** 统一把服务层的 {myScore, avgScore, ratingCount} 塞进 AjaxResult（成功码 0） */
    private AjaxResult toResult(Map<String, Object> result)
    {
        return AjaxResult.success()
                .put("myScore", result.get("myScore"))
                .put("avgScore", result.get("avgScore"))
                .put("ratingCount", result.get("ratingCount"));
    }
}
