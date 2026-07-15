package com.mdplatform.common.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

/**
 * Docker客户端配置类，配置与Docker引擎的连接参数
 *
 * <p>功能：
 *     1. 根据操作系统自动选择Docker连接方式（Windows使用命名管道，Linux/macOS使用Unix套接字）
 *     2. 创建并配置DockerClient Bean
 *     3. Docker连接失败时优雅降级，不影响应用启动
 * </p>
 *
 * <p>配置要求：
 *     必须在application.yml中配置以下属性：
 *     app.docker.md-container-name: MD计算引擎容器名称（默认md-engine）
 *     app.docker.enabled: 是否启用Docker集成（默认true）
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Configuration
@Slf4j
public class DockerConfig {

    /** MD计算引擎容器名称，从配置文件注入 */
    @Value("${app.docker.md-container-name:md-engine}")
    private String mdContainerName;

    /** 是否启用Docker集成，从配置文件注入 */
    @Value("${app.docker.enabled:true}")
    private boolean dockerEnabled;

    /**
     * 创建Docker客户端Bean
     *
     * <p>处理流程：
     *     1. 检查Docker是否启用，未启用则返回null
     *     2. 根据操作系统自动检测Docker连接方式
     *     3. 创建DockerClient并测试连接
     *     4. 连接失败时记录警告并返回null，不影响应用启动
     * </p>
     *
     * @return DockerClient实例，Docker未启用或连接失败时返回null
     */
    @Bean
    @Primary
    public DockerClient dockerClient() {
        if (!dockerEnabled) {
            log.info("ℹ️ Docker integration disabled by configuration. MD simulation features will not be available.");
            return null;
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();
            String dockerHost;

            if (os.contains("win")) {
                dockerHost = "npipe:////./pipe/docker_engine";
                log.info("ℹ️ Detected Windows, using named pipe: {}", dockerHost);
            } else if (os.contains("mac")) {
                dockerHost = "unix:///var/run/docker.sock";
                log.info("ℹ️ Detected macOS, using Unix socket: {}", dockerHost);
            } else {
                dockerHost = "unix:///var/run/docker.sock";
                log.info("ℹ️ Detected Linux, using Unix socket: {}", dockerHost);
            }

            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost(dockerHost)
                    .withDockerTlsVerify(false)
                    .build();

            ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .maxConnections(100)
                    .connectionTimeout(Duration.ofSeconds(30))
                    .responseTimeout(Duration.ofSeconds(45))
                    .build();

            DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient);
            dockerClient.pingCmd().exec();
            log.info("✅ Docker client connected successfully");
            return dockerClient;

        } catch (Exception e) {
            log.warn("⚠️ Failed to connect to Docker daemon: {}", e.getMessage());
            log.warn("ℹ️ Application will start without Docker integration. MD simulation features will be disabled.");
            return null;
        }
    }
}