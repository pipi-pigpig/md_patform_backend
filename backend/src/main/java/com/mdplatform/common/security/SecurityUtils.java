package com.mdplatform.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全工具类，提供获取当前认证用户信息的便捷方法
 *
 * <p>功能：
 *     1. 获取当前认证上下文中的Authentication对象
 *     2. 获取当前登录用户名
 *     3. 获取当前登录用户ID
 *     4. 判断当前请求是否已认证
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
public class SecurityUtils {

    /**
     * 私有构造方法，防止实例化工具类
     */
    private SecurityUtils() {
    }

    /**
     * 获取当前认证上下文中的Authentication对象
     *
     * @return 当前认证对象，未认证时返回null
     */
    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 获取当前登录用户名
     *
     * <p>从SecurityContext中提取CustomUserDetails获取用户名</p>
     *
     * @return 当前登录用户名，未认证时返回null
     */
    public static String getCurrentUsername() {
        Authentication authentication = getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            return ((CustomUserDetails) authentication.getPrincipal()).getUsername();
        }
        return null;
    }

    /**
     * 获取当前登录用户ID
     *
     * <p>从SecurityContext中提取CustomUserDetails获取用户ID</p>
     *
     * @return 当前登录用户ID，未认证时返回null
     */
    public static Long getCurrentUserId() {
        Authentication authentication = getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            return ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    /**
     * 判断当前请求是否已认证
     *
     * <p>认证条件：Authentication对象存在、已认证、且不是匿名用户</p>
     *
     * @return true表示已认证，false表示未认证或匿名用户
     */
    public static boolean isAuthenticated() {
        Authentication authentication = getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }
}