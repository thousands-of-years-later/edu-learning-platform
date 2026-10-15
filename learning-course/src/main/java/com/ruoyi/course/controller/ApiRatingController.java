package com.ruoyi.course.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.course.service.ICourseRatingService;

/**
 * 课程评分 接口（给小程序用）
 *
 * 路径放在 /api 下，因此【默认就要求登录】—— ShiroConfig 只放行了
 * /api/login 与 /api/register，这个前缀天然满足「评分必须登录」，不用改过滤链。
 *
 * 用户ID一律取登录态，绝不接受前端传入。
 *
 * 每个写接口都提供【表单】与【JSON】两个出口，靠 consumes 区分（与 ApiCommentController 同一写法）：
 * 表单版是裸参数，JSON 版接 Map 后转调表单版，避免两套逻辑各写一遍。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/rating")
public class ApiRatingController extends BaseController
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
        return toResult(courseRatingService.addRating(ShiroUtils.getUserId(), courseId, Convert.toInt(score)));
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
        return toResult(courseRatingService.updateRating(ShiroUtils.getUserId(), courseId, Convert.toInt(score)));
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
        return toResult(courseRatingService.getRatingInfo(ShiroUtils.getUserId(), courseId));
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
