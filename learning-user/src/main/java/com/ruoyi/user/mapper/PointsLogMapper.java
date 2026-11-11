package com.ruoyi.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.user.domain.PointsLog;

/**
 * 积分流水 数据层
 *
 * 写操作只有插入：流水是审计数据，不提供修改/删除方法（要纠正就再记一条 adjust）。
 * 后台只读查询（selectAdminList）是新增的，不影响这条纪律。
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


    /**
     * 某个用户的积分明细（后台"积分记录 → 查看明细"用），按 log_id 倒序
     *
     * @param userId 用户ID
     * @param type   来源编码（可空：空 = 全部来源）
     */
    public List<PointsLog> selectByUser(@Param("userId") Long userId,
                                        @Param("type") String type);
}
