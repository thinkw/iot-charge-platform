package com.iot.access.mqtt;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DirectMemoryProtectScheduler {

    private final double highWatermark;
    private final double lowWatermark;

    private final MqttSessionManager mqttSessionManager;

    /** 被暂停 autoRead 的 ChannelId，轻量、不可变 */
    private final Set<ChannelId> pausedChannels = ConcurrentHashMap.newKeySet();

    /** JVM 允许的最大直接内存，启动时一次性获取 */
    private final long maxDirectMemory;

    public DirectMemoryProtectScheduler(MqttSessionManager mqttSessionManager,
                                        @Value("${mqtt.server.memory-protect.high-watermark:0.9}") double highWatermark,
                                        @Value("${mqtt.server.memory-protect.low-watermark:0.6}") double lowWatermark,
                                        @Value("${mqtt.server.memory-protect.max-direct-memory:0}") long configMaxDirectMemory) {
        this.mqttSessionManager = mqttSessionManager;
        this.highWatermark = highWatermark;
        this.lowWatermark = lowWatermark;
        this.maxDirectMemory = resolveMaxDirectMemory(configMaxDirectMemory);
        log.info("[内存保护] 初始化完成 - maxDirectMemory: {} MB ({} bytes), highWatermark: {}, lowWatermark: {}",
                maxDirectMemory / 1024 / 1024, maxDirectMemory, highWatermark, lowWatermark);
    }

    /**
     * 获取 JVM 允许的最大直接内存。
     * 优先级：
     * 1. 配置显式指定 mqtt.server.memory-protect.max-direct-memory（单位 bytes，>0 生效）
     * 2. 默认: Runtime.getRuntime().maxMemory()（HotSpot 默认 MaxDirectMemorySize = -Xmx）
     */
    private static long resolveMaxDirectMemory(long configMaxDirectMemory) {
        if (configMaxDirectMemory > 0) {
            log.info("[内存保护] 使用配置指定的 maxDirectMemory: {} MB", configMaxDirectMemory / 1024 / 1024);
            return configMaxDirectMemory;
        }
        long fallback = Runtime.getRuntime().maxMemory();
        log.info("[内存保护] 使用 -Xmx 作为 MaxDirectMemorySize: {} MB", fallback / 1024 / 1024);
        return fallback;
    }

    @Scheduled(cron = "${mqtt.server.memory-protect.check-interval-cron:*/10 * * * * *}")
    public void memoryScheduler() {
        List<BufferPoolMXBean> platformMXBeansList = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);

        long usedMemory = 0;

        for (BufferPoolMXBean bufferPoolMXBean : platformMXBeansList) {
            if ("direct".equals(bufferPoolMXBean.getName())) {
                usedMemory = bufferPoolMXBean.getMemoryUsed();
                break;
            }
        }

        double usedRate = (double) usedMemory / maxDirectMemory;
        log.debug("[内存保护] 原始数据 - usedMemory: {} MB, maxDirectMemory: {} MB, usedRate: {}",
                usedMemory / 1024 / 1024, maxDirectMemory / 1024 / 1024, usedRate);

        int stop = 0;
        int start = 0;

        if (usedRate >= highWatermark) {
            // 高水位：暂停所有可读 Channel
            Collection<Channel> allChannels = mqttSessionManager.getAllChannels();
            for (Channel ch : allChannels) {
                if (!pausedChannels.contains(ch.id()) && ch.config().isAutoRead()) {
                    ch.config().setAutoRead(false);
                    pausedChannels.add(ch.id());
                    stop++;
                }
            }
            // 清理已断连的死 ChannelId，防止 pausedChannels 无限增长
            int cleaned = 0;
            Iterator<ChannelId> it = pausedChannels.iterator();
            while (it.hasNext()) {
                ChannelId cid = it.next();
                Channel ch = mqttSessionManager.getChannelById(cid);
                if (ch == null || !ch.isActive()) {
                    it.remove();
                    cleaned++;
                }
            }
            if (cleaned > 0) {
                log.info("[内存保护] 清理 {} 个已断连的暂停记录，剩余暂停: {} 个", cleaned, pausedChannels.size());
            }
        } else if (usedRate <= lowWatermark && !pausedChannels.isEmpty()) {
            // 低水位：恢复被暂停的 Channel
            Iterator<ChannelId> it = pausedChannels.iterator();
            while (it.hasNext()) {
                ChannelId cid = it.next();
                Channel ch = mqttSessionManager.getChannelById(cid);
                if (ch == null || !ch.isActive()) {
                    // Channel 已断连，从记录中清除
                    it.remove();
                    continue;
                }
                if (!ch.config().isAutoRead()) {
                    ch.config().setAutoRead(true);
                    start++;
                }
                it.remove();
            }
        }

        if (stop > 0) {
            log.warn("[内存保护] 直接内存使用率 {}，暂停 {} 个 Channel 的读操作", usedRate, stop);
        } else if (start > 0) {
            log.info("[内存保护] 直接内存使用率 {}，恢复 {} 个 Channel 的读操作", usedRate, start);
        } else {
            log.debug("[内存保护] 直接内存使用率 {}，无需操作，暂停列表: {} 个", usedRate, pausedChannels.size());
        }
    }
}
