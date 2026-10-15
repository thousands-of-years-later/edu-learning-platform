package com.ruoyi.interaction.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.interaction.service.IInteractionService;

/**
 * 点赞收藏 接口（给小程序用）
 *
 * 路径放在 /api 下，因此【默认就要求登录】—— ShiroConfig 只放行了
 * /api/login 与 /api/register，这个前缀天然满足「点赞必须登录」，不用改过滤链。
 *
 * 用户ID一律取登录态，绝不接受前端传入。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/interaction")
public class ApiInteractionController extends BaseController
{
    @Autowired
    private IInteractionService interactionService;

    /**
     * 点赞/取消 或 收藏/取消
     *
     * @param targetType 目标类型（1帖子 2课程 3短视频）
     * @param targetId   目标ID
     * @param type       行为类型（1点赞 2收藏）
     * @return {active: 操作后是否已点赞/已收藏, count: 操作后的真实数量}
     */
    @PostMapping("/toggle")
    public AjaxResult toggle(String targetType, Long targetId, String type)
    {
        Map<String, Object> result = interactionService.toggle(ShiroUtils.getUserId(), targetType, targetId, type);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("active", result.get("active"));
        ajax.put("count", result.get("count"));
        return ajax;
    }

    /**
     * 批量查询当前用户已点赞/已收藏的目标ID（供列表页一次性回填按钮状态）
     *
     * @param targetType 目标类型
     * @param type       行为类型
     * @param targetIds  目标ID，逗号分隔
     * @return {ids: [已点赞/已收藏的目标ID]}
     */
    @GetMapping("/status")
    public AjaxResult status(String targetType, String type, String targetIds)
    {
        Long[] ids = Convert.toLongArray(targetIds);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("ids", interactionService.activeIds(ShiroUtils.getUserId(), targetType, type, ids));
        return ajax;
    }

    /**
     * 查某个目标的真实互动数（对账用，前端也可直接展示）
     */
    @GetMapping("/count")
    public AjaxResult count(String targetType, Long targetId, String type)
    {
        Long[] ids = new Long[] { targetId };
        AjaxResult ajax = AjaxResult.success();
        ajax.put("ids", interactionService.activeIds(ShiroUtils.getUserId(), targetType, type, ids));
        return ajax;
    }
}