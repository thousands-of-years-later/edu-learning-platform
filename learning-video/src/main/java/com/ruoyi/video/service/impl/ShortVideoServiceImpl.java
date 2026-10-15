package com.ruoyi.video.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.config.OssConfig;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.video.domain.ShortVideo;
import com.ruoyi.video.mapper.ShortVideoMapper;
import com.ruoyi.video.service.IShortVideoService;

/**
 * 短视频 服务层实现
 *
 * @author edu
 */
@Service
public class ShortVideoServiceImpl implements IShortVideoService
{
    @Autowired
    private ShortVideoMapper shortVideoMapper;

    /**
     * 查询短视频列表
     *
     * @param shortVideo 短视频信息
     * @return 短视频
     */
    @Override
    public List<ShortVideo> selectShortVideoList(ShortVideo shortVideo)
    {
        return shortVideoMapper.selectShortVideoList(shortVideo);
    }

    /**
     * 根据视频ID查询短视频
     *
     * @param videoId 视频ID
     * @return 短视频
     */
    @Override
    public ShortVideo selectShortVideoById(Long videoId)
    {
        return shortVideoMapper.selectShortVideoById(videoId);
    }

    /**
     * 校验视频标题是否唯一
     *
     * @param shortVideo 短视频信息
     * @return 结果
     */
    @Override
    public boolean checkVideoTitleUnique(ShortVideo shortVideo)
    {
        // 重名校验发生在 insert/update 之前，这里必须先归一化标题，
        // 否则「" 抖音 "」会因为带空白查不到同名记录而绕过校验（与课程模块同一处理）
        shortVideo.setVideoTitle(StringUtils.trim(shortVideo.getVideoTitle()));
        Long videoId = StringUtils.isNull(shortVideo.getVideoId()) ? -1L : shortVideo.getVideoId();
        ShortVideo info = shortVideoMapper.checkVideoTitleUnique(shortVideo);
        if (StringUtils.isNotNull(info) && info.getVideoId().longValue() != videoId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 新增短视频
     *
     * @param shortVideo 短视频信息
     * @return 结果
     */
    @Override
    @Transactional
    public int insertShortVideo(ShortVideo shortVideo)
    {
        shortVideo.setVideoTitle(trimVideoTitle(shortVideo.getVideoTitle()));
        // 视频必传、封面选填，两者都必须是上传接口的产物
        assertUploadUrl("视频地址", shortVideo.getVideoUrl(), true);
        assertUploadUrl("封面地址", shortVideo.getCoverUrl(), false);
        return shortVideoMapper.insertShortVideo(shortVideo);
    }

    /**
     * 修改短视频
     *
     * @param shortVideo 短视频信息
     * @return 结果
     */
    @Override
    @Transactional
    public int updateShortVideo(ShortVideo shortVideo)
    {
        shortVideo.setVideoTitle(trimVideoTitle(shortVideo.getVideoTitle()));
        ShortVideo old = shortVideoMapper.selectShortVideoById(shortVideo.getVideoId());
        if (StringUtils.isNull(old))
        {
            throw new ServiceException("短视频不存在或已被删除");
        }
        // 传 null 表示「这一列不改」（Mapper 用 <if test="... != null">）；
        // 传了值就必须是上传产物，而且视频不允许被清空，封面可以
        if (shortVideo.getVideoUrl() != null)
        {
            assertUploadUrl("视频地址", shortVideo.getVideoUrl(), true);
        }
        assertUploadUrl("封面地址", shortVideo.getCoverUrl(), false);
        return shortVideoMapper.updateShortVideo(shortVideo);
    }

    /**
     * 批量逻辑删除短视频，并级联清理挂在它上面的点赞收藏与评论
     *
     * 顺序上【先删内容、后清子表】：删除语句把 del_flag='2' 与计数清零写在同一句，
     * 视频先落删除态后，并发的点赞/评论要么被 existsVideo 的 del_flag='0' 拦住，
     * 要么让 updateCount 的 del_flag='0' 兜底影响 0 行而整体回滚。
     * 反过来先清子表的话，中间窗口里提交的互动会永远留在库里 —— 那正是本次修的缺陷。
     * 三条语句同事务，任一失败整体回滚。
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteShortVideoByIds(String ids)
    {
        Long[] videoIds = Convert.toLongArray(ids);
        // 空串会被 Convert 转成空数组，<foreach> 将拼出 in () 造成 SQL 语法错误，必须拦截
        if (videoIds == null || videoIds.length == 0)
        {
            throw new ServiceException("未选择要删除的短视频数据");
        }
        // 1、软删视频并清零三个计数（同一句 SQL）
        int rows = shortVideoMapper.deleteShortVideoByIds(videoIds);
        // 2、清掉挂在视频上的点赞收藏（硬删除）与评论（软删除）
        shortVideoMapper.deleteInteractionByVideoIds(videoIds);
        shortVideoMapper.softDeleteCommentByVideoIds(videoIds);
        return rows;
    }

    /**
     * 标题归一化：trim 后为空就报错
     *
     * 与课程模块保持一致：校验和入库用同一份处理，避免「带空格的标题」既骗过重名校验、
     * 又被原样存进库里。
     */
    private String trimVideoTitle(String videoTitle)
    {
        String title = StringUtils.trim(videoTitle);
        if (StringUtils.isEmpty(title))
        {
            throw new ServiceException("视频标题不能为空");
        }
        return title;
    }

    /**
     * 视频/封面地址必须是上传接口返回的地址
     *
     * 页面上那句「请先上传视频文件」只是前端提示，绕过表单直接 POST 就能塞外链甚至脚本；
     * 而 cover_url 会在列表页被拼进 &lt;img src='...'&gt;，所以必须从源头卡住：
     * 只接受本地上传产物 /profile/...，或打开 OSS 之后的上传前缀。
     *
     * @param label    字段中文名，用于提示
     * @param url      待校验的地址
     * @param required 是否必填（视频必填、封面选填）
     */
    private void assertUploadUrl(String label, String url, boolean required)
    {
        if (StringUtils.isEmpty(url))
        {
            if (required)
            {
                throw new ServiceException("请先上传视频文件");
            }
            return;
        }
        if (url.startsWith("/profile/"))
        {
            return;
        }
        if (OssConfig.isEnabled() && url.startsWith(OssConfig.resolveUrlPrefix() + "/"))
        {
            return;
        }
        throw new ServiceException(label + "必须是上传接口返回的地址，请重新上传");
    }
}