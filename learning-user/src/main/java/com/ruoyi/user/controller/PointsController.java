package com.ruoyi.user.controller;

import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.user.domain.PointsAccount;
import com.ruoyi.user.domain.PointsLog;
import com.ruoyi.user.service.IPointsService;

/**
 * 积分账户 / 积分流水（后台，只读）
 *
 * 积分是签到与兑换算出来的结果，后台只查看不修改（要纠正就记一条 adjust 流水）。
 * 权限：user:points:view / list（只给 admin）。
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
     * 积分账户页面
     */
    @RequiresPermissions("user:points:view")
    @GetMapping("/account")
    public String account()
    {
        return "user/points/account";
    }

    /**
     * 积分账户列表（分页）
     *
     * @param userName 用户昵称模糊（可空）
     */
    @RequiresPermissions("user:points:list")
    @PostMapping("/account/list")
    @ResponseBody
    public TableDataInfo accountList(String userName)
    {
        startPage();
        List<PointsAccount> list = pointsService.selectAdminAccountList(userName);
        return getDataTable(list);
    }

    /**
     * 积分流水页面
     */
    @RequiresPermissions("user:points:view")
    @GetMapping("/log")
    public String log()
    {
        return "user/points/log";
    }

    /**
     * 积分流水列表（分页）
     *
     * @param userName 用户昵称模糊（可空）
     * @param type     类型 checkin / exchange / adjust（可空）
     */
    @RequiresPermissions("user:points:list")
    @PostMapping("/log/list")
    @ResponseBody
    public TableDataInfo logList(String userName, String type)
    {
        startPage();
        List<PointsLog> list = pointsService.selectAdminLogList(userName, type);
        return getDataTable(list);
    }
}
