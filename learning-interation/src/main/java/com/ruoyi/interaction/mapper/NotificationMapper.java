package com.ruoyi.interaction.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.interaction.domain.Notification;

/**
 * 用户通知 数据层
 *
 * 写入只有 insertNotification 一个入口（取消互动不删通知、也没有删除接口）；
 * 列表/未读数/已读都是接收者维度（where user_id = ...），已读更新用 CAS
 * （where 带 user_id 与 is_read='0'，别人的/已读的改不动）。
 *
 * @author edu
 */
public interface NotificationMapper
{
    /**
     * 新增一条通知
     *
     * 不需要 useGeneratedKeys：响应里用不到新行ID（与 FollowMapper.insertFollow 同一取舍）。
     * 写入失败（比如表还没建）让异常直接抛给调用方，与触发动作同事务一起回滚 ——
     * 这里【不】try/catch 吞掉。
     */
    public int insertNotification(Notification notification);

    /**
     * 我的通知列表（分页由控制器 startPage() 驱动）
     *
     * @param isRead 读取状态筛选（null / 空 = 全部）
     * @param types  通知类型集合（null / 空 = 全部）；客户端 4 个 tab 用它过滤
     */
    public List<Notification> selectMyList(@Param("userId") Long userId,
                                           @Param("isRead") String isRead,
                                           @Param("types") List<String> types);

    /** 我的未读数（角标） */
    public int countUnread(@Param("userId") Long userId);

    /**
     * 标记单条已读（CAS：只改自己的、未读的那行）
     *
     * @return 影响行数；0 = 已读过 / 不是自己的 / 不存在，调用方按成功处理（幂等）
     */
    public int markRead(@Param("userId") Long userId, @Param("notificationId") Long notificationId);

    /** 我的全部未读标记已读 */
    public int markReadAll(@Param("userId") Long userId);

    /**
     * 查内容归属人（点赞/收藏/评论的被触发者）
     *
     * table / idColumn 由服务层从常量映射而来，只可能是
     * learning_post + post_id / learning_short_video + video_id，
     * 不接受任何外部输入，因此 ${} 拼接安全（与 InteractionMapper.updateCount 同一纪律）。
     * 课程没有归属人列，服务层根本不会调到这里。
     *
     * @return 归属人用户ID；内容不存在/已删/已下架时返回 null（调用方据此不产生通知）
     */
    public Long selectTargetOwner(@Param("table") String table,
                                  @Param("idColumn") String idColumn,
                                  @Param("targetId") Long targetId);
}
