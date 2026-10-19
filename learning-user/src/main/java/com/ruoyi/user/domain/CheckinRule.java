package com.ruoyi.user.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 签到积分档位（设计稿 19_积分规则）
 *
 * 连续 1..7 天分别 5 / 8 / 10 / 12 / 15 / 18 / 20 分，第 8 天起从第 1 档重新循环。
 * 写死成常量而不是配置表：档位是产品在设计稿里定死的，做成配置反而多一张表 + 一套后台；
 * 以后真要可配置，把 TIERS 换成查表即可，pointsOf / tiers 的调用方都不用改。
 *
 * 【断签重置不在这里】：连续天数由 CheckinServiceImpl 按「昨天有没有签到记录」算出来，
 * 断了自然从 1 开始；本类只回答「连续第 N 天给几分」。
 *
 * @author edu
 */
public class CheckinRule
{
    /** 循环周期：连续 7 天后回到第 1 档 */
    public static final int LOOP_DAYS = 7;

    /** 每档积分，下标 0 = 连续第 1 天 */
    private static final int[] TIERS = { 5, 8, 10, 12, 15, 18, 20 };

    /**
     * 连续第 continuousDays 天能得多少分（第 8 天起按 7 天周期循环）
     *
     * @param continuousDays 连续天数（今天签完后的天数，>= 1）
     * @return 本次签到积分
     */
    public static int pointsOf(int continuousDays)
    {
        int day = continuousDays < 1 ? 1 : continuousDays;
        int index = (day - 1) % LOOP_DAYS;
        return TIERS[index];
    }

    /**
     * 完整档位表（给 /rules 接口渲染"签到积分规则"用）
     *
     * @return [{day:1, points:5}, ..., {day:7, points:20}]
     */
    public static List<Map<String, Object>> tiers()
    {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < TIERS.length; i++)
        {
            Map<String, Object> tier = new LinkedHashMap<>();
            tier.put("day", i + 1);
            tier.put("points", TIERS[i]);
            list.add(tier);
        }
        return list;
    }
}
