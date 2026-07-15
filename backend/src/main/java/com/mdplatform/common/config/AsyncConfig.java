package com.mdplatform.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务配置类
 *
 * <p>配置Spring管理的线程池，用于异步任务执行。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>启用Spring异步任务支持（@EnableAsync）</li>
 *   <li>配置ThreadPoolTaskExecutor线程池</li>
 *   <li>设置线程池参数（核心线程数、最大线程数、队列容量等）</li>
 *   <li>配置优雅关闭策略</li>
 *   <li>统一异常处理</li>
 * </ul>
 *
 * <p>线程池参数说明：</p>
 * <ul>
 *   <li>corePoolSize: 核心线程数，线程池维护的最小线程数量</li>
 *   <li>maxPoolSize: 最大线程数，线程池允许创建的最大线程数量</li>
 *   <li>queueCapacity: 队列容量，任务队列的最大长度</li>
 *   <li>threadNamePrefix: 线程名称前缀，便于日志追踪和调试</li>
 *   <li>waitForTasksToCompleteOnShutdown: 应用关闭时是否等待任务完成</li>
 *   <li>awaitTerminationSeconds: 等待任务完成的最大时间（秒）</li>
 * </ul>
 *
 * <p>使用方式：</p>
 * <pre>
 * // 方式1：通过@Async注解
 * &#64;Async
 * public void asyncMethod() { ... }
 *
 * // 方式2：注入ThreadPoolTaskExecutor
 * &#64;Autowired
 * private ThreadPoolTaskExecutor taskExecutor;
 *
 * CompletableFuture.runAsync(() -> {...}, taskExecutor);
 * </pre>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    /** 核心线程数，默认10 */
    @Value("${spring.task.execution.pool.core-size:10}")
    private int corePoolSize;

    /** 最大线程数，默认50 */
    @Value("${spring.task.execution.pool.max-size:50}")
    private int maxPoolSize;

    /** 队列容量，默认100 */
    @Value("${spring.task.execution.pool.queue-capacity:100}")
    private int queueCapacity;

    /** 线程名称前缀 */
    @Value("${spring.task.execution.thread-name-prefix:Async-}")
    private String threadNamePrefix;

    /** 应用关闭时等待任务完成的超时时间（秒） */
    @Value("${spring.task.execution.pool.await-termination-seconds:60}")
    private int awaitTerminationSeconds;

    /**
     * 配置ThreadPoolTaskExecutor线程池Bean（返回具体类型以便按类型注入）
     *
     * <p>创建Spring管理的线程池，支持优雅关闭和任务追踪。
     * 返回ThreadPoolTaskExecutor类型，支持通过类型注入获取。</p>
     *
     * @return 配置好的ThreadPoolTaskExecutor实例
     */
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        log.info("[异步配置] 初始化ThreadPoolTaskExecutor: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                corePoolSize, maxPoolSize, queueCapacity);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数：线程池维护的最小线程数量
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数：线程池允许创建的最大线程数量
        executor.setMaxPoolSize(maxPoolSize);

        // 队列容量：任务队列的最大长度
        executor.setQueueCapacity(queueCapacity);

        // 线程名称前缀：便于日志追踪和调试
        executor.setThreadNamePrefix(threadNamePrefix);

        // 应用关闭时等待任务完成：确保正在执行的任务能够完成
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 等待任务完成的最大时间（秒）：超时后强制关闭
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);

        // 拒绝策略：当队列满且线程数达到最大值时的处理策略
        // CallerRunsPolicy: 由调用线程执行该任务，降低新任务的提交速度
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        // 初始化线程池
        executor.initialize();

        log.info("[异步配置] ThreadPoolTaskExecutor初始化完成");
        return executor;
    }

    /**
     * AsyncConfigurer接口要求的异步执行器（委托给taskExecutor Bean）
     *
     * <p>返回与taskExecutor Bean相同类型，确保@Async注解能正确使用该线程池。</p>
     *
     * @return 异步执行器实例
     */
    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }

    /**
     * 异步任务异常处理器
     *
     * <p>统一处理@Async注解方法中抛出的未捕获异常。</p>
     *
     * @return 异常处理器实例
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            log.error("[异步任务异常] 方法: {}, 参数: {}, 异常: {}",
                    method.getName(),
                    params,
                    throwable.getMessage(),
                    throwable);
        };
    }
}