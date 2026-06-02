package com.iot.access.websocket;

import com.iot.core.dto.response.DashboardVO;
import com.iot.core.service.DeviceEventPublisher;
import com.iot.core.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 大屏数据定时推送器
 * <p>
 * 每 5 秒获取最新的仪表盘数据，通过 WebSocket 广播给所有已连接的管理端。
 * 替代前端频繁的 HTTP 轮询，减少服务端压力，实现真正实时的大屏数据刷新。
 * 通过 DeviceEventPublisher 接口推送，遵循依赖倒置原则。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardPushScheduler {

    private final StatisticsService statisticsService;
    private final WebSocketSessionManager sessionManager;
    private final DeviceEventPublisher deviceEventPublisher;

    /**
     * 定时推送仪表盘数据
     * <p>
     * 每 5 秒执行一次，从 StatisticsService 获取最新的 DashboardVO，
     * 通过 DeviceEventPublisher 广播 DASHBOARD_UPDATE 消息。
     * 无连接时跳过推送，避免不必要的统计查询。
     * </p>
     */
    @Scheduled(fixedRate = 5000)
    public void pushDashboardData() {
        // 无在线连接时跳过，避免无意义的统计查询
        int connectionCount = sessionManager.getConnectionCount();
        if (connectionCount == 0) {
            return;
        }

        try {
            DashboardVO dashboard = statisticsService.getDashboard();
            deviceEventPublisher.broadcast("DASHBOARD_UPDATE", dashboard);
            log.debug("[大屏推送] 已推送仪表盘数据到 {} 个连接", connectionCount);
        } catch (Exception e) {
            log.error("[大屏推送] 推送仪表盘数据失败", e);
        }
    }
}
