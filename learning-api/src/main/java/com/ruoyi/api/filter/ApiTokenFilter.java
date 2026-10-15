package com.ruoyi.api.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ruoyi.api.service.IApiTokenService;
import com.ruoyi.api.utils.ApiTokenUtils;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.api.ApiUser;
import com.ruoyi.common.utils.StringUtils;

/**
 * /api/v1/** 的 Token 认证过滤器
 *
 * 为什么用普通 Servlet Filter，而不是往 Shiro 的过滤器链里加一个自定义 Shiro 过滤器：
 *   Shiro 那条链（user,kickout,onlineSession,syncOnlineSession,csrfValidateFilter）
 *   是给「后台页面 + Cookie 会话」用的。要把 Token 认证接进去，
 *   要么改 realm、要么给每个 Token 请求造一个假登录态，两者都会动到原有权限体系。
 *   这里独立成过滤器、只挂在 /api/v1/* 上（注册见 ApiFilterConfig），
 *   Shiro 那边只多一行 anon 放行，后台的菜单/角色/@RequiresPermissions/会话登录完全不受影响。
 *
 * 职责边界（重要）：只做「取 Token → 校验 → 放进 ApiContext → 放行」，
 * 不碰 Shiro 的 Subject，也不做任何业务判断。
 *
 * @author edu
 */
public class ApiTokenFilter implements Filter
{
    private static final Logger log = LoggerFactory.getLogger(ApiTokenFilter.class);

    /**
     * 免登录白名单
     *
     * logout 也在里面：Token 已经过期时，「退出登录」也应该能成功
     * （删不到就算了，接口做成幂等的），否则客户端会卡在退不出去的状态。
     */
    private static final List<String> WHITE_LIST = Arrays.asList("/api/v1/login", "/api/v1/register", "/api/v1/logout");

    private final IApiTokenService apiTokenService;

    /**
     * 依赖由 ApiFilterConfig 手工传进来
     *
     * 【不要】给本类加 @Component：Spring Boot 会把容器里的 Filter 自动注册到 /*，
     * 那时候连后台登录页都会被要求带 Token。
     */
    public ApiTokenFilter(IApiTokenService apiTokenService)
    {
        this.apiTokenService = apiTokenService;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException
    {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException
    {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        try
        {
            // 跨域预检请求不带业务凭证，直接放行，
            // 否则浏览器/小程序的预检会先被 401 挡掉，正式请求都发不出去
            if ("OPTIONS".equalsIgnoreCase(req.getMethod()))
            {
                chain.doFilter(request, response);
                return;
            }
            // 登录、注册、退出：不需要 Token
            if (isWhiteList(req))
            {
                chain.doFilter(request, response);
                return;
            }

            String token = ApiTokenUtils.resolveToken(req);
            if (StringUtils.isEmpty(token))
            {
                ApiTokenUtils.writeUnauthorized(res, "未登录或登录超时。请重新登录");
                return;
            }
            ApiUser user;
            try
            {
                user = apiTokenService.validate(token);
            }
            catch (Exception e)
            {
                // 校验要查库：数据库或表暂时不可用时也必须回 JSON，
                // 否则小程序收到的是一整页 HTML 错误页，解析直接失败
                log.error("校验 Token 时发生异常", e);
                ApiTokenUtils.writeError(res, ApiTokenUtils.INTERNAL_ERROR, "服务暂时不可用，请稍后再试");
                return;
            }
            if (user == null)
            {
                ApiTokenUtils.writeUnauthorized(res, "登录状态已失效，请重新登录");
                return;
            }
            // 校验通过：控制器只从 ApiContext 取用户ID，绝不接受前端传 userId
            ApiContext.setUser(user);
            chain.doFilter(request, response);
        }
        finally
        {
            // 必须清理（重要）：Tomcat 线程是复用的，残留身份会让下一个请求
            // （很可能是另一个用户的）冒用上一个用户 —— 直接是越权。
            // 放在 finally 里，保证抛异常、提前 return 也一定会清。
            ApiContext.clear();
        }
    }

    @Override
    public void destroy()
    {
    }

    /**
     * 是否白名单地址
     *
     * servletPath 天然不含 context-path，直接比就行；
     * 末尾多一个 "/" 也当同一个地址，免得客户端多个斜杠就 401。
     */
    private boolean isWhiteList(HttpServletRequest request)
    {
        String path = request.getServletPath();
        if (StringUtils.isEmpty(path))
        {
            return false;
        }
        if (path.length() > 1 && path.endsWith("/"))
        {
            path = path.substring(0, path.length() - 1);
        }
        return WHITE_LIST.contains(path);
    }
}
