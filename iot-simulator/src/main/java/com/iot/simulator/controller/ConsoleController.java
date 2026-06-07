package com.iot.simulator.controller;

import com.iot.simulator.client.MqttClientManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Scanner;

/**
 * 模拟器命令行交互控制器
 * <p>
 * 在模拟器启动后提供命令行交互界面，支持以下命令：
 * - start [count]    启动指定数量虚拟充电桩（默认10台）
 * - stop [sn]        停止指定充电桩（不指定则停止全部）
 * - status           查看所有充电桩状态
 * - fault [sn] [type] 触发指定充电桩故障（type: TEMP/VOLT/SHORT）
 * - recover [sn]     恢复指定充电桩为正常状态
 * - help             显示帮助信息
 * - exit             退出模拟器
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsoleController implements CommandLineRunner {

    private final MqttClientManager clientManager;

    @Override
    public void run(String... args) {
        printBanner();
        printHelp();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\n> ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            String[] parts = input.split("\\s+");
            String cmd = parts[0].toLowerCase();

            try {
                switch (cmd) {
                    case "start" -> {
                        int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
                        count = Math.min(count, 100); // 限制最多100台
                        System.out.println("正在启动 " + count + " 台虚拟充电桩...");
                        clientManager.startChargers(count);
                        System.out.println("启动完成！当前在线: " + clientManager.getStatus().size());
                    }
                    case "stop" -> {
                        if (parts.length > 1) {
                            clientManager.stopCharger(parts[1].toUpperCase());
                        } else {
                            clientManager.stopAll();
                        }
                    }
                    case "status" -> {
                        System.out.println("=== 虚拟充电桩状态 ===");
                        clientManager.getStatus().forEach(System.out::println);
                    }
                    case "fault" -> {
                        if (parts.length < 3) {
                            System.out.println("用法: fault <SN> <TYPE>  (TYPE: TEMP/VOLT/SHORT)");
                            break;
                        }
                        String sn = parts[1].toUpperCase();
                        String type = parts[2].toUpperCase();
                        clientManager.triggerFault(sn, type);
                    }
                    case "recover" -> {
                        if (parts.length < 2) {
                            System.out.println("用法: recover <SN>");
                            break;
                        }
                        clientManager.recoverCharger(parts[1].toUpperCase());
                    }
                    case "heartbeat-off" -> {
                        if (parts.length < 2) {
                            System.out.println("用法: heartbeat-off <SN>");
                            break;
                        }
                        String sn = parts[1].toUpperCase();
                        if (clientManager.pauseHeartbeat(sn)) {
                            System.out.println(sn + " 心跳已暂停（空闲超时后服务端判定离线）");
                        }
                    }
                    case "heartbeat-on" -> {
                        if (parts.length < 2) {
                            System.out.println("用法: heartbeat-on <SN>");
                            break;
                        }
                        String sn = parts[1].toUpperCase();
                        if (clientManager.resumeHeartbeat(sn)) {
                            System.out.println(sn + " 心跳已恢复");
                        }
                    }
                    case "help" -> printHelp();
                    case "exit" -> {
                        System.out.println("正在停止所有虚拟充电桩...");
                        clientManager.stopAll();
                        System.out.println("模拟器已退出");
                        System.exit(0);
                    }
                    default -> System.out.println("未知命令: " + cmd + "，输入 help 查看帮助");
                }
            } catch (NumberFormatException e) {
                System.out.println("参数格式错误，请输入有效的数字");
            } catch (Exception e) {
                System.err.println("命令执行异常: " + e.getMessage());
                log.error("命令执行异常", e);
            }
        }
    }

    private void printBanner() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║        IoT 充电桩虚拟模拟器 v1.0                  ║");
        System.out.println("║        IoT Charger Simulator                     ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
    }

    private void printHelp() {
        System.out.println();
        System.out.println("=== 命令列表 ===");
        System.out.println("  start [count]     启动指定数量虚拟充电桩（默认10，最多100）");
        System.out.println("  stop [sn]         停止指定充电桩（不指定则停止全部）");
        System.out.println("  status            查看所有虚拟充电桩状态");
        System.out.println("  fault <sn> <type> 触发指定充电桩故障（type: TEMP/VOLT/SHORT）");
        System.out.println("  recover <sn>      恢复指定充电桩为正常状态");
        System.out.println("  heartbeat-off <sn>暂停指定设备心跳（模拟离线）");
        System.out.println("  heartbeat-on <sn> 恢复指定设备心跳（模拟上线）");
        System.out.println("  help              显示此帮助信息");
        System.out.println("  exit              退出模拟器");
    }
}
