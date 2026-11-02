package com.ruoyi.web.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.ruoyi.common.config.RuoYiConfig;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger2的接口配置
 * 
 * @author ruoyi
 */
@Configuration
public class SwaggerConfig
{
    /** 系统基础配置 */
    @Autowired
    private RuoYiConfig ruoyiConfig;
    
    /**
     * 自定义的 OpenAPI 对象
     */
    @Bean
    public OpenAPI customOpenApi()
    {
        return new OpenAPI().components(new Components()
            // 设置认证的请求头
            .addSecuritySchemes("apikey", securityScheme()))
            .addSecurityItem(new SecurityRequirement().addList("apikey"))
            .info(getApiInfo());
    }
    
    @Bean
    public SecurityScheme securityScheme()
    {
        return new SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .name("Authorization")
            .in(SecurityScheme.In.HEADER)
            .scheme("Bearer")
            // 文档里提示：客户端令牌要带 Bearer 前缀
            .description("客户端接口填：Bearer <登录返回的 token>");
    }
    
    /**
     * 添加摘要信息
     */
    @SuppressWarnings("static-access")
    public Info getApiInfo()
    {
        return new Info()
            // 设置标题
            .title("智能广告基座 · 学习平台 接口文档")
            // 描述
            .description("客户端接口统一前缀 /api/v1，鉴权：请求头 Authorization: Bearer <token>（登录接口返回）。"
                    + "后台管理接口（/system/**、/course/** 等）走 Shiro 会话，需先在后台登录。")
            // 作者信息
            .contact(new Contact().name(ruoyiConfig.getName()))
            // 版本
            .version("版本号:" + ruoyiConfig.getVersion());
    }
}
