package com.ruoyi.framework.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import com.ruoyi.common.utils.StringUtils;

/**
 * 跨域（CORS）配置
 *
 * 为什么需要：小程序、安卓、iOS 原生请求不受浏览器同源策略限制，但
 *   ① H5 / uni-app 在浏览器里调试时会被浏览器拦截；
 *   ② 小程序内嵌 WebView、后台页面被别的站点 iframe 引用时同样受限。
 * 加一个 CORS 过滤器，三端联调时不会再因为"跨域"卡住。
 *
 * 为什么用 Filter 而不是 WebMvcConfigurer.addCorsMappings：
 *   Shiro 的过滤链对 /** 配的是 user + csrfValidateFilter（见 ShiroConfig），
 *   浏览器的预检请求 OPTIONS 不带 Cookie/令牌，用 addCorsMappings 时预检会先被
 *   Shiro 拦掉（302 跳登录或 401），响应里没有 CORS 头，浏览器直接报跨域失败。
 *   CorsFilter 注册在最高优先级，预检在进入 Shiro 之前就被应答（200 + CORS 头）。
 *
 * 安全约定：
 *   · 默认 allowed-origins=*（开发方便）；生产建议在 application.yml 里改成具体域名，
 *     如 `cors.allowed-origins: https://edu.example.com,https://h5.example.com`；
 *   · 用 OriginPattern 而不是 addAllowedOrigin("*")：前者在 allow-credentials=true 时
 *     也能回显具体来源，后者与凭据互斥会直接失效；
 *   · 本项目客户端用 Token（Authorization 头）而不是 Cookie，默认 allow-credentials=false。
 *
 * @author edu
 */
@Configuration
public class CorsConfig
{
    /** 允许的来源，逗号分隔；默认 * */
    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * 是否允许携带凭据（Cookie）。
     *
     * 移动端用 Token、不需要 Cookie，默认 false；
     * 如果以后有页面需要带 Cookie 跨域，记得改成具体域名 + true（* 与 true 组合浏览器会拒绝）。
     */
    @Value("${cors.allow-credentials:false}")
    private boolean allowCredentials;

    /** 预检结果缓存秒数：缓存后浏览器不会每个请求都发 OPTIONS */
    @Value("${cors.max-age:3600}")
    private long maxAge;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration()
    {
        CorsConfiguration config = new CorsConfiguration();
        for (String origin : StringUtils.split(allowedOrigins, ","))
        {
            if (StringUtils.isNotEmpty(origin.trim()))
            {
                config.addAllowedOriginPattern(origin.trim());
            }
        }
        // 允许所有请求头（Authorization、Content-Type、X-Token 等）与所有方法（GET/POST/OPTIONS…）
        config.addAllowedHeader(CorsConfiguration.ALL);
        config.addAllowedMethod(CorsConfiguration.ALL);
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(maxAge);
        // 让前端能读到这几个响应头：下载时的文件名、以及将来可能下发的令牌
        config.addExposedHeader("Content-Disposition");
        config.addExposedHeader("Authorization");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setName("corsFilter");
        // 最高优先级：预检请求必须在 Shiro 的过滤链之前被应答
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
