package com.iot.access.mqtt;

import com.iot.access.mqtt.codec.MqttDecoder;
import com.iot.access.mqtt.codec.MqttEncoder;
import com.iot.access.mqtt.handler.MaxConnectLimitHandler;
import com.iot.access.mqtt.handler.MqttMessageHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * MQTT 服务端（基于 Netty）
 * <p>
 * 启动 Netty TCP Server 监听 MQTT 设备连接，配置 Channel Pipeline：
 * - IdleStateHandler：90秒读空闲检测，超时自动断连
 * - MqttDecoder：MQTT 二进制协议 → MqttMessage
 * - MqttEncoder：MqttMessage → MQTT 二进制协议
 * - MqttMessageHandler：业务消息处理
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttServer {

    @Value("${mqtt.server.port:1883}")
    private int port;

    @Value("${mqtt.server.boss-thread-count:2}")
    private int bossThreadCount;

    @Value("${mqtt.server.worker-thread-count:8}")
    private int workerThreadCount;

    @Value("${mqtt.server.idle-read-timeout-seconds:90}")
    private int idleReadTimeoutSeconds;

    @Value("${mqtt.server.so-backlog:1024}")
    private int soBacklog;

    private final MqttMessageHandler mqttMessageHandler;
    private final MaxConnectLimitHandler maxConnectLimitHandler;

    /** Boss 线程组，负责接收连接 */
    private EventLoopGroup bossGroup;
    /** Worker 线程组，负责处理连接上的 I/O */
    private EventLoopGroup workerGroup;
    /** Netty Channel */
    private ChannelFuture channelFuture;

    /**
     * 启动 MQTT 服务器
     * <p>
     * 在 Spring Bean 初始化完成后自动调用。
     * 使用独立的 EventLoopGroup，与 HTTP 请求处理线程隔离。
     * </p>
     */
    @PostConstruct
    public void start() {
        bossGroup = new NioEventLoopGroup(bossThreadCount);
        workerGroup = new NioEventLoopGroup(workerThreadCount);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, soBacklog)    // 连接等待队列
                    .childOption(ChannelOption.SO_KEEPALIVE, true)  // TCP KeepAlive
                    .childOption(ChannelOption.TCP_NODELAY, true)   // 禁用 Nagle 算法
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            pipeline.addLast("limitConn", maxConnectLimitHandler);
                            // 1. 空闲检测：配置化的读空闲超时
                            pipeline.addLast("idleHandler",
                                    new IdleStateHandler(idleReadTimeoutSeconds, 0, 0, TimeUnit.SECONDS));

                            // 2. MQTT 协议编解码
                            pipeline.addLast("mqttDecoder", new MqttDecoder());
                            pipeline.addLast("mqttEncoder", new MqttEncoder());

                            // 3. 业务消息处理（@Sharable，可共享）
                            pipeline.addLast("mqttHandler", mqttMessageHandler);
                        }
                    });

            channelFuture = bootstrap.bind(port).sync();
            log.info("================================");
            log.info("MQTT Server 启动成功，监听端口: {}", port);
            log.info("Boss线程数: {}, Worker线程数: {}", bossThreadCount, workerThreadCount);
            log.info("================================");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("MQTT Server 启动失败", e);
            throw new RuntimeException("MQTT Server 启动失败", e);
        }
    }

    /**
     * 停止 MQTT 服务器
     * <p>
     * 在 Spring Bean 销毁前自动调用，优雅关闭 Netty 资源：
     * 1. 关闭 Channel，停止接收新连接
     * 2. 关闭 EventLoopGroup，释放所有线程资源
     * </p>
     */
    @PreDestroy
    public void stop() {
        log.info("MQTT Server 正在关闭...");

        if (channelFuture != null) {
            try {
                channelFuture.channel().close().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        log.info("MQTT Server 已关闭");
    }
}
