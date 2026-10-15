package com.ruoyi.course.service.impl;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.course.domain.CourseRating;
import com.ruoyi.course.mapper.CourseRatingMapper;
import com.ruoyi.course.service.ICourseRatingService;

/**
 * 课程评分 服务层实现
 *
 * 计数维护的关键点（与 InteractionServiceImpl / CommentServiceImpl 同一套规矩）：
 *   1. 先写评分表，再按【影响行数】决定要不要动计数
 *   2. 计数用 rating_count = rating_count + 1、rating_score = rating_score + delta 的原子写法，
 *      不做"读出来加一再写回"
 *   3. 整个操作在一个事务里
 *
 * 接口返回的平均分/评分人数一律取评分表的 count(*) / sum(score) 这个【唯一真相】，
 * 不读 learning_course 的两列冗余统计 —— 那两列只给后台列表展示，
 * 漂移了有 learning_course_rating_fix_count.sql 兜底。
 *
 * @author edu
 */
@Service
public class CourseRatingServiceImpl implements ICourseRatingService
{
    @Autowired
    private CourseRatingMapper courseRatingMapper;

    @Override
    @Transactional
    public Map<String, Object> addRating(Long userId, Long courseId, Integer score)
    {
        checkParams(userId, courseId, score);
        if (courseRatingMapper.existsCourse(courseId) == 0)
        {
            throw new ServiceException("课程不存在或已被删除");
        }

        CourseRating rating = new CourseRating();
        rating.setCourseId(courseId);
        rating.setUserId(userId);
        rating.setScore(score);
        try
        {
            courseRatingMapper.insertRating(rating);
        }
        catch (DuplicateKeyException e)
        {
            // 唯一索引 uk_course_user 报重复 = 已经评过。按约定【不覆盖】：
            // 覆盖会让客户端双击「新增」时静默变成改分，用户不知道自己的分被改了。
            // 也不做「先查后插」：两次查询之间有竞态，唯一索引才是权威判定
            throw new ServiceException("您已评分，请使用修改");
        }

        // 原子累加冗余统计：人数 +1、总分 +本次评分
        int updated = courseRatingMapper.updateCourseRatingStats(courseId, 1, score);
        // 影响 0 行 = 课程在「存在性校验」之后又被删了。必须报错回滚，
        // 否则评分表多一条挂在已删课程上的孤儿记录、计数永久漂移
        if (updated == 0)
        {
            throw new ServiceException("课程不存在或已被删除");
        }
        return buildResult(score, courseRatingMapper.selectCourseStats(courseId));
    }

    @Override
    @Transactional
    public Map<String, Object> updateRating(Long userId, Long courseId, Integer score)
    {
        checkParams(userId, courseId, score);
        if (courseRatingMapper.existsCourse(courseId) == 0)
        {
            throw new ServiceException("课程不存在或已被删除");
        }

        // 加锁读：锁住自己的评分行，两个并发改分在这里串行，
        // 后到的事务读到的是最新旧分，下面的差值不会算在过期快照上
        CourseRating old = courseRatingMapper.selectRatingForUpdate(courseId, userId);
        if (old == null)
        {
            throw new ServiceException("您还未评分，请先评分");
        }

        CourseRating rating = new CourseRating();
        rating.setCourseId(courseId);
        rating.setUserId(userId);
        rating.setScore(score);
        int updated = courseRatingMapper.updateScoreByCourseAndUser(rating);
        // 影响 0 行 = 记录在查出来之后没了（本接口没有删分，正常到不了这里）。
        // 必须报错回滚，绝不能出现「提示成功但分数没变」
        if (updated == 0)
        {
            throw new ServiceException("您还未评分，请先评分");
        }

        // 改分只动总分：人数不变，总分按差值原子调整
        int delta = score - old.getScore();
        if (delta != 0)
        {
            int countUpdated = courseRatingMapper.updateCourseRatingStats(courseId, 0, delta);
            if (countUpdated == 0)
            {
                throw new ServiceException("课程不存在或已被删除");
            }
        }
        return buildResult(score, courseRatingMapper.selectCourseStats(courseId));
    }

    @Override
    public Map<String, Object> getRatingInfo(Long userId, Long courseId)
    {
        if (courseId == null)
        {
            throw new ServiceException("课程ID不能为空");
        }
        // 不可见的内容连评分统计都不给看（与 CommentServiceImpl.checkTarget 同一原则），
        // 否则能拿统计接口反推哪些 ID 是已停用课程
        if (courseRatingMapper.existsCourse(courseId) == 0)
        {
            throw new ServiceException("课程不存在或已被删除");
        }

        // userId 为 null 时按「未评分」返回；两个前缀（/api、/api/v1）本身都要求登录，正常到不了这里
        Integer myScore = null;
        if (userId != null)
        {
            CourseRating mine = courseRatingMapper.selectRatingByCourseAndUser(courseId, userId);
            myScore = mine == null ? null : mine.getScore();
        }
        return buildResult(myScore, courseRatingMapper.selectCourseStats(courseId));
    }

    /** 参数校验，顺序固定：登录 → 课程ID → 分数范围 */
    private void checkParams(Long userId, Long courseId, Integer score)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
        if (courseId == null)
        {
            throw new ServiceException("课程ID不能为空");
        }
        if (!CourseRating.isValidScore(score))
        {
            throw new ServiceException("评分只能为1-5的整数");
        }
    }

    /**
     * 组装接口返回：{myScore, avgScore, ratingCount}
     *
     * 平均分的算法收敛在 CourseRating.avgScore（保留 1 位小数 / HALF_UP / 无人评分返回 null），
     * 课程列表接口算 avgScore 用的是同一个方法，两处不会漂移。
     */
    private Map<String, Object> buildResult(Integer myScore, Map<String, Object> stats)
    {
        long ratingCount = ((Number) stats.get("ratingCount")).longValue();
        long ratingScore = ((Number) stats.get("ratingScore")).longValue();

        Double avgScore = CourseRating.avgScore(ratingCount, ratingScore);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("myScore", myScore);
        result.put("avgScore", avgScore);
        result.put("ratingCount", ratingCount);
        return result;
    }
}
