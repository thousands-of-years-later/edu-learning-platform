package com.ruoyi.api.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.ruoyi.api.filter.ApiTokenFilter;
import com.ruoyi.api.service.IApiTokenService;

/**
 * /api/v1/** 过滤器注册
 *
 * 类名没沿用"FilterConfig"这个名字：ruoyi-framework 里已经有一个 FilterConfig
 * （注册 XSS 过滤器），重名虽然能编译，但排查问题时极易看错。
 *
 * @author edu
 */
@Configuration
public class ApiFilterConfig
{
    /**
     * 把 ApiTokenFilter 只挂在 /api/v1/* 上
     *
     * 这里是用 new 手工创建过滤器、并且只通过 FilterRegistrationBean 注册：
     * 一旦给 ApiTokenFilter 标上 @Component，Spring Boot 会把它自动注册到 /*，
     * 那时整个后台（包括登录页）都要带 Token 才打得开。
     */
    @Bean
    public FilterRegistrationBean<ApiTokenFilter> apiTokenFilterRegistration(IApiTokenService apiTokenService)
    {
        FilterRegistrationBean<ApiTokenFilter> registration = new FilterRegistrationBean<ApiTokenFilter>();
        // 只在正常请求上跑：错误页转发（ERROR）不必再校验一次
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setFilter(new ApiTokenFilter(apiTokenService));
        registration.addUrlPatterns("/api/v1/*");
        registration.setName("apiTokenFilter");
        // 排到最前面：Token 校验要尽可能早，没带 Token 的请求直接拿到 JSON 401，
        // 而不是先被 Shiro 那条链当成匿名请求处理一遍
        registration.setOrder(FilterRegistrationBean.HIGHEST_PRECEDENCE);
        return registration;
    }
}
