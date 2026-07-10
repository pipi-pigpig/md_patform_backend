package com.mdplatform.management.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 注册请求DTO - 用于用户注册接口的请求参数
 *
 * <p>功能：封装用户注册所需的参数，并提供Bean Validation校验</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
public class RegisterRequest {

    /** 用户名，不能为空，长度3-50 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50之间")
    private String username;

    /** 密码，不能为空，最少6位 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6-100之间")
    private String password;

    /** 邮箱，可选，但必须符合邮箱格式 */
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 真实姓名 */
    private String realName;

    /** 所属机构 */
    private String organization;

    public RegisterRequest() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }
}
