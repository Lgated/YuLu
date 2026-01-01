package com.ityfz.yulu.common.config;

import com.ityfz.yulu.common.security.JwtAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


/**
 * Web MVC 配置类。
 * 注册 JWT 认证过滤器，配置跨域等。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {



    /**
     * 注册JWT认证过滤器
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilter() {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new JwtAuthFilter());
        registration.addUrlPatterns("/*");
        registration.setName("jwtAuthFilter");
        registration.setOrder(1); // 设置过滤器执行顺序

        // 排除不需要认证的接口
        registration.addInitParameter("excludedUrls",
                "/api/auth/login," +
                        "/api/auth/registerTenant," +
                        "/actuator/health," +
                        "/error");

        return registration;
    }

    /**
     * 跨域配置
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
                //匹配范围
        registry.addMapping("/**")
                //允许的来源域
                //TODO: 待域名上线后再修改
                .allowedOriginPatterns("*")
                //允许的请求方法
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                //允许浏览器携带任何自定义请求头
                .allowedHeaders("*")
                //核心开关：
                //允许前端 withCredentials = true（XMLHttpRequest 或 fetch credentials: 'include'）。
                //意味着可以携带 Cookie、Authorization 头、TLS 客户端证书。
                //一旦开启，第 2 条和第 4 条就不能再出现 *，必须显式列域名和头，否则浏览器直接拦截
                .allowCredentials(true)
                .maxAge(3600);
    }
}
