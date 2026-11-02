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
     * 后台积分流水列表（分页由控制器驱动），带用户昵称
     *
     * @param userName 用户昵称模糊（可空）
     * @param type     类型 checkin / exchange / adjust（可空）
     */
    public List<PointsLog> selectAdminList(@Param("userName") String userName,
                                           @Param("type") String type);
}
