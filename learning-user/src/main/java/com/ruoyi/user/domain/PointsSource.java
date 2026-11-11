package com.ruoyi.user.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 积分来源（明细类型）
 *
 * 目前五类来源（顺序即后台「积分记录」的展示/筛选顺序）：
 *   签到 / 观看课程 / 观看短视频 / 观看帖子 / 观看广告
 *
 * 【积分获取规则（2026-11 定稿）】
 *  - 观看课程、短视频、帖子：每条内容每天只能得一次积分，每次固定 +5 分；
 *    三类内容合计每天最多 50 分（即每天最多 10 条内容）。
 *    "一条内容"的粒度：课程 = 一个小节视频，短视频 = 一条短视频，帖子 = 一篇帖子。
 *    看完一分钟的视频也只给 5 分，不按秒累加。
 *  - 签到：7 天循环 1~7 分，断签从 1 分重来（见 CheckinRule）。
 *  - 观看广告：单独计分，不占上面 50 分的额度。
 *
 * 扩展方式：以后要加新来源（任务、邀请、活动…）只需要
 *   1) 在本类加一个常量 + 一行中文标签；
 *   2) 发积分的业务代码调用 IPointsService.awardPoints(...)，type 传新常量；
 * 后台「积分记录 → 查看明细」会自动显示新来源，不需要改页面、菜单和 SQL。
 *
 * 注意：兑换（exchange）是【消耗积分】，不算"来源"，只作为历史/支出类型保留展示；
 *      手工调整（adjust）同理，仅用于后台纠错。
 *
 * @author edu
 */
public class PointsSource
{
    /** 签到 */
    public static final String CHECKIN = "checkin";

    /** 观看课程（粒度：课程小节视频） */
    public static final String WATCH_COURSE = "watchCourse";

    /** 观看短视频 */
    public static final String WATCH_VIDEO = "watchVideo";

    /** 观看帖子 */
    public static final String WATCH_POST = "watchPost";

    /** 观看广告 */
    public static final String WATCH_AD = "watchAd";

    /** 每条内容每天给多少分（课程/短视频/帖子通用） */
    public static final int CONTENT_POINTS = 5;

    /** 课程 + 短视频 + 帖子 三类内容的每日合计上限 */
    public static final int CONTENT_DAILY_CAP = 50;

    // ===== 以下是"消耗/调整"，不是积分来源，只为历史数据和后台纠错保留 =====

    /** 兑换（老版本免广告兑换，消耗，负向） */
    public static final String EXCHANGE = "exchange";

    /** 管理员手工调整（正负都可以） */
    public static final String ADJUST = "adjust";

    /**
     * 五种来源：编码 -> 中文名
     *
     * 用 LinkedHashMap 保证遍历顺序 = 页面展示顺序
     */
    private static final Map<String, String> SOURCES;

    /**
     * 消耗/调整类：有自己的中文名，但不出现在"来源"列表里
     */
    private static final Map<String, String> CONSUME_TYPES;

    static
    {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(CHECKIN, "签到");
        sources.put(WATCH_COURSE, "观看课程");
        sources.put(WATCH_VIDEO, "观看短视频");
        sources.put(WATCH_POST, "观看帖子");
        sources.put(WATCH_AD, "观看广告");
        SOURCES = Collections.unmodifiableMap(sources);

        Map<String, String> consumes = new LinkedHashMap<>();
        consumes.put(EXCHANGE, "兑换");
        consumes.put(ADJUST, "手工调整");
        CONSUME_TYPES = Collections.unmodifiableMap(consumes);
    }

    private PointsSource()
    {
    }

    /**
     * 编码 -> 中文名
     *
     * 先查五种来源，再查消耗/调整，最后原样返回（防止加了来源忘了加标签时页面空白）
     */
    public static String labelOf(String code)
    {
        if (code == null || code.isEmpty())
        {
            return "其他";
        }
        String label = SOURCES.get(code);
        if (label == null)
        {
            label = CONSUME_TYPES.get(code);
        }
        return label == null ? code : label;
    }

    /**
     * 是否是合法的"积分来源"（五种之一）
     */
    public static boolean isSource(String code)
    {
        return code != null && SOURCES.containsKey(code);
    }

    /**
     * 是否是"内容观看"类来源（课程/短视频/帖子，受每日 50 分上限约束）
     */
    public static boolean isContentSource(String code)
    {
        return WATCH_COURSE.equals(code) || WATCH_VIDEO.equals(code) || WATCH_POST.equals(code);
    }

    /**
     * 是否是合法的明细类型（五种来源 + 兑换/调整）
     */
    public static boolean isKnown(String code)
    {
        return isSource(code) || (code != null && CONSUME_TYPES.containsKey(code));
    }

    /**
     * 五种来源（编码 -> 中文），给后台筛选/展示用
     */
    public static Map<String, String> all()
    {
        return SOURCES;
    }
}
