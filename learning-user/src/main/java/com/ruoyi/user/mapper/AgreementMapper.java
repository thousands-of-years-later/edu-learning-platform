package com.ruoyi.user.mapper;

import java.util.List;
import com.ruoyi.user.domain.Agreement;

/**
 * 平台协议 数据层
 *
 * @author edu
 */
public interface AgreementMapper
{
    /**
     * 查询协议列表（后台用，不返回 content 大字段）
     *
     * @param agreement 查询条件（可选：agreementType、title 模糊、status）
     * @return 协议集合
     */
    public List<Agreement> selectAgreementList(Agreement agreement);

    /**
     * 根据协议ID查询（带正文，编辑页用）
     *
     * @param agreementId 协议ID
     * @return 协议信息
     */
    public Agreement selectAgreementById(Long agreementId);

    /**
     * 按类型查询未删除的协议（类型查重用）
     *
     * agreement.agreementId 不为空时会排除该ID，用于「修改时排除自己」。
     *
     * @param agreement 至少带 agreementType；可选 agreementId
     * @return 协议信息；不存在返回 null
     */
    public Agreement selectAgreementByType(Agreement agreement);

    /**
     * 查询 App 端可见的协议列表（正常状态 + 未删除，不返回 content 大字段）
     *
     * @return 协议集合（按 order_num 升序）
     */
    public List<Agreement> selectEnabledAgreementList();

    /**
     * 按类型查询 App 端可见的协议（正常状态 + 未删除，带正文）
     *
     * @param agreementType 协议类型
     * @return 协议信息；不存在或已停用返回 null
     */
    public Agreement selectEnabledAgreementByType(String agreementType);

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
     * @param agreementIds 需要删除的协议ID
     * @return 结果
     */
    public int deleteAgreementByIds(Long[] agreementIds);
}
