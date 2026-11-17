package com.ruoyi.user.service;

import java.util.List;
import com.ruoyi.user.domain.Agreement;

/**
 * 平台协议 服务层
 *
 * @author edu
 */
public interface IAgreementService
{
    /**
     * 查询协议列表（后台用，不返回正文）
     *
     * @param agreement 查询条件（可选：agreementType、title 模糊、status）
     * @return 协议集合
     */
    public List<Agreement> selectAgreementList(Agreement agreement);

    /**
     * 根据协议ID查询协议（带正文）
     *
     * @param agreementId 协议ID
     * @return 协议信息；不存在返回 null
     */
    public Agreement selectAgreementById(Long agreementId);

    /**
     * 查询 App 端可见的协议列表（正常状态 + 未删除，不返回正文）
     *
     * @return 协议集合（按 order_num 升序）
     */
    public List<Agreement> selectEnabledAgreementList();

    /**
     * 按类型查询 App 端可见的协议（正常状态 + 未删除，带正文）
     *
     * @param agreementType 协议类型（user_agreement / privacy_policy / about_us）
     * @return 协议信息；不存在或已停用返回 null
     */
    public Agreement selectEnabledAgreementByType(String agreementType);

    /**
     * 校验协议类型是否唯一（每种类型只允许一条未删除记录）
     *
     * 修改时会排除自身（agreement.agreementId）。
     *
     * @param agreement 协议信息
     * @return true 可用；false 类型不合法或已被占用
     */
    public boolean checkAgreementTypeUnique(Agreement agreement);

    /**
     * 新增协议
     *
     * @param agreement 协议信息
     * @return 结果
     */
    public int insertAgreement(Agreement agreement);

    /**
     * 修改协议
     *
     * @param agreement 协议信息
     * @return 结果
     */
    public int updateAgreement(Agreement agreement);

    /**
     * 批量逻辑删除协议
     *
     * @param ids 需要删除的数据ID（逗号分隔）
     * @return 结果
     */
    public int deleteAgreementByIds(String ids);
}
