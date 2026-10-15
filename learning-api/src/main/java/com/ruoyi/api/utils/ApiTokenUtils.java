package com.ruoyi.api.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.alibaba.fastjson.JSON;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.StringUtils;

/**
 * Token 读取 / 未登录响应的公共小工具
 *
 * 过滤器要用（校验前取 Token、校验失败写 401），ApiAuthController 也要用
 * （退出登录时取 Token），所以单独放一个工具类，避免两边各写一份。
 *
 * @author edu
 */
public class ApiTokenUtils
{
    /** 标准请求头：Authorization: Bearer <token> */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /** 兼容请求头：部分客户端不方便改 Authorization，用 X-Token 也认 */
    public static final String HEADER_TOKEN = "X-Token";

    /** Bearer 前缀（比较时不区分大小写） */
    private static final String BEARER = "Bearer ";

    /** 未登录的 HTTP 状态码 */
    public static final int UNAUTHORIZED = 401;

    /** 服务端异常的 HTTP 状态码 */
    public static final int INTERNAL_ERROR = 500;

    private ApiTokenUtils()
    {
    }

    /**
     * 从请求头里取 Token
     *
     * 只认请求头，【故意不支持】?token=xxx：
     * URL 会被 Nginx、Tomcat 原样写进访问日志，长期有效的凭证放 URL 里等于写进日志扩散。
     *
     * @param request 请求
     * @return Token，没带则返回 null
     */
    public static String resolveToken(HttpServletRequest request)
    {
        String header = request.getHeader(HEADER_AUTHORIZATION);
        if (StringUtils.isNotEmpty(header))
        {
            if (header.regionMatches(true, 0, BEARER, 0, BEARER.length()))
            {
                return StringUtils.trim(header.substring(BEARER.length()));
            }
            // 也兼容直接把裸 Token 放在 Authorization 里的客户端
            return StringUtils.trim(header);
        }
        return StringUtils.trim(request.getHeader(HEADER_TOKEN));
    }

    /**
     * 输出统一的「未登录」JSON
     *
     * @param response 响应
     * @param msg      提示
     */
    public static void writeUnauthorized(HttpServletResponse response, String msg)
    {
        writeError(response, UNAUTHORIZED, msg);
    }

    /**
     * 输出统一结构的错误 JSON
     *
     * 这里是直接写响应体，不走 DispatcherServlet —— 过滤器里出的错，
     * 请求根本没进控制器，指望 GlobalExceptionHandler 是来不及的。
     * 返回结构与后台保持一致（{code,msg}），只是 code 换成对应的 HTTP 状态码：
     * 客户端按 HTTP 状态或 code 都能判断（401 该重新登录、500 稍后重试）。
     *
     * @param response 响应
     * @param status   HTTP 状态码
     * @param msg      提示
     */
    public static void writeError(HttpServletResponse response, int status, String msg)
    {
        AjaxResult result = AjaxResult.error(msg);
        result.put(AjaxResult.CODE_TAG, status);
        response.setStatus(status);
        ServletUtils.renderString(response, JSON.toJSONString(result));
    }
}
