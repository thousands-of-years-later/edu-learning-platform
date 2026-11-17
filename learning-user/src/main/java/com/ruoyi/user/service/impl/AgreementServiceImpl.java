package com.ruoyi.user.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.user.domain.Agreement;
import com.ruoyi.user.mapper.AgreementMapper;
import com.ruoyi.user.service.IAgreementService;

/**
 * 平台协议 服务层处理
 *
 * 写操作都是单条语句，因此不加 @Transactional（本仓库的规则是「多条语句才需要事务」）。
 *
 * 类型唯一性只在服务层校验，没有建数据库唯一索引：
 * 本表统一软删除（del_flag='2'），(agreement_type, del_flag) 唯一索引会挡住
 * 「删掉后重新新增同类型」；而 MySQL 也不支持只对部分行生效的唯一索引。
 * 后台协议由管理员单人维护、写入频率极低，服务层查重足够。
 *
 * @author edu
 */
@Service
public class AgreementServiceImpl implements IAgreementService
{
    @Autowired
    private AgreementMapper agreementMapper;

    /**
     * 查询协议列表
     *
     * @param agreement 查询条件
     * @return 协议集合
     */
    @Override
    public List<Agreement> selectAgreementList(Agreement agreement)
    {
        return agreementMapper.selectAgreementList(agreement);
    }

    /**
     * 根据协议ID查询协议
     *
     * @param agreementId 协议ID
     * @return 协议信息
     */
    @Override
    public Agreement selectAgreementById(Long agreementId)
    {
        return agreementMapper.selectAgreementById(agreementId);
    }

    /**
     * 查询 App 端可见的协议列表
     *
     * @return 协议集合
     */
    @Override
    public List<Agreement> selectEnabledAgreementList()
    {
        return agreementMapper.selectEnabledAgreementList();
    }

    /**
     * 按类型查询 App 端可见的协议
     *
     * 类型只接受约定的三种；非法类型直接返回 null，不查库。
     *
     * @param agreementType 协议类型
     * @return 协议信息；不存在/非法/已停用返回 null
     */
    @Override
    public Agreement selectEnabledAgreementByType(String agreementType)
    {
        String type = StringUtils.trim(agreementType);
        if (!Agreement.isAllowedType(type))
        {
            return null;
        }
        return agreementMapper.selectEnabledAgreementByType(type);
    }

    /**
     * 校验协议类型是否唯一
     *
     * @param agreement 协议信息
     * @return true 可用；false 类型不合法或已被占用
     */
    @Override
    public boolean checkAgreementTypeUnique(Agreement agreement)
    {
        if (agreement == null || !Agreement.isAllowedType(agreement.getAgreementType()))
        {
            return false;
        }
        return agreementMapper.selectAgreementByType(agreement) == null;
    }

    /**
     * 新增协议
     *
     * @param agreement 协议信息
     * @return 结果
     */
    @Override
    public int insertAgreement(Agreement agreement)
    {
        trimTypeAndTitle(agreement);
        if (StringUtils.isEmpty(agreement.getStatus()))
        {
            agreement.setStatus(Agreement.STATUS_NORMAL);
        }
        if (agreement.getOrderNum() == null)
        {
            agreement.setOrderNum(0);
        }
        validate(agreement);
        return agreementMapper.insertAgreement(agreement);
    }

    /**
     * 修改协议
     *
     * @param agreement 协议信息
     * @return 结果
     */
    @Override
    public int updateAgreement(Agreement agreement)
    {
        if (agreement.getAgreementId() == null
                || StringUtils.isNull(agreementMapper.selectAgreementById(agreement.getAgreementId())))
        {
            throw new ServiceException("协议不存在或已被删除");
        }
        trimTypeAndTitle(agreement);
        validate(agreement);
        return agreementMapper.updateAgreement(agreement);
    }

    /**
     * 批量逻辑删除协议
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deleteAgreementByIds(String ids)
    {
        Long[] agreementIds = Convert.toLongArray(ids);
        // 空串会被 Convert 转成空数组，<foreach> 将拼出 in () 造成 SQL 语法错误，必须拦截
        if (agreementIds == null || agreementIds.length == 0)
        {
            throw new ServiceException("未选择要删除的协议数据");
        }
        return agreementMapper.deleteAgreementByIds(agreementIds);
    }

    /**
     * 类型和标题去空白（正文是 HTML，不能 trim）
     *
     * @param agreement 协议信息
     */
    private void trimTypeAndTitle(Agreement agreement)
    {
        agreement.setAgreementType(StringUtils.trim(agreement.getAgreementType()));
        agreement.setTitle(StringUtils.trim(agreement.getTitle()));
    }

    /**
     * 新增/修改共用的业务校验
     *
     * @param agreement 协议信息
     */
    private void validate(Agreement agreement)
    {
        if (!Agreement.isAllowedType(agreement.getAgreementType()))
        {
            throw new ServiceException("协议类型不合法");
        }
        if (StringUtils.isEmpty(agreement.getTitle()))
        {
            throw new ServiceException("协议标题不能为空");
        }
        if (StringUtils.isEmpty(agreement.getContent()))
        {
            throw new ServiceException("协议内容不能为空");
        }
        if (!checkAgreementTypeUnique(agreement))
        {
            throw new ServiceException("该类型的协议已存在，请直接编辑已有记录");
        }
    }
}
