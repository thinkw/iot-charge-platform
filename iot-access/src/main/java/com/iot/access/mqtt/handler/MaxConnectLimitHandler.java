package com.iot.access.mqtt.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@ChannelHandler.Sharable
public class MaxConnectLimitHandler extends ChannelDuplexHandler {

    private final int maxConnections;
    private final AtomicInteger onlineConn = new AtomicInteger();

    public MaxConnectLimitHandler(@Value("${mqtt.server.max-connections:5000}") int maxConnections) {
        this.maxConnections = maxConnections;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        int curr = onlineConn.incrementAndGet();
        if (curr > maxConnections) {
            // 不在这里 decrement，由 channelInactive 回调统一处理，避免重复扣减
            log.warn("[连接限制] 连接数已达上限 {}，拒绝新连接", maxConnections);
            ctx.close();
            return;
        }
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        onlineConn.decrementAndGet();
        ctx.fireChannelInactive();
    }
}
