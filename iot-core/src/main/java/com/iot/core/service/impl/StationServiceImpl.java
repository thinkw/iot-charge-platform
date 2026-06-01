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
import com.iot.core.service.StationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class StationServiceImpl implements StationService {

    private final StationMapper stationMapper;
    private final ChargerMapper chargerMapper;
    private final PricingRuleMapper pricingRuleMapper;

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
     */
    private ChargerVO toChargerVO(Charger charger) {
        DeviceStatusEnum statusEnum = DeviceStatusEnum.fromCode(charger.getStatus());
        return ChargerVO.builder()
                .id(charger.getId())
                .sn(charger.getSn())
                .name(charger.getName())
                .power(charger.getPower())
                .status(charger.getStatus())
                .statusDesc(statusEnum.getDesc())
                .currentPower(charger.getCurrentPower())
                .chargedEnergy(charger.getChargedEnergy())
                .build();
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
