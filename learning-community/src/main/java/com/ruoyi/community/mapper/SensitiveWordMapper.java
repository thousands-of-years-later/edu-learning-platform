package com.ruoyi.community.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.community.domain.SensitiveWord;

/**
 * 违禁词 数据层
 *
 * @author edu
 */
public interface SensitiveWordMapper
{
    /** 后台列表（按词/状态筛） */
    public List<SensitiveWord> selectSensitiveWordList(SensitiveWord query);

    /** 按ID查 */
    public SensitiveWord selectSensitiveWordById(@Param("wordId") Long wordId);

    /** 按词查（唯一性校验用） */
    public SensitiveWord selectByWord(@Param("word") String word);

    /** 所有启用中的词（启动时加载缓存） */
    public List<String> selectEnabledWords();

    public int insertSensitiveWord(SensitiveWord word);

    public int updateSensitiveWord(SensitiveWord word);

    public int deleteSensitiveWordByIds(@Param("wordIds") Long[] wordIds);
}
