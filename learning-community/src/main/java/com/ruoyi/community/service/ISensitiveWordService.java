package com.ruoyi.community.service;

import java.util.List;
import com.ruoyi.community.domain.SensitiveWord;

/**
 * 违禁词 服务层
 *
 * 只服务帖子（标题/正文）发布校验；词库后台可维护，内存缓存，命中直接拒绝保存。
 *
 * @author edu
 */
public interface ISensitiveWordService
{
    /** 后台列表 */
    public List<SensitiveWord> selectSensitiveWordList(SensitiveWord query);

    /** 按ID查 */
    public SensitiveWord selectSensitiveWordById(Long wordId);

    /** 新增（词不能重复） */
    public int insertSensitiveWord(SensitiveWord word);

    /** 修改（词不能与其他记录重复） */
    public int updateSensitiveWord(SensitiveWord word);

    /** 批量删除 */
    public int deleteSensitiveWordByIds(String ids);

    /**
     * 校验文本是否包含违禁词；命中抛 ServiceException("内容包含违禁词，请修改")
     *
     * 传多个字段（标题、正文）时按换行拼接后统一匹配。
     */
    public void checkText(String... texts);
}
