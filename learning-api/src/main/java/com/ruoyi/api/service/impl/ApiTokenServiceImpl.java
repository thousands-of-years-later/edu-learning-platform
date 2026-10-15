package com.ruoyi.api.service.impl;

import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.ruoyi.api.domain.ApiToken;
import com.ruoyi.api.mapper.ApiTokenMapper;
import com.ruoyi.api.service.IApiTokenService;
import com.ruoyi.common.core.api.ApiUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;

/**
 * API 访问令牌 服务层实现
 *
 * @author edu
 */
@Service
public class ApiTokenServiceImpl implements IApiTokenService
{
    /**
     * Token 有效期（天），默认 30
     *
     * 后台会话给 30 分钟，是因为人坐在浏览器前、掉线了重新登一次不费事；
     * 小程序不能动不动弹登录，所以给一个长有效期。
     * 配置写在 application.yml 的 api.token.expireDays，改它【只影响新签发】的 Token，
     * 已经发出去的 Token 还是按它自己那行的 expire_time 走。
     */
    @Value("${api.token.expireDays:30}")
    private int expireDays;

    @Autowired
    private ApiTokenMapper apiTokenMapper;

    @Override
    public ApiToken createToken(Long userId, String loginIp)
    {
        if (userId == null)
        {
            throw new ServiceException("签发令牌失败：用户ID为空");
        }
        Date now = DateUtils.getNowDate();
        ApiToken apiToken = new ApiToken();
        apiToken.setToken(ApiToken.randomToken());
        apiToken.setUserId(userId);
        // 128 是建表时 login_ip 的列长度：代理头可能是很长的 IP 串，先截断免得插入失败
        apiToken.setLoginIp(StringUtils.substring(loginIp, 0, 128));
        apiToken.setExpireTime(DateUtils.addDays(now, expireDays));
        apiToken.setCreateTime(now);
        apiTokenMapper.insertToken(apiToken);

        // 顺手清理这个用户已过期的 Token：表不会随时间无限膨胀，
        // 也就不用为这点小事再挂一个定时任务
        apiTokenMapper.deleteExpiredByUserId(userId);
        return apiToken;
    }

    @Override
    public ApiUser validate(String token)
    {
        if (StringUtils.isEmpty(token))
        {
            return null;
        }
        // 过期 / 账号停用 / 账号已删除都在 SQL 里挡掉（见 ApiTokenMapper.xml），
        // 这里刻意不区分失败原因，一律当未登录处理
        return apiTokenMapper.selectUserByToken(token);
    }

    @Override
    public void revoke(String token)
    {
        if (StringUtils.isNotEmpty(token))
        {
            apiTokenMapper.deleteToken(token);
        }
    }
}
