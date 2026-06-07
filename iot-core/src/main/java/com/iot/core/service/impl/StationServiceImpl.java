package com.iot.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.common.enums.DeviceStatusEnum;
import com.iot.common.exception.BusinessException;
import com.iot.common.model.PageResult;
import com.iot.core.dto.response.ChargerVO;
import com.iot.core.dto.response.StationDetailVO;
import com.iot.core.dto.response.StationVO;
import com.iot.core.entity.Charger;
import com.iot.core.entity.PricingRule;
import com.iot.core.entity.Station;
import com.iot.core.mapper.ChargerMapper;
import com.iot.core.mapper.PricingRuleMapper;
import com.iot.core.mapper.StationMapper;
import com.iot.core.service.DeviceService;
import com.iot.core.service.StationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 充电站服务实现类
 * <p>
 * 提供充电站列表查询、详情查看和充电桩详情功能。
 * 列表支持按距离（Haversine公式）、价格、可用桩数排序。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
public class StationServiceImpl implements StationService {

    private final StationMapper stationMapper;
    private final ChargerMapper chargerMapper;
    private final PricingRuleMapper pricingRuleMapper;

    /**
     * DeviceService 用于查询设备是否真正在线（Redis）。
     * required = false：当 iot-access 未加载时（如单元测试），此依赖为 null。
     */
    @Autowired(required = false)
    private DeviceService deviceService;

    public StationServiceImpl(StationMapper stationMapper, ChargerMapper chargerMapper,
                              PricingRuleMapper pricingRuleMapper) {
        this.stationMapper = stationMapper;
        this.chargerMapper = chargerMapper;
        this.pricingRuleMapper = pricingRuleMapper;
    }

    /**
     * 获取充电站列表
     * <p>
     * 排序说明：
     * - distance：使用 Haversine 公式计算用户与站点间距离后排序
     * - price：按该站所有有效计费规则中的最低电价排序
     * - available：按空闲充电桩数量降序排列
     * 默认按 ID 升序排列。
     * </p>
     */
    @Override
    public PageResult<StationVO> listStations(int page, int size, String name, String sortBy,
                                               BigDecimal latitude, BigDecimal longitude) {
        // 1. 查询站点，支持名称模糊搜索
        LambdaQueryWrapper<Station> queryWrapper = new LambdaQueryWrapper<Station>()
                .eq(Station::getStatus, 1) // 只查营业中的站点
                .orderByAsc(Station::getId);

        if (name != null && !name.isBlank()) {
            queryWrapper.like(Station::getName, name);
        }

        Page<Station> pageResult = stationMapper.selectPage(Page.of(page, size), queryWrapper);
        List<Station> stations = pageResult.getRecords();

        if (stations.isEmpty()) {
            return PageResult.of(new ArrayList<>(), pageResult.getTotal(), page, size);
        }

        // 2. 批量查询每个站的空闲桩数和最低电价
        List<Long> stationIds = stations.stream().map(Station::getId).toList();

        // 空闲桩数统计 Map<stationId, count>
        Map<Long, Long> availableCountMap = chargerMapper.selectList(
                new LambdaQueryWrapper<Charger>()
                        .in(Charger::getStationId, stationIds)
                        .eq(Charger::getStatus, DeviceStatusEnum.IDLE.getCode())
        ).stream().collect(Collectors.groupingBy(Charger::getStationId, Collectors.counting()));

        // 最低电价 Map<stationId, minPrice>
        Map<Long, BigDecimal> minPriceMap = pricingRuleMapper.selectList(
                new LambdaQueryWrapper<PricingRule>()
                        .in(PricingRule::getStationId, stationIds)
                        .eq(PricingRule::getStatus, 1)
        ).stream().collect(Collectors.toMap(
                PricingRule::getStationId,
                PricingRule::getElectricityPrice,
                BigDecimal::min)); // 同一站多条规则时取最低价

        // 全局电价（stationId=0）用于 fallback
        BigDecimal globalMinPrice = pricingRuleMapper.selectList(
                new LambdaQueryWrapper<PricingRule>()
                        .eq(PricingRule::getStationId, 0)
                        .eq(PricingRule::getStatus, 1)
        ).stream().map(PricingRule::getElectricityPrice)
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        // 3. 转换为 StationVO
        List<StationVO> voList = stations.stream().map(s -> {
            StationVO.StationVOBuilder builder = StationVO.builder()
                    .id(s.getId())
                    .name(s.getName())
                    .address(s.getAddress())
                    .longitude(s.getLongitude())
                    .latitude(s.getLatitude())
                    .businessHours(s.getBusinessHours())
                    .contact(s.getContact())
                    .status(s.getStatus())
                    .availableCount(availableCountMap.getOrDefault(s.getId(), 0L).intValue());

            // 最低电价：站级规则优先，否则使用全局电价
            BigDecimal minPrice = minPriceMap.getOrDefault(s.getId(), null);
            if (minPrice == null || minPrice.compareTo(BigDecimal.ZERO) == 0) {
                minPrice = globalMinPrice;
            }
            builder.minPrice(minPrice);

            // 距离计算（用户提供了经纬度时）
            if ("distance".equals(sortBy) && latitude != null && longitude != null
                    && s.getLatitude() != null && s.getLongitude() != null) {
                double distance = haversineDistance(
                        latitude.doubleValue(), longitude.doubleValue(),
                        s.getLatitude().doubleValue(), s.getLongitude().doubleValue());
                builder.distance(Math.round(distance * 100.0) / 100.0); // 保留2位小数
            }

            return builder.build();
        }).collect(Collectors.toList());

        // 4. 排序处理（数据库查询后内存排序）
        if ("price".equals(sortBy)) {
            voList.sort((a, b) -> a.getMinPrice().compareTo(b.getMinPrice()));
        } else if ("available".equals(sortBy)) {
            voList.sort((a, b) -> b.getAvailableCount().compareTo(a.getAvailableCount()));
        } else if ("distance".equals(sortBy)) {
            voList.sort((a, b) -> {
                Double d1 = a.getDistance() != null ? a.getDistance() : Double.MAX_VALUE;
                Double d2 = b.getDistance() != null ? b.getDistance() : Double.MAX_VALUE;
                return d1.compareTo(d2);
            });
        }

        return PageResult.of(voList, pageResult.getTotal(), page, size);
    }

    /**
     * 获取充电站详情
     */
    @Override
    public StationDetailVO getStationDetail(Long stationId) {
        Station station = stationMapper.selectById(stationId);
        if (station == null) {
            throw new BusinessException(404, "充电站不存在");
        }

        // 查询该站下所有充电桩
        List<Charger> chargers = chargerMapper.selectList(
                new LambdaQueryWrapper<Charger>().eq(Charger::getStationId, stationId)
        );

        // 转换为 VO
        StationVO stationVO = StationVO.builder()
                .id(station.getId())
                .name(station.getName())
                .address(station.getAddress())
                .longitude(station.getLongitude())
                .latitude(station.getLatitude())
                .businessHours(station.getBusinessHours())
                .contact(station.getContact())
                .status(station.getStatus())
                .build();

        List<ChargerVO> chargerVOs = chargers.stream().map(this::toChargerVO).toList();

        return StationDetailVO.builder()
                .station(stationVO)
                .chargers(chargerVOs)
                .build();
    }

    /**
     * 获取充电桩详情
     */
    @Override
    public ChargerVO getChargerDetail(Long chargerId) {
        Charger charger = chargerMapper.selectById(chargerId);
        if (charger == null) {
            throw new BusinessException(404, "充电桩不存在");
        }
        return toChargerVO(charger);
    }

    /**
     * 将 Charger 实体转换为 ChargerVO
     * <p>
     * <b>状态一致性保证</b>：MySQL charger.status 可能与 Redis 设备在线状态不一致
     * （如 handleOffline 更新 MySQL 失败时）。以 Redis device:status:{sn} 的 online 字段
     * 作为在线/离线判断的权威数据源：如果 Redis 显示离线，则强制覆盖 status 为 OFFLINE，
     * 避免前端显示在线但后端检测离线的不一致问题。
     * </p>
     */
    private ChargerVO toChargerVO(Charger charger) {
        int displayStatus = charger.getStatus();

        // 以 Redis 在线状态为准：如果设备不在线，强制显示为离线
        if (deviceService != null && charger.getSn() != null) {
            try {
                if (!deviceService.isDeviceOnline(charger.getSn())) {
                    // Redis 显示离线 → 覆盖 MySQL 状态为离线
                    // 例外：如果 MySQL 本身就是 FAULT，保留故障状态（比离线更具体）
                    if (displayStatus != DeviceStatusEnum.FAULT.getCode()) {
                        displayStatus = DeviceStatusEnum.OFFLINE.getCode();
                    }
                }
            } catch (Exception e) {
                // 防御性编程：Redis 查询异常时降级使用 MySQL 数据
                log.debug("[状态查询] Redis 在线状态查询异常 - SN: {}, 降级使用 MySQL 数据", charger.getSn());
            }
        }

        DeviceStatusEnum statusEnum = DeviceStatusEnum.fromCode(displayStatus);
        return ChargerVO.builder()
                .id(charger.getId())
                .sn(charger.getSn())
                .name(charger.getName())
                .power(charger.getPower())
                .status(displayStatus)
                .statusDesc(statusEnum.getDesc())
                .currentPower(charger.getCurrentPower())
                .chargedEnergy(charger.getChargedEnergy())
                .build();
    }

    // ==================== 管理端 CRUD ====================

    /**
     * 管理端分页查询充电站列表
     * <p>
     * 支持按名称模糊搜索和状态筛选，结果按ID升序排列。
     * </p>
     */
    @Override
    public PageResult<Station> adminListStations(String name, Integer status, int page, int size) {
        LambdaQueryWrapper<Station> wrapper = new LambdaQueryWrapper<>();

        if (name != null && !name.isBlank()) {
            wrapper.like(Station::getName, name);
        }
        if (status != null) {
            wrapper.eq(Station::getStatus, status);
        }
        wrapper.orderByAsc(Station::getId);

        Page<Station> pageResult = stationMapper.selectPage(Page.of(page, size), wrapper);
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(), page, size);
    }

    /**
     * 管理端获取充电站详情
     */
    @Override
    public Station adminGetStation(Long id) {
        Station station = stationMapper.selectById(id);
        if (station == null) {
            throw new BusinessException(404, "充电站不存在");
        }
        return station;
    }

    /**
     * 管理端新增充电站
     * <p>
     * 设置默认值：状态默认为营业中(1)，营业时间默认为 00:00-24:00。
     * </p>
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Station adminCreateStation(Station station) {
        if (station.getStatus() == null) {
            station.setStatus(1);
        }
        if (station.getBusinessHours() == null) {
            station.setBusinessHours("00:00-24:00");
        }
        stationMapper.insert(station);
        log.info("[充电站管理] 新增成功 - id: {}, name: {}", station.getId(), station.getName());
        return station;
    }

    /**
     * 管理端修改充电站
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Station adminUpdateStation(Station station) {
        Station existing = stationMapper.selectById(station.getId());
        if (existing == null) {
            throw new BusinessException(404, "充电站不存在");
        }
        stationMapper.updateById(station);
        log.info("[充电站管理] 修改成功 - id: {}, name: {}", station.getId(), station.getName());
        return stationMapper.selectById(station.getId());
    }

    /**
     * 管理端删除充电站
     * <p>
     * 删除前检查是否有关联的充电桩，有关联则不允许删除。
     * </p>
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void adminDeleteStation(Long id) {
        Station station = stationMapper.selectById(id);
        if (station == null) {
            throw new BusinessException(404, "充电站不存在");
        }

        // 检查是否有关联的充电桩
        long chargerCount = chargerMapper.selectCount(
                new LambdaQueryWrapper<Charger>().eq(Charger::getStationId, id)
        );
        if (chargerCount > 0) {
            throw new BusinessException(409, "该充电站下存在 " + chargerCount + " 个充电桩，无法删除");
        }

        stationMapper.deleteById(id);
        log.info("[充电站管理] 删除成功 - id: {}, name: {}", id, station.getName());
    }

    /**
     * 管理端修改充电站营业状态
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void adminUpdateStationStatus(Long id, Integer status) {
        Station station = stationMapper.selectById(id);
        if (station == null) {
            throw new BusinessException(404, "充电站不存在");
        }
        station.setStatus(status);
        stationMapper.updateById(station);
        log.info("[充电站管理] 状态更新 - id: {}, status: {} -> {}", id, station.getName(),
                status == 0 ? "暂停营业" : status == 1 ? "营业中" : "维护中");
    }

    /**
     * Haversine 公式计算两点间距离（km）
     * <p>
     * 公式：a = sin²(Δlat/2) + cos(lat1)*cos(lat2)*sin²(Δlon/2)
     *       c = 2 * atan2(√a, √(1−a))
     *       d = R * c
     * 其中 R = 6371 km（地球平均半径）
     * </p>
     *
     * @param lat1 用户纬度
     * @param lon1 用户经度
     * @param lat2 站点纬度
     * @param lon2 站点经度
     * @return 距离（km）
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
