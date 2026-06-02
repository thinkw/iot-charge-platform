package com.iot.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.common.enums.DeviceStatusEnum;
import com.iot.common.exception.BusinessException;
import com.iot.common.model.PageResult;
import com.iot.core.entity.ChargeOrder;
import com.iot.core.entity.Charger;
import com.iot.core.mapper.ChargeOrderMapper;
import com.iot.core.mapper.ChargerMapper;
import com.iot.core.service.ChargerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 充电桩管理服务实现类
 * <p>
 * 提供运营后台对充电桩的增删改查和状态管理具体实现。
 * 新增充电桩时校验 SN 唯一性，修改状态时同步更新 Redis 缓存。
 * </p>
 *
 * @author IoT Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargerServiceImpl implements ChargerService {

    private final ChargerMapper chargerMapper;
    private final ChargeOrderMapper chargeOrderMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /** Redis 充电桩状态缓存 Key 前缀 */
    private static final String CHARGER_STATUS_PREFIX = "charger:status:";

    /** 充电中状态码 */
    private static final int STATUS_CHARGING = 2;

    // ==================== 查询 ====================

    /**
     * 分页查询充电桩列表
     * <p>
     * 支持按充电站、SN 模糊搜索、状态筛选。
     * 结果按 ID 升序排列。
     * </p>
     */
    @Override
    public PageResult<Charger> adminListChargers(Long stationId, String sn, Integer status,
                                                  int page, int size) {
        LambdaQueryWrapper<Charger> wrapper = new LambdaQueryWrapper<>();

        if (stationId != null) {
            wrapper.eq(Charger::getStationId, stationId);
        }
        if (sn != null && !sn.isBlank()) {
            wrapper.like(Charger::getSn, sn);
        }
        if (status != null) {
            wrapper.eq(Charger::getStatus, status);
        }
        wrapper.orderByAsc(Charger::getId);

        Page<Charger> pageResult = chargerMapper.selectPage(Page.of(page, size), wrapper);
        return PageResult.of(pageResult.getRecords(), pageResult.getTotal(), page, size);
    }

    /**
     * 获取充电桩详情
     */
    @Override
    public Charger adminGetCharger(Long id) {
        Charger charger = chargerMapper.selectById(id);
        if (charger == null) {
            throw new BusinessException(404, "充电桩不存在");
        }
        return charger;
    }

    // ==================== 新增 ====================

    /**
     * 新增充电桩
     * <p>
     * 校验 SN 唯一性，设置默认状态为离线(0)。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Charger adminCreateCharger(Charger charger) {
        // 1. SN 唯一性校验
        Long count = chargerMapper.selectCount(
                new LambdaQueryWrapper<Charger>().eq(Charger::getSn, charger.getSn())
        );
        if (count > 0) {
            throw new BusinessException(409, "设备SN「" + charger.getSn() + "」已存在");
        }

        // 2. 校验所属充电站存在
        if (charger.getStationId() == null) {
            throw new BusinessException(400, "所属充电站不能为空");
        }

        // 3. 设置默认值
        if (charger.getStatus() == null) {
            charger.setStatus(DeviceStatusEnum.OFFLINE.getCode());
        }
        if (charger.getPower() == null) {
            charger.setPower(new java.math.BigDecimal("7.00"));
        }

        chargerMapper.insert(charger);
        log.info("[充电桩管理] 新增成功 - id: {}, sn: {}, stationId: {}", charger.getId(),
                charger.getSn(), charger.getStationId());
        return charger;
    }

    // ==================== 修改 ====================

    /**
     * 修改充电桩
     * <p>
     * SN 和 stationId 不允许修改（保持设备归属一致性）。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Charger adminUpdateCharger(Charger charger) {
        Charger existing = chargerMapper.selectById(charger.getId());
        if (existing == null) {
            throw new BusinessException(404, "充电桩不存在");
        }

        // SN 和 stationId 不允许修改
        charger.setSn(existing.getSn());
        charger.setStationId(existing.getStationId());

        chargerMapper.updateById(charger);
        log.info("[充电桩管理] 修改成功 - id: {}, name: {}", charger.getId(), charger.getName());
        return chargerMapper.selectById(charger.getId());
    }

    // ==================== 删除 ====================

    /**
     * 删除充电桩
     * <p>
     * 删除前检查是否有进行中的充电订单。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDeleteCharger(Long id) {
        Charger charger = chargerMapper.selectById(id);
        if (charger == null) {
            throw new BusinessException(404, "充电桩不存在");
        }

        // 检查是否有进行中的充电订单
        long chargingCount = chargeOrderMapper.selectCount(
                new LambdaQueryWrapper<ChargeOrder>()
                        .eq(ChargeOrder::getChargerId, id)
                        .eq(ChargeOrder::getOrderStatus, STATUS_CHARGING)
        );
        if (chargingCount > 0) {
            throw new BusinessException(409, "该充电桩有进行中的充电订单，无法删除");
        }

        chargerMapper.deleteById(id);
        // 清理 Redis 缓存
        redisTemplate.delete(CHARGER_STATUS_PREFIX + id);
        log.info("[充电桩管理] 删除成功 - id: {}, sn: {}", id, charger.getSn());
    }

    // ==================== 状态管理 ====================

    /**
     * 修改充电桩启禁用状态
     * <p>
     * 启用：状态设为空闲(1)，更新 Redis 缓存
     * 禁用：状态设为离线(0)，更新 Redis 缓存
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminUpdateChargerStatus(Long id, Integer status) {
        Charger charger = chargerMapper.selectById(id);
        if (charger == null) {
            throw new BusinessException(404, "充电桩不存在");
        }

        int newStatus;
        if (status == 1) {
            // 启用 → 空闲
            newStatus = DeviceStatusEnum.IDLE.getCode();
        } else {
            // 禁用 → 离线
            newStatus = DeviceStatusEnum.OFFLINE.getCode();
        }

        charger.setStatus(newStatus);
        chargerMapper.updateById(charger);

        // 同步更新 Redis 缓存
        redisTemplate.opsForValue().set(CHARGER_STATUS_PREFIX + id, String.valueOf(newStatus));

        log.info("[充电桩管理] 状态更新 - id: {}, status: {} -> {}", id, status,
                newStatus == 1 ? "启用(空闲)" : "禁用(离线)");
    }
}
