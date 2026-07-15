package com.mdplatform.management.dto;

import javax.validation.constraints.NotBlank;

/**
 * 登录请求DTO - 用于用户登录接口的请求参数
 *
 * <p>功能：封装用户登录所需的用户名和密码参数，并提供Bean Validation校验</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
public class LoginRequest {

    /** 用户名，不能为空 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码，不能为空 */
    @NotBlank(message = "密码不能为空")
    private String password;

    public LoginRequest() {}

    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
