package com.ruoyi.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.api.service.IApiTokenService;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.service.ISysUserService;

/**
 * 密码变更 + Token 吊销 的事务编排
 *
 * 放在 ruoyi-admin：只有这个模块同时看得到 ruoyi-system 的 ISysUserService
 * 和 learning-api 的 IApiTokenService，不需要给 learning-* 业务模块新增框架依赖。
 *
 * 改密和吊销必须在同一事务里：只改了密码却没删掉旧 Token，
 * 等于改密前泄露的 Token 依然能用，这个功能就白做了。
 *
 * @author edu
 */
@Service
public class UserPasswordChangeService
{
    private static final Logger log = LoggerFactory.getLogger(UserPasswordChangeService.class);

    @Autowired
    private ISysUserService userService;

    @Autowired
    private IApiTokenService apiTokenService;

    /**
     * 更新密码并吊销该用户全部 API Token（同一数据库事务）
     *
     * 后台自助改密、后台重置他人密码、APP 自助改密三个入口共用，
     * 保证任何入口改密后旧 Token 都立即失效。
     *
     * @param user 必须已带好 userId 和加密后的 password / salt
     * @return true=更新成功；false=用户不存在或更新 0 行
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean resetUserPwdAndRevokeTokens(SysUser user)
    {
        if (user == null || user.getUserId() == null)
        {
            return false;
        }
        try
        {
            if (userService.resetUserPwd(user) <= 0)
            {
                return false;
            }
            // 删除失败会抛异常并回滚上面的密码更新，避免「密码已改、旧 Token 仍有效」
            apiTokenService.revokeAll(user.getUserId());
            return true;
        }
        catch (Exception e)
        {
            // 不要把 SQL / 表名等内部细节返回给客户端；ServiceException 会触发事务回滚
            log.error("修改密码失败，userId={}", user.getUserId(), e);
            throw new ServiceException("修改密码异常，请联系管理员");
        }
    }
}
