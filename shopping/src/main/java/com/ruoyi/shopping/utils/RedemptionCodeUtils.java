package com.ruoyi.shopping.utils;

import java.security.SecureRandom;

/**
 * 兑换码生成器
 *
 * 格式：SHOP + 12 位随机字符（大写字母 + 数字），总长 16。
 * 字符集去掉了 0/O/1/I 这类肉眼容易混淆的字符，方便用户抄写/客服核对。
 *
 * 唯一性不靠这里保证：learning_shop_order.uk_shop_order_code 是最终闸门，
 * 撞码时由 Service 重新生成后重试（概率极低）。
 *
 * @author edu
 */
public class RedemptionCodeUtils
{
    /** 去掉 0/O/1/I 的字符集 */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    /** 随机部分长度 */
    private static final int RANDOM_LENGTH = 12;

    /** 固定前缀 */
    private static final String PREFIX = "SHOP";

    private static final SecureRandom RANDOM = new SecureRandom();

    private RedemptionCodeUtils()
    {
    }

    /**
     * 生成一个兑换码（不保证全局唯一，唯一性由数据库唯一键保证）
     */
    public static String generate()
    {
        StringBuilder code = new StringBuilder(PREFIX.length() + RANDOM_LENGTH);
        code.append(PREFIX);
        for (int i = 0; i < RANDOM_LENGTH; i++)
        {
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}
