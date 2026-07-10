package com.mdplatform.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文件存储配置类，配置文件存储的根路径和临时目录
 *
 * <p>功能：
 *     1. 配置文件存储根路径（md-platform.file-storage.root-path）
 *     2. 配置临时文件保留时间
 *     3. 配置系统模板和用户上传目录路径
 * </p>
 *
 * <p>配置前缀：md-platform.file-storage</p>
 *
 * <p>注意：不使用@Validated注解，避免Spring上下文关闭时异步任务访问
 * 触发MethodValidationPostProcessor导致BeanCreationNotAllowedException。
 * 属性校验由application.yml的配置管理和启动时的自动校验保证。</p>
 *
 * @author 电解液MD平台
 * @version 1.1.0
 */
@Configuration
@ConfigurationProperties(prefix = "md-platform.file-storage")
@Getter
@Setter
public class StorageConfig {

    /** 文件存储根路径，所有用户数据的基础目录 */
    private String rootPath = "./data/md_platform_data";

    /** 临时文件保留时间（小时），超过此时间的临时文件将被自动清理 */
    private int tempRetentionHours = 24;

    /** 系统预置模板目录路径，存储分子模板、力场文件等共享资源 */
    private String systemTemplatesPath = "system_templates";

    /** 用户上传文件目录路径，存储用户上传的分子结构文件等 */
    private String userUploadsPath = "user_uploads";
}