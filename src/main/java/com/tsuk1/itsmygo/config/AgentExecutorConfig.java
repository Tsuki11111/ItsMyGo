package com.tsuk1.itsmygo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 客服智能体的线程池。
 *
 * 每条 Converse 流会阻塞一个线程最长 deadline 秒，所以流线程池必须有界；
 * 满了直接拒绝（429），不能 CallerRuns，否则会占住 Tomcat 线程。
 * Stop / SSE 心跳放在独立小池里，避免被长流拖住。
 */
@Configuration
public class AgentExecutorConfig {

    @Bean(name = "agentExecutor", destroyMethod = "shutdown")
    public ExecutorService agentExecutor() {
        return new ThreadPoolExecutor(
                8, 32, 60, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                threadFactory("agent-stream-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 智能推广的延迟调度池：登录后 0~N 秒随机延迟发送，把流量摊到全天，
     * 不再用「每天 10:00 一次性扫全量」的固定 cron（避免同一时刻打满 DB/聊天推送）。
     */
    @Bean(name = "promotionExecutor", destroyMethod = "shutdown")
    public ScheduledThreadPoolExecutor promotionExecutor() {
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(2, threadFactory("promotion-"));
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    @Bean(name = "agentAuxExecutor", destroyMethod = "shutdown")
    public ScheduledThreadPoolExecutor agentAuxExecutor() {
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(2, threadFactory("agent-aux-"));
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private ThreadFactory threadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
