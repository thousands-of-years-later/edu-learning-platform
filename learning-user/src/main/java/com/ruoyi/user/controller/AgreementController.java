package com.ruoyi.user.controller;

import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.user.domain.Agreement;
import com.ruoyi.user.service.IAgreementService;

/**
 * 平台协议（后台）：用户协议 / 隐私政策 / 关于我们
 *
 * 页面模板：templates/user/agreement/
 * 菜单与按钮权限：sql/learning_agreement_menu.sql
 *
 * 三种类型各维护一条记录，新增/修改时由服务层做类型查重（软删除的不算占用）。
 * 列表接口按仓库约定不加权限注解（登录即可读），增删改受 @RequiresPermissions 控制。
 *
 * @author edu
 */
@Controller
@RequestMapping("/user/agreement")
public class AgreementController extends BaseController
{
    /** 页面根路径，模板位于 templates/<prefix>/ 下 */
    private String prefix = "user/agreement";

    @Autowired
    private IAgreementService agreementService;

    /**
     * 协议列表页
     */
    @RequiresPermissions("user:agreement:view")
    @GetMapping()
    public String agreement(ModelMap mmap)
    {
        mmap.put("agreementTypes", Agreement.typeOptions());
        return prefix + "/agreement";
    }

    /**
     * 新增协议页
     */
    @RequiresPermissions("user:agreement:add")
    @GetMapping("/add")
    public String add(ModelMap mmap)
    {
        mmap.put("agreementTypes", Agreement.typeOptions());
        return prefix + "/add";
    }

    /**
     * 修改协议页
     */
    @RequiresPermissions("user:agreement:edit")
    @GetMapping("/edit/{agreementId}")
    public String edit(@PathVariable("agreementId") Long agreementId, ModelMap mmap)
    {
        Agreement agreement = agreementService.selectAgreementById(agreementId);
        if (agreement == null)
        {
            // 记录不存在/已删除时回列表，避免渲染空对象
            return redirect("/user/agreement");
        }
        mmap.put("agreement", agreement);
        mmap.put("agreementTypes", Agreement.typeOptions());
        return prefix + "/edit";
    }

    /**
     * 查询协议列表
     *
     * 按约定不加权限注解：所有登录用户都能读这个列表，写操作仍受权限控制。
     */
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Agreement agreement)
    {
        startPage();
        List<Agreement> list = agreementService.selectAgreementList(agreement);
        return getDataTable(list);
    }

    /**
     * 协议类型唯一性校验（新增/编辑表单远程校验）
     *
     * 编辑时表单会带上 agreementId，服务层据此排除自身。
     */
    @PostMapping("/checkTypeUnique")
    @ResponseBody
    public boolean checkTypeUnique(Agreement agreement)
    {
        return agreementService.checkAgreementTypeUnique(agreement);
    }

    /**
     * 新增保存协议
     */
    @RequiresPermissions("user:agreement:add")
    @Log(title = "平台协议", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Validated Agreement agreement)
    {
        agreement.setCreateBy(getLoginName());
        return toAjax(agreementService.insertAgreement(agreement));
    }

    /**
     * 修改保存协议
     */
    @RequiresPermissions("user:agreement:edit")
    @Log(title = "平台协议", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Validated Agreement agreement)
    {
        agreement.setUpdateBy(getLoginName());
        return toAjax(agreementService.updateAgreement(agreement));
    }

    /**
     * 批量删除协议（逻辑删除，删掉后可重新新增同类型）
     */
    @RequiresPermissions("user:agreement:remove")
    @Log(title = "平台协议", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(agreementService.deleteAgreementByIds(ids));
    }
}
