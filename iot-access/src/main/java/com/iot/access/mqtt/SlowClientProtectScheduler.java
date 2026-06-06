package com.iot.access.mqtt;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 慢客户端保护调度器
 * <p>
 * 基于 {@link ChannelOption#WRITE_BUFFER_WATER_MARK} 的 isWritable 信号，
 * 对出站缓冲区持续积压（即消费者处理过慢）的客户端主动断连。
 * </p>
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>Netty 的 WRITE_BUFFER_WATER_MARK 在单个 Channel 的待写数据超过高水位时，
 *       自动将 {@code ch.isWritable()} 置为 {@code false}。</li>
 *   <li>本调度器以固定间隔（默认 10 秒）遍历所有在线 Channel。</li>
 *   <li>对 {@code !ch.isWritable()} 的 Channel，记录其首次变不可写的时间戳。</li>
 *   <li>若持续不可写超过阈值（默认 30 秒），判定为慢客户端，调用 {@code ch.close()} 断连。</li>
 *   <li>Channel 恢复可写后自动清除记录。</li>
 * </ol>
 *
 * @author IoT Team
 */
@Slf4j
@Component
public class SlowClientProtectScheduler {

    private final MqttSessionManager sessionManager;
    private final long maxNonWritableMillis;

    /** ChannelId → 首次变为不可写的时间戳 (epoch ms)，用于计算持续时长 */
    private final ConcurrentMap<ChannelId, Long> nonWritableSince = new ConcurrentHashMap<>();

    public SlowClientProtectScheduler(
            MqttSessionManager sessionManager,
            @Value("${mqtt.server.slow-client.max-non-writable-seconds:30}") long maxNonWritableSeconds) {
        this.sessionManager = sessionManager;
        this.maxNonWritableMillis = maxNonWritableSeconds * 1000;
        log.info("[慢客户端保护] 初始化完成 - maxNonWritableSeconds: {}s, checkInterval: cron", maxNonWritableSeconds);
    }

    /**
     * 定时检测慢客户端
     * <p>
     * 遍历所有在线 Channel，检查 isWritable 状态，对持续不可写的连接主动断连。
     * 同时清理已断连的陈旧记录，防止内存泄漏。
     * </p>
     */
    @Scheduled(cron = "${mqtt.server.slow-client.check-interval-cron:*/10 * * * * *}")
    public void detectSlowClients() {
        if (sessionManager.getOnlineCount() == 0) {
            // 无在线连接时清理陈旧记录
            if (!nonWritableSince.isEmpty()) {
                nonWritableSince.clear();
            }
            return;
        }

        long now = System.currentTimeMillis();
        int closed = 0;

        for (Channel ch : sessionManager.getAllChannels()) {
            if (!ch.isActive()) {
                continue;
            }

            ChannelId cid = ch.id();
            if (!ch.isWritable()) {
                // 记录首次变为不可写的时间
                nonWritableSince.putIfAbsent(cid, now);
                long since = nonWritableSince.get(cid);
                long duration = now - since;
                if (duration >= maxNonWritableMillis) {
                    log.warn("[慢客户端保护] Channel 持续不可写 {}ms，主动断连: remoteAddress={}, channelId={}",
                            duration, ch.remoteAddress(), cid);
                    ch.close();
                    nonWritableSince.remove(cid);
                    closed++;
                }
            } else {
                // 恢复可写，清除追踪记录
                nonWritableSince.remove(cid);
            }
        }

        // 清理已断连的死 key，防止内存泄漏
        int cleaned = 0;
        Iterator<ChannelId> it = nonWritableSince.keySet().iterator();
        while (it.hasNext()) {
            ChannelId cid = it.next();
            Channel ch = sessionManager.getChannelById(cid);
            if (ch == null || !ch.isActive()) {
                it.remove();
                cleaned++;
            }
        }

        if (closed > 0 || cleaned > 0) {
            log.info("[慢客户端保护] 本轮关闭 {} 个慢客户端，清理 {} 个陈旧记录，剩余追踪: {} 个",
                    closed, cleaned, nonWritableSince.size());
        }
    }
}
