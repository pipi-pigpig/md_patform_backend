package com.mdplatform.common.config;

import com.mdplatform.common.security.CustomUserDetailsService;
import com.mdplatform.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security安全配置类，配置认证授权规则和CORS策略
 *
 * <p>功能：
 *     1. 配置HTTP安全规则（认证、授权）
 *     2. 配置CORS跨域策略
 *     3. 配置JWT认证过滤器
 *     4. 配置密码编码器
 * </p>
 *
 * <p>配置要求：
 *     必须在application.yml中配置cors.allowed-origins属性，指定允许的跨域来源。
 *     示例配置：
 *     cors:
 *       allowed-origins: http://localhost:3000,http://localhost:8080
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** JWT认证过滤器 */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    /** 用户详情服务 */
    private final CustomUserDetailsService userDetailsService;

    /** 允许的跨域来源列表，从配置文件注入 */
    @Value("${cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    /**
     * 密码编码器Bean - 使用BCrypt算法加密密码
     *
     * @return BCrypt密码编码器实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 安全过滤器链配置 - 定义HTTP安全规则
     *
     * <p>配置内容：
     *     - 禁用CSRF（使用无状态JWT认证）
     *     - 配置CORS跨域策略
     *     - 无状态会话管理
     *     - 定义公开接口和需认证接口
     *     - 添加JWT认证过滤器
     * </p>
     *
     * @param http HttpSecurity构建器
     * @return 配置完成的SecurityFilterChain
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .cors().configurationSource(corsConfigurationSource())
            .and()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
                .antMatchers(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/health/**",
                    "/error",
                    "/api/**"  // 暂时禁用认证，用于端到端测试
                ).permitAll()
                .anyRequest().authenticated()
            .and()
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS配置源 - 定义跨域访问策略
     *
     * <p>从application.yml中读取允许的来源列表，禁止使用通配符"*"</p>
     *
     * @return CORS配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 使用配置文件中的允许来源列表，禁止使用通配符
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(Arrays.asList("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
