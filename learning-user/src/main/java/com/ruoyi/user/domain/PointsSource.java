package com.ruoyi.user.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 积分来源（明细类型）
 *
 * 目前四类来源（顺序即后台「积分记录」的展示/筛选顺序）：
 *   签到 / 观看课程 / 观看帖子 / 观看广告
 *
 * 【积分获取规则（2026-11-11 定稿）】
 *  - 观看课程小节、帖子：每条内容每天只能得一次积分，每次固定 +5 分；
 *    需真实观看/阅读满 30 秒才结算，之后不再累加（看 1 分钟也只给 5 分）。
 *    "一条内容"的粒度：课程 = 一个小节视频，帖子 = 一篇帖子。
 *  - 课程 + 帖子两类合计每天最多 50 分（即每天最多 10 条内容）。
 *  - 刷短视频【不再计分】（保留编码与标签只为兼容可能存在的历史数据）。
 *  - 签到：7 天循环 1~7 分，断签从 1 分重来（见 CheckinRule）。
 *  - 观看广告：完整看完一条 +10 分，每天最多 3 条（30 分），不占上面 50 分的额度。
 *    真实广告 SDK 后续接入，目前在后台规则里先把类型和分值定好。
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

    /** 观看课程（粒度：课程小节视频，看满 30 秒 +5，每天一次） */
    public static final String WATCH_COURSE = "watchCourse";

    /** 观看帖子（阅读满 30 秒 +5，每天一次） */
    public static final String WATCH_POST = "watchPost";

    /** 观看广告（完整看完一条 +10） */
    public static final String WATCH_AD = "watchAd";

    /**
     * 观看短视频（已停用，不再发分）
     *
     * 保留常量与标签，仅用于兼容历史流水；不放进 SOURCES，后台筛选里不会出现。
     */
    public static final String WATCH_VIDEO = "watchVideo";

    /** 每条内容（课程小节 / 帖子）每天给多少分 */
    public static final int CONTENT_POINTS = 5;

    /** 课程 + 帖子 两类内容的每日合计上限 */
    public static final int CONTENT_DAILY_CAP = 50;

    /** 完整看完一条广告给多少分 */
    public static final int AD_POINTS = 10;

    /** 广告每天最多计几条（3 条 = 30 分/天） */
    public static final int AD_DAILY_LIMIT = 3;

    /** 广告至少观看多少秒才算"看完"（接入真实 SDK 后由回调保证） */
    public static final int AD_MIN_SECONDS = 15;

    /** 观看/阅读满多少秒才结算内容积分 */
    public static final int CONTENT_MIN_SECONDS = 30;

    // ===== 以下不是"积分来源"：消耗、调整、已停用类型，只为历史数据和后台纠错保留 =====

    /** 兑换（老版本免广告兑换，消耗，负向） */
    public static final String EXCHANGE = "exchange";

    /** 管理员手工调整（正负都可以） */
    public static final String ADJUST = "adjust";

    /**
     * 四种来源：编码 -> 中文名
     *
     * 用 LinkedHashMap 保证遍历顺序 = 页面展示顺序
     */
    private static final Map<String, String> SOURCES;

    /**
     * 非来源类型（消耗/调整/已停用）：有中文名，但不出现在"来源"列表里
     */
    private static final Map<String, String> OTHER_TYPES;

    static
    {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(CHECKIN, "签到");
        sources.put(WATCH_COURSE, "观看课程");
        sources.put(WATCH_POST, "观看帖子");
        sources.put(WATCH_AD, "观看广告");
        SOURCES = Collections.unmodifiableMap(sources);

        Map<String, String> others = new LinkedHashMap<>();
        others.put(EXCHANGE, "兑换");
        others.put(ADJUST, "手工调整");
        others.put(WATCH_VIDEO, "观看短视频（已停用）");
        OTHER_TYPES = Collections.unmodifiableMap(others);
    }

    private PointsSource()
    {
    }

    /**
     * 编码 -> 中文名
     *
     * 先查四种来源，再查消耗/调整/停用类型，最后原样返回（防止加了来源忘了加标签时页面空白）
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
            label = OTHER_TYPES.get(code);
        }
        return label == null ? code : label;
    }

    /**
     * 是否是合法的"积分来源"（四种之一）
     */
    public static boolean isSource(String code)
    {
        return code != null && SOURCES.containsKey(code);
    }

    /**
     * 是否是"内容观看"类来源（课程 / 帖子，受每日 50 分上限约束）
     *
     * 短视频已停用，不在这里。
     */
    public static boolean isContentSource(String code)
    {
        return WATCH_COURSE.equals(code) || WATCH_POST.equals(code);
    }

    /**
     * 是否是合法的明细类型（四种来源 + 消耗/调整/已停用）
     */
    public static boolean isKnown(String code)
    {
        return isSource(code) || (code != null && OTHER_TYPES.containsKey(code));
    }

    /**
     * 四种来源（编码 -> 中文），给后台筛选/展示用
     */
    public static Map<String, String> all()
    {
        return SOURCES;
    }
}
