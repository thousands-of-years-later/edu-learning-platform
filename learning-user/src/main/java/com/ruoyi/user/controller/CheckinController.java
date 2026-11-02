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
import com.ruoyi.user.domain.CheckinRecord;
import com.ruoyi.user.service.ICheckinService;

/**
 * 签到记录（后台）
 *
 * 只读：签到记录是审计数据，管理员只查看不修改（积分流水同理）。
 * 权限：user:checkin:view / list（只给 admin）。
 *
 * @author edu
 */
@Controller
@RequestMapping("/user/checkin")
public class CheckinController extends BaseController
{
    @Autowired
    private ICheckinService checkinService;

    /**
     * 签到记录页面
     */
    @RequiresPermissions("user:checkin:view")
    @GetMapping()
    public String checkin()
    {
        return "user/checkin/checkin";
    }

    /**
     * 签到记录列表（分页）
     *
     * @param userName  用户昵称模糊（可空）
     * @param beginDate 起始日期 yyyy-MM-dd（可空）
     * @param endDate   结束日期 yyyy-MM-dd（可空）
     */
    @RequiresPermissions("user:checkin:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(String userName, String beginDate, String endDate)
    {
        startPage();
        List<CheckinRecord> list = checkinService.selectAdminList(userName, beginDate, endDate);
        return getDataTable(list);
    }
}
