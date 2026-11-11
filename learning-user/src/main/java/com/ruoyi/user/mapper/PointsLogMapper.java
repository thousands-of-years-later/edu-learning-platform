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

    /**
     * 某用户某时间段内、指定来源的积分合计（正数）
     *
     * 用于"课程 + 帖子每天最多 50 分"的上限判断：直接以流水为准，不信任何计数器。
     *
     * @param userId 用户ID
     * @param types  来源编码集合（watchCourse / watchPost）
     * @param begin  起始时间（含）
     * @param end    结束时间（不含）
     */
    public int sumPointsByTypesBetween(@Param("userId") Long userId,
                                       @Param("types") List<String> types,
                                       @Param("begin") java.util.Date begin,
                                       @Param("end") java.util.Date end);
}
