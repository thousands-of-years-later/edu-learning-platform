package com.ruoyi.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.user.domain.FanBrief;

/**
 * 「我的主页」聚合数据 数据层
 *
 * 关注/粉丝/获赞这三个数字来自别的模块的表（learning_follow、learning_post），
 * 这里直接写纯 SQL 查，不反向依赖 learning-community：
 * 本模块只依赖 ruoyi-common，反向依赖会形成循环依赖
 * （与 NoteMapper / WatchHistoryMapper 同一做法）。
 *
 * @author edu
 */
public interface UserHomeMapper
{
    /**
     * 我的粉丝数（有多少人关注我）
     */
    public int countFans(@Param("userId") Long userId);

    /**
     * 我的关注数（我关注了多少人）
     */
    public int countFollowing(@Param("userId") Long userId);

    /**
     * 我获得的点赞总数：我发的帖子的 like_count 之和
     *
     * 口径（产品确认）：只算帖子，不算短视频；所有状态都算（草稿/待审核/已下架都计入），
     * 只排除已删除（del_flag='0'）。
     *
     * @return 点赞总数；没有帖子时返回 null，服务层按 0 处理
     */
    public Integer sumPostLikes(@Param("userId") Long userId);

    /**
     * 最近关注我的几个人（设计稿顶部头像预览，一般取 4 个）
     *
     * @param userId 我的用户ID
     * @param limit  取几条
     */
    public List<FanBrief> selectFanPreview(@Param("userId") Long userId, @Param("limit") int limit);
}
