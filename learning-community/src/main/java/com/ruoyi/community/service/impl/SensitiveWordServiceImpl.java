package com.ruoyi.community.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.community.domain.SensitiveWord;
import com.ruoyi.community.mapper.SensitiveWordMapper;
import com.ruoyi.community.service.ISensitiveWordService;

/**
 * 违禁词 服务层实现
 *
 * 匹配前统一归一化：去空格、去标点/符号、转小写，
 * 这样"赌 博""赌*博""赌-博"都会命中"赌博"。
 *
 * 词库缓存在内存里，后台增删改后立即刷新；启动时由 @PostConstruct 加载。
 *
 * @author edu
 */
@Service
public class SensitiveWordServiceImpl implements ISensitiveWordService
{
    private static final Logger log = LoggerFactory.getLogger(SensitiveWordServiceImpl.class);

    /** 空白 + 标点 + 符号，全部去掉后再匹配 */
    private static final Pattern NOISE = Pattern.compile("[\\s\\p{P}\\p{S}]+");

    @Autowired
    private SensitiveWordMapper sensitiveWordMapper;

    /** 归一化后的词库（volatile：后台改了之后立刻对读线程可见） */
    private volatile List<String> cachedWords = Collections.emptyList();

    @PostConstruct
    public void init()
    {
        refreshCache();
    }

    /**
     * 重新加载词库；查库失败不阻断启动（记录日志，按空词库处理）
     */
    public void refreshCache()
    {
        try
        {
            List<String> words = sensitiveWordMapper.selectEnabledWords();
            List<String> normalized = new ArrayList<>();
            if (words != null)
            {
                for (String word : words)
                {
                    String text = normalize(word);
                    if (StringUtils.isNotEmpty(text))
                    {
                        normalized.add(text);
                    }
                }
            }
            cachedWords = normalized;
            log.info("违禁词库加载完成，共 {} 条", normalized.size());
        }
        catch (Exception e)
        {
            cachedWords = Collections.emptyList();
            log.warn("违禁词库加载失败，本次按空词库处理: {}", e.getMessage());
        }
    }

    @Override
    public List<SensitiveWord> selectSensitiveWordList(SensitiveWord query)
    {
        return sensitiveWordMapper.selectSensitiveWordList(query);
    }

    @Override
    public SensitiveWord selectSensitiveWordById(Long wordId)
    {
        return sensitiveWordMapper.selectSensitiveWordById(wordId);
    }

    @Override
    @Transactional
    public int insertSensitiveWord(SensitiveWord word)
    {
        normalizeAndCheck(word);
        if (sensitiveWordMapper.selectByWord(word.getWord()) != null)
        {
            throw new ServiceException("违禁词已存在");
        }
        int rows = sensitiveWordMapper.insertSensitiveWord(word);
        refreshCache();
        return rows;
    }

    @Override
    @Transactional
    public int updateSensitiveWord(SensitiveWord word)
    {
        if (word.getWordId() == null || sensitiveWordMapper.selectSensitiveWordById(word.getWordId()) == null)
        {
            throw new ServiceException("违禁词不存在或已被删除");
        }
        normalizeAndCheck(word);
        SensitiveWord same = sensitiveWordMapper.selectByWord(word.getWord());
        if (same != null && !same.getWordId().equals(word.getWordId()))
        {
            throw new ServiceException("违禁词已存在");
        }
        int rows = sensitiveWordMapper.updateSensitiveWord(word);
        refreshCache();
        return rows;
    }

    @Override
    @Transactional
    public int deleteSensitiveWordByIds(String ids)
    {
        Long[] wordIds = Convert.toLongArray(ids);
        if (wordIds == null || wordIds.length == 0)
        {
            throw new ServiceException("未选择要删除的违禁词");
        }
        int rows = sensitiveWordMapper.deleteSensitiveWordByIds(wordIds);
        refreshCache();
        return rows;
    }

    @Override
    public void checkText(String... texts)
    {
        if (cachedWords.isEmpty() || texts == null || texts.length == 0)
        {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String text : texts)
        {
            if (StringUtils.isNotEmpty(text))
            {
                sb.append(text).append('\n');
            }
        }
        String normalized = normalize(sb.toString());
        if (StringUtils.isEmpty(normalized))
        {
            return;
        }
        for (String word : cachedWords)
        {
            if (normalized.contains(word))
            {
                throw new ServiceException("内容包含违禁词，请修改");
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

    /**
     * 新增/修改前统一 trim、校验非空和长度
     */
    private void normalizeAndCheck(SensitiveWord word)
    {
        String value = StringUtils.trim(word.getWord());
        if (StringUtils.isEmpty(value))
        {
            throw new ServiceException("违禁词不能为空");
        }
        if (value.length() > 100)
        {
            throw new ServiceException("违禁词不能超过 100 个字符");
        }
        word.setWord(value);
        if (StringUtils.isEmpty(word.getStatus()))
        {
            word.setStatus("0");
        }
    }
}
