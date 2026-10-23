package com.ruoyi.user.controller;

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
import com.ruoyi.user.service.IUserProfileService;

/**
 * 用户资料 接口 v1（Token 认证，给小程序用）
 *
 * 对应设计稿：
 *   27_编辑主页  → GET 回显 + POST 保存（头像先调 /api/v1/upload 拿地址，再在保存时提交）
 *   18_我的主页 / 06_个人主页 → GET 同一份资料
 *
 * 只能看/改自己的资料：userId 取 ApiContext，绝不接受前端传入。
 *
 * 昵称/头像/性别写的是 sys_user（后台用户管理看到同一份数据），简介写 learning_user_profile。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/user")
public class ApiV1UserProfileController extends BaseController
{
    @Autowired
    private IUserProfileService userProfileService;

    /**
     * 我的资料（编辑主页回显）
     *
     * @return {userId, userName, avatar, sex, sexLabel, bio}
     *         avatar 为 null 时前端用昵称首字兜底；sexLabel 是"男/女/不公开"
     */
    @GetMapping("/profile")
    public AjaxResult profile()
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.putAll(userProfileService.profile(ApiContext.getUserId()));
        return ajax;
    }

    /**
     * 我的主页首屏数据（设计稿 18_我的主页）
     *
     * 一次返回：资料（昵称/头像/性别文案/简介）+ 关注数 + 粉丝数 + 获赞数 + 粉丝头像预览，
     * 一个请求就能铺满首屏（不然前端要连着调 profile / follow/count / follow/fans 三个）。
     *
     * 获赞口径：只算我发的帖子的点赞数之和（不含短视频），不限帖子状态、只排除已删除。
     *
     * @return {userId, userName, avatar, sex, sexLabel, bio,
     *          followCount, fansCount, likeReceivedCount,
     *          fanPreview:[{userId, userName, avatar}]}
     */
    @GetMapping("/home")
    public AjaxResult home()
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.putAll(userProfileService.home(ApiContext.getUserId()));
        return ajax;
    }

    /**
     * 保存我的资料
     *
     * @param userName 昵称（1~20 字，去首尾空格）
     * @param avatar   头像地址（先调 /api/v1/upload 拿到的 url；传空 = 清空头像）
     * @param sex      性别：0男 1女 2不公开
     * @param bio      个人简介（≤200 字，传空 = 清空）
     * @return 保存后的资料（结构同 GET /profile，前端可直接刷新，不用再查）
     */
    @PostMapping("/profile")
    public AjaxResult update(String userName, String avatar, String sex, String bio)
    {
        AjaxResult ajax = AjaxResult.success("保存成功");
        ajax.putAll(userProfileService.updateProfile(ApiContext.getUserId(), userName, avatar, sex, bio));
        return ajax;
    }

    /**
     * 保存我的资料（JSON 请求体）
     *
     * @param body 请求体，字段同表单：userName、avatar、sex、bio
     */
    @PostMapping(value = "/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult updateJson(@RequestBody Map<String, String> body)
    {
        return update(body.get("userName"), body.get("avatar"), body.get("sex"), body.get("bio"));
    }
}
