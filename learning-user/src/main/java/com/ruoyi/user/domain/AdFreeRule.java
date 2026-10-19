package com.ruoyi.user.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 免广告兑换档位（设计稿 19_积分规则 的「积分兑换规则」）
 *
 *   10 积分  = 免广告 30 分钟
 *   30 积分  = 免广告 2 小时
 *   100 积分 = 免广告 24 小时
 *
 * 写死成常量而不是配置表：档位是产品定死的，做成配置反而多一张表 + 一套后台；
 * 以后真要可配置，把 TIERS 换成查表即可，调用方（PointsServiceImpl / 前端）不用改。
 *
 * 【叠加规则不在本类】：兑换时长怎么叠加、权益什么时候到期，由 PointsAccountMapper.exchangeAdFree
 * 那条 SQL 用 greatest(原到期时间, 现在) + 时长 一次算完（见该语句注释）。
 *
 * @author edu
 */
public class AdFreeRule
{
    /**
     * 档位表，每项 { 消耗积分, 免广告时长(分钟) }
     * 下标 0 对应对外暴露的 tier = 1（客户端传 1/2/3，从 1 开始更符合习惯）
     */
    private static final int[][] TIERS = { { 10, 30 }, { 30, 120 }, { 100, 1440 } };

    /** 档位总数 */
    public static final int TIER_COUNT = TIERS.length;

    /**
     * 档位是否合法（客户端传的 tier 从 1 开始）
     */
    public static boolean isValidTier(Integer tier)
    {
        return tier != null && tier >= 1 && tier <= TIER_COUNT;
    }

    /** 该档消耗多少积分 */
    public static int costOf(int tier)
    {
        return TIERS[tier - 1][0];
    }

    /** 该档免广告多少分钟 */
    public static int minutesOf(int tier)
    {
        return TIERS[tier - 1][1];
    }

    /**
     * 时长文案：整小时显示"X 小时"，否则显示"X 分钟"
     * （30 → "30 分钟"，120 → "2 小时"，1440 → "24 小时"）
     */
    public static String labelOf(int minutes)
    {
        if (minutes >= 60 && minutes % 60 == 0)
        {
            return (minutes / 60) + " 小时";
        }
        return minutes + " 分钟";
    }

    /**
     * 完整档位表（给 /api/v1/points/exchange/tiers 渲染"积分兑换规则"用）
     *
     * @return [{tier:1, cost:10, minutes:30, label:"30 分钟"}, ...]
     */
    public static List<Map<String, Object>> tiers()
    {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < TIERS.length; i++)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tier", i + 1);
            item.put("cost", TIERS[i][0]);
            item.put("minutes", TIERS[i][1]);
            item.put("label", labelOf(TIERS[i][1]));
            list.add(item);
        }
        return list;
    }
}
