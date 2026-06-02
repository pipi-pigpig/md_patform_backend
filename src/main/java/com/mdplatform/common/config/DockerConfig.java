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

@Configuration
@Slf4j
public class DockerConfig {

    @Value("${app.docker.md-container-name:md-engine}")
    private String mdContainerName;

    @Value("${app.docker.enabled:true}")
    private boolean dockerEnabled;

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