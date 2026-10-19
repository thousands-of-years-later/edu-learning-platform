package com.ruoyi.user.mapper;

import com.ruoyi.user.domain.PointsLog;

/**
 * 积分流水 数据层
 *
 * 只有插入：流水是审计数据，不提供修改/删除方法（要纠正就再记一条 adjust）。
 *
 * @author edu
 */
public interface PointsLogMapper
{
    /**
     * 记一条积分变动（logId 由 useGeneratedKeys 回填）
     *
     * @param log 流水（userId / changeAmount / balance / type / refId / remark 由调用方填好）
     * @return 影响行数
     */
    public int insertLog(PointsLog log);
}
