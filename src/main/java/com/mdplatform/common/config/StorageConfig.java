package com.mdplatform.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

@Configuration
@ConfigurationProperties(prefix = "md-platform.file-storage")
@Validated
@Getter
@Setter
public class StorageConfig {

    @NotBlank(message = "rootPath不能为空")
    private String rootPath = "./data/md_platform_data";

    @Min(value = 1, message = "tempRetentionHours必须大于0")
    private int tempRetentionHours = 24;

    @NotBlank(message = "systemTemplatesPath不能为空")
    private String systemTemplatesPath = "system_templates";

    @NotBlank(message = "userUploadsPath不能为空")
    private String userUploadsPath = "user_uploads";
}