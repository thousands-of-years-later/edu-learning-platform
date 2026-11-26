package com.ruoyi.interaction.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.interaction.mapper.CommentSensitiveWordMapper;

/**
 * 评论违禁词校验
 *
 * 与 learning-community 的 SensitiveWordServiceImpl 用同一套归一化规则
 * （去空白/标点/符号 + 转小写），所以「加 微*信」也会命中「加微信」。
 *
 * 这里【不做内存缓存】，每次发布查一次启用词库：
 * 评论写入频率低、词库只有几十行；缓存反而会让后台新增的词等到重启才生效。
 * 查库失败按空词库放行（与帖子侧一致），只记 warn，不因词库故障阻断评论。
 *
 * @author edu
 */
@Component
public class CommentContentChecker
{
    private static final Logger log = LoggerFactory.getLogger(CommentContentChecker.class);

    /** 空白 + 标点 + 符号，全部去掉后再匹配 */
    private static final Pattern NOISE = Pattern.compile("[\\s\\p{P}\\p{S}]+");

    @Autowired
    private CommentSensitiveWordMapper sensitiveWordMapper;

    /**
     * 校验文本，命中违禁词抛业务异常（由全局异常处理转成前端提示）
     *
     * @param content 待校验文本
     */
    public void check(String content)
    {
        if (StringUtils.isEmpty(content))
        {
            return;
        }

        List<String> words;
        try
        {
            words = sensitiveWordMapper.selectEnabledWords();
        }
        catch (Exception e)
        {
            log.warn("评论违禁词查询失败，本次放行: {}", e.getMessage());
            return;
        }
        if (words == null || words.isEmpty())
        {
            return;
        }

        String normalized = normalize(content);
        if (StringUtils.isEmpty(normalized))
        {
            return;
        }

        for (String word : words)
        {
            String target = normalize(word);
            if (StringUtils.isNotEmpty(target) && normalized.contains(target))
            {
                throw new ServiceException("评论内容包含违禁词，请修改");
            }
        }
    }

    /**
     * 去空白、标点、符号后转小写
     */
    private String normalize(String text)
    {
        if (text == null)
        {
            return "";
        }
        return NOISE.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
