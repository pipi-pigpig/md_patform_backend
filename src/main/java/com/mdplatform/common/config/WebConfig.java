package com.mdplatform.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置类，配置静态资源映射
 *
 * <p>功能：
 *     1. 配置静态资源映射（上传文件、结果文件）
 * </p>
 *
 * <p>注意：CORS跨域配置由SecurityConfig统一管理，此处不再重复配置</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 配置静态资源处理器
     *
     * <p>映射上传文件和结果文件的访问路径</p>
     *
     * @param registry 资源处理器注册表
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:./uploads/");
        registry.addResourceHandler("/results/**")
                .addResourceLocations("file:./results/");
    }
}
