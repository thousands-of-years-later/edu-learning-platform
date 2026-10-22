package com.ruoyi.interaction.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.interaction.domain.WatchHistory;
import com.ruoyi.interaction.mapper.WatchHistoryMapper;
import com.ruoyi.interaction.service.IWatchHistoryService;

/**
 * 观看历史 服务层处理
 *
 * 几条规矩：
 *   1) 进度【跟随最新】：重看直接覆盖 position，不取最大值 —— 历史里的"看到 X"永远是最新位置，
 *      断点续播也从这里接着播；
 *   2) 课程按【课程】粒度：一门课一行，videoId 记上次看的小节；上报的小节必须属于该课程，
 *      否则"点了 A 课却把 B 课的小节写进历史"，续播会跳到别的课里去；
 *   3) 写入是「先 insert，撞唯一索引再 update」：一条内容只留一行，并发上报也安全；
 *   4) 文章没有进度：position / duration / videoId 一律存 null，只留"看过 + 时间"。
 *
 * @author edu
 */
@Service
public class WatchHistoryServiceImpl implements IWatchHistoryService
{
    @Autowired
    private WatchHistoryMapper watchHistoryMapper;

    @Override
    @Transactional
    public Map<String, Object> report(Long userId, String targetType, Long targetId,
                                      Long videoId, Integer position, Integer duration)
    {
        checkUser(userId);
        String type = checkTargetType(targetType);
        if (targetId == null)
        {
            throw new ServiceException("内容ID不能为空");
        }

        WatchHistory history = new WatchHistory();
        history.setUserId(userId);
        history.setTargetType(type);
        history.setTargetId(targetId);

        if (WatchHistory.TARGET_SHORT_VIDEO.equals(type))
        {
            if (watchHistoryMapper.countShortVideo(targetId) == 0)
            {
                throw new ServiceException("内容不存在或当前不可观看");
            }
            // 时长以内容表为准（客户端上报的可能是估算值，表里的更可信）
            Integer realDuration = watchHistoryMapper.selectShortVideoDuration(targetId);
            history.setDuration(realDuration);
            history.setPosition(clampPosition(position, realDuration));
        }
        else if (WatchHistory.TARGET_COURSE.equals(type))
        {
            if (watchHistoryMapper.countCourse(targetId) == 0)
            {
                throw new ServiceException("内容不存在或当前不可观看");
            }
            if (videoId != null)
            {
                // 小节必须属于这门课，否则续播会跳到别的课程里去
                if (watchHistoryMapper.countCourseVideoOfCourse(videoId, targetId) == 0)
                {
                    throw new ServiceException("小节不属于该课程或已删除");
                }
                history.setVideoId(videoId);
                // 课程小节表没有时长列，只能信客户端上报的值（空 / 负数按未知处理）
                history.setDuration(duration == null || duration <= 0 ? null : duration);
                history.setPosition(clampPosition(position, history.getDuration()));
            }
            // 只打开了课程详情页、还没点小节：只记"看过"，进度留空
        }
        else
        {
            // 文章：点开就算看过，没有进度可记
            if (watchHistoryMapper.countPost(targetId) == 0)
            {
                throw new ServiceException("内容不存在或当前不可观看");
            }
        }

        boolean firstTime = false;
        try
        {
            watchHistoryMapper.insertHistory(history);
            firstTime = true;
        }
        catch (DuplicateKeyException e)
        {
            // 已经看过：同一条记录上覆盖进度与时间（唯一索引保证不会插出第二条）
            watchHistoryMapper.updateHistory(history);
        }

        // 短视频首次观看 → 播放数 +1（只有短视频表有 view_count 列）
        if (firstTime && WatchHistory.TARGET_SHORT_VIDEO.equals(type))
        {
            watchHistoryMapper.increaseShortVideoViewCount(targetId);
        }

        WatchHistory saved = watchHistoryMapper.selectOne(userId, type, targetId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("historyId", saved == null ? history.getHistoryId() : saved.getHistoryId());
        result.put("targetType", type);
        result.put("targetId", targetId);
        result.put("videoId", saved == null ? history.getVideoId() : saved.getVideoId());
        result.put("position", saved == null ? history.getPosition() : saved.getPosition());
        result.put("duration", saved == null ? history.getDuration() : saved.getDuration());
        result.put("firstTime", firstTime);
        return result;
    }

    @Override
    public Map<String, Object> progress(Long userId, String targetType, Long targetId)
    {
        checkUser(userId);
        String type = checkTargetType(targetType);
        if (targetId == null)
        {
            throw new ServiceException("内容ID不能为空");
        }
        WatchHistory history = watchHistoryMapper.selectOne(userId, type, targetId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", history != null);
        result.put("videoId", history == null ? null : history.getVideoId());
        result.put("position", history == null ? null : history.getPosition());
        result.put("duration", history == null ? null : history.getDuration());
        result.put("lastViewTime", history == null ? null : formatTime(history.getLastViewTime()));
        return result;
    }

    @Override
    public List<WatchHistory> list(Long userId, String targetType)
    {
        checkUser(userId);
        String type = StringUtils.isEmpty(StringUtils.trim(targetType)) ? null : checkTargetType(targetType);
        List<WatchHistory> list = watchHistoryMapper.selectList(userId, type);
        for (WatchHistory history : list)
        {
            // 内容被删/不存在时 sourceDelFlag 为 null，同样算"已下架"
            history.setSourceDeleted(!WatchHistory.DEL_FLAG_NORMAL.equals(history.getSourceDelFlag()));
        }
        return list;
    }

    @Override
    public int remove(Long userId, Long historyId)
    {
        checkUser(userId);
        if (historyId == null)
        {
            throw new ServiceException("历史ID不能为空");
        }
        if (watchHistoryMapper.deleteById(historyId, userId) == 0)
        {
            throw new ServiceException("记录不存在或已被删除");
        }
        return 1;
    }

    @Override
    public int clear(Long userId)
    {
        checkUser(userId);
        return watchHistoryMapper.deleteAll(userId);
    }

    @Override
    public void checkQuery(String targetType)
    {
        if (StringUtils.isNotEmpty(StringUtils.trim(targetType)))
        {
            checkTargetType(targetType);
        }
    }

    private void checkUser(Long userId)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
    }

    /**
     * 校验并归一内容类型（trim 后只认 shortVideo / course / post）
     */
    private String checkTargetType(String targetType)
    {
        String type = StringUtils.trim(targetType);
        if (StringUtils.isEmpty(type))
        {
            throw new ServiceException("内容类型不能为空");
        }
        if (!WatchHistory.isValidTargetType(type))
        {
            throw new ServiceException("内容类型不合法（只支持 shortVideo / course / post）");
        }
        return type;
    }

    /**
     * 归一播放进度：null 保持 null；负数归 0；超过总时长按总时长算
     *
     * 不报错而是收敛，是因为进度只用于展示与续播，客户端偶发的越界值不该让整次上报失败。
     */
    private Integer clampPosition(Integer position, Integer duration)
    {
        if (position == null)
        {
            return null;
        }
        int value = Math.max(position, 0);
        if (duration != null && duration > 0 && value > duration)
        {
            value = duration;
        }
        return value;
    }

    private String formatTime(java.util.Date time)
    {
        return time == null ? null : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time);
    }
}
