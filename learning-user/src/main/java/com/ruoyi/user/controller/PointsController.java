package com.ruoyi.user.controller;

import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.user.domain.PointsAccount;
import com.ruoyi.user.domain.PointsLog;
import com.ruoyi.user.domain.PointsSource;
import com.ruoyi.user.service.IPointsService;

/**
 * 积分记录（后台，只读）
 *
 * 一个菜单两页：
 *   1) /user/points/records  用户列表：每个用户的总积分（当前余额）、累计获得、最后变动时间；
 *   2) /user/points/detail   点某个用户进来：他的每一条积分来源、积分变动、变动后余额、时间。
 *
 * 「来源」用 PointsLog.type 存编码，展示时用 PointsSource.labelOf 转中文；
 * 以后新增积分来源（任务、邀请、活动…）只要在 PointsSource 里加编码+标签、发积分时写一条流水即可，
 * 后台这里不需要改代码。
 *
 * 权限：user:points:view（页面）/ user:points:list（列表数据），只给 admin。
 *
 * @author edu
 */
@Controller
@RequestMapping("/user/points")
public class PointsController extends BaseController
{
    @Autowired
    private IPointsService pointsService;

    /**
     * 积分记录页（用户列表）
     */
    @RequiresPermissions("user:points:view")
    @GetMapping("/records")
    public String records()
    {
        return "user/points/records";
    }

    /**
     * 积分记录列表（分页）：每个用户一行
     *
     * @param userName 用户昵称模糊（可空）
     */
    @RequiresPermissions("user:points:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(String userName)
    {
        startPage();
        List<PointsAccount> list = pointsService.selectAdminAccountList(userName);
        return getDataTable(list);
    }

    /**
     * 某个用户的积分明细页
     *
     * @param userId 用户ID
     * @param type   来源编码（可空：空 = 全部来源）
     */
    @RequiresPermissions("user:points:view")
    @GetMapping("/detail")
    public String detail(Long userId, String type, ModelMap mmap)
    {
        mmap.put("userId", userId);
        mmap.put("type", type);
        mmap.put("sources", PointsSource.all());
        if (userId == null)
        {
            mmap.put("error", "参数不合法，无法查看积分记录");
            return "user/points/detail";
        }

        PointsAccount account = pointsService.selectAdminAccount(userId);
        if (account == null)
        {
            mmap.put("error", "该用户还没有积分记录");
            return "user/points/detail";
        }

        mmap.put("account", account);
        mmap.put("logs", pointsService.selectLogsByUser(userId, type));
        return "user/points/detail";
    }
}
