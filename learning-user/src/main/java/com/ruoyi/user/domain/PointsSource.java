package com.ruoyi.user.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 积分来源（明细类型）
 *
 * 积分流水的 type 列就是"来源编码"，这里的每个常量对应一种来源。
 * 以后要加新来源（比如：每日任务、邀请好友、管理员手动补发、活动奖励），
 * 只需要三步：
 *   1) 在本类加一个常量 + 一行中文标签（保持顺序即页面展示顺序）；
 *   2) 发积分的业务代码里写一条 learning_points_log，type 用新常量；
 *   3) 后台「积分记录 → 查看明细」会自动显示新来源，不需要改页面。
 *
 * 【不要】把来源塞进 remark：remark 是给人看的补充说明，来源要能统计、能筛选。
 *
 * @author edu
 */
public class PointsSource
{
    /** 签到（目前唯一的正向来源） */
    public static final String CHECKIN = "checkin";

    /** 兑换免广告（负向） */
    public static final String EXCHANGE = "exchange";

    /** 管理员手工调整（正负都可以） */
    public static final String ADJUST = "adjust";

    /**
     * 来源编码 -> 中文名
     *
     * 用 LinkedHashMap 保证遍历顺序 = 页面下拉/标签的顺序；
     * 以后加来源直接在这里加一行即可。
     */
    private static final Map<String, String> LABELS;

    static
    {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(CHECKIN, "签到");
        labels.put(EXCHANGE, "兑换");
        labels.put(ADJUST, "手工调整");
        LABELS = Collections.unmodifiableMap(labels);
    }

    private PointsSource()
    {
    }

    /**
     * 来源中文名；未知编码原样返回（保证以后加了来源但忘了加标签时也能看出来）
     */
    public static String labelOf(String code)
    {
        if (code == null || code.isEmpty())
        {
            return "其他";
        }
        String label = LABELS.get(code);
        return label == null ? code : label;
    }

    /**
     * 全部来源（编码 -> 中文），给后台筛选/展示预留
     */
    public static Map<String, String> all()
    {
        return LABELS;
    }
}
