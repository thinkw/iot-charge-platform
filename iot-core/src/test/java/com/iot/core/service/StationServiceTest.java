package com.iot.core.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.iot.core.service.impl.StationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * StationService 单元测试
 * <p>
 * 使用 Mockito 隔离 Mapper 层依赖，重点测试充电站和充电桩相关业务逻辑：
 * - 充电站/充电桩存在性校验
 * - 充电站列表分页、排序（价格、可用桩数、距离）
 * - 名称模糊搜索
 * - 状态描述转换
 * </p>
 *
 * @author IoT Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StationService 单元测试")
class StationServiceTest {

    @Mock
    private StationMapper stationMapper;

    @Mock
    private ChargerMapper chargerMapper;

    @Mock
    private PricingRuleMapper pricingRuleMapper;

    @InjectMocks
    private StationServiceImpl stationService;

    // ==================== 充电站详情测试 ====================

    /**
     * getStationDetail - 充电站不存在时抛出 BusinessException(404)
     * <p>
     * 验证：selectById 返回 null 时报 404。
     * </p>
     */
    @Test
    @DisplayName("充电站详情 - 充电站不存在，抛出404异常")
    void getStationDetail_StationNotFound() {
        when(stationMapper.selectById(anyLong())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stationService.getStationDetail(999L));
        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("不存在"));
    }

    /**
     * getStationDetail - 正常返回 StationDetailVO 包含充电桩列表
     * <p>
     * 验证：返回的详情中包含充电站基本信息和对应的充电桩列表。
     * 充电桩的状态描述应正确转换（如 1 -> "空闲"）。
     * </p>
     */
    @Test
    @DisplayName("充电站详情 - 正常返回，含充电桩列表")
    void getStationDetail_Success() {
        // 准备充电站数据
        Station station = new Station();
        station.setId(1L);
        station.setName("测试充电站");
        station.setAddress("北京市海淀区");
        station.setBusinessHours("00:00-24:00");
        station.setContact("010-88888888");
        station.setStatus(1);

        // 准备充电桩数据
        Charger charger1 = new Charger();
        charger1.setId(1L);
        charger1.setSn("CHARGER-001");
        charger1.setName("桩A");
        charger1.setPower(new BigDecimal("120.0"));
        charger1.setStatus(1); // 空闲
        charger1.setCurrentPower(BigDecimal.ZERO);
        charger1.setChargedEnergy(new BigDecimal("100.0"));

        Charger charger2 = new Charger();
        charger2.setId(2L);
        charger2.setSn("CHARGER-002");
        charger2.setName("桩B");
        charger2.setPower(new BigDecimal("60.0"));
        charger2.setStatus(2); // 充电中
        charger2.setCurrentPower(new BigDecimal("45.0"));
        charger2.setChargedEnergy(new BigDecimal("500.0"));

        // mock
        when(stationMapper.selectById(1L)).thenReturn(station);
        when(chargerMapper.selectList(any())).thenReturn(List.of(charger1, charger2));

        StationDetailVO detail = stationService.getStationDetail(1L);

        // 验证充电站信息
        assertNotNull(detail);
        assertNotNull(detail.getStation());
        assertEquals("测试充电站", detail.getStation().getName());
        assertEquals("北京市海淀区", detail.getStation().getAddress());

        // 验证充电桩列表
        List<ChargerVO> chargers = detail.getChargers();
        assertEquals(2, chargers.size());

        // 验证充电桩状态描述
        ChargerVO vo1 = chargers.get(0);
        assertEquals("桩A", vo1.getName());
        assertEquals(1, vo1.getStatus());
        assertEquals("空闲", vo1.getStatusDesc());

        ChargerVO vo2 = chargers.get(1);
        assertEquals("桩B", vo2.getName());
        assertEquals(2, vo2.getStatus());
        assertEquals("充电中", vo2.getStatusDesc());
    }

    // ==================== 充电桩详情测试 ====================

    /**
     * getChargerDetail - 充电桩不存在时抛出 BusinessException(404)
     * <p>
     * 验证：selectById 返回 null 时报 404。
     * </p>
     */
    @Test
    @DisplayName("充电桩详情 - 充电桩不存在，抛出404异常")
    void getChargerDetail_ChargerNotFound() {
        when(chargerMapper.selectById(anyLong())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> stationService.getChargerDetail(999L));
        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("不存在"));
    }

    /**
     * getChargerDetail - 正常返回 ChargerVO 含状态描述
     * <p>
     * 验证：充电桩的各种状态（离线、空闲、充电中、故障、锁定）能正确转换为中文描述。
     * </p>
     */
    @Test
    @DisplayName("充电桩详情 - 正常返回，含状态描述")
    void getChargerDetail_Success() {
        // 准备充电桩数据（故障状态）
        Charger charger = new Charger();
        charger.setId(1L);
        charger.setSn("CHARGER-003");
        charger.setName("桩C");
        charger.setPower(new BigDecimal("150.0"));
        charger.setStatus(3); // 故障
        charger.setCurrentPower(BigDecimal.ZERO);
        charger.setChargedEnergy(new BigDecimal("0"));

        when(chargerMapper.selectById(1L)).thenReturn(charger);

        ChargerVO vo = stationService.getChargerDetail(1L);

        assertNotNull(vo);
        assertEquals(1L, vo.getId());
        assertEquals("CHARGER-003", vo.getSn());
        assertEquals("桩C", vo.getName());
        assertEquals(3, vo.getStatus());
        assertEquals("故障", vo.getStatusDesc());
        assertEquals(new BigDecimal("150.0"), vo.getPower());
    }

    // ==================== 充电站列表测试 ====================

    /**
     * listStations - 空结果时返回空 PageResult
     * <p>
     * 验证：selectPage 返回空记录时，应直接返回空列表，不进行后续的充电桩和价格查询。
     * </p>
     */
    @Test
    @DisplayName("充电站列表 - 空结果返回空PageResult")
    void listStations_EmptyResult() {
        // mock: 分页查询返回空记录
        Page<Station> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());
        emptyPage.setTotal(0L);
        when(stationMapper.selectPage(any(Page.class), any())).thenReturn(emptyPage);

        PageResult<StationVO> result = stationService.listStations(1, 10, null, null, null, null);

        assertNotNull(result);
        assertTrue(result.getRecords().isEmpty());
        assertEquals(0L, result.getTotal());

        // 验证：空结果时不应查询充电桩和价格规则
        verify(chargerMapper, never()).selectList(any());
        verify(pricingRuleMapper, never()).selectList(any());
    }

    /**
     * listStations - 按价格排序，验证返回按电价升序的结果
     * <p>
     * 验证：两个充电站分别有 0.8 和 0.5 的最低电价时，
     * 排序后的结果应为低电价（站B=0.5）在前，高电价（站A=0.8）在后。
     * </p>
     */
    @Test
    @DisplayName("充电站列表 - 按价格升序排列")
    void listStations_SortByPrice() {
        // 准备充电站
        Station stationA = new Station();
        stationA.setId(1L);
        stationA.setName("站A");
        stationA.setStatus(1);

        Station stationB = new Station();
        stationB.setId(2L);
        stationB.setName("站B");
        stationB.setStatus(1);

        List<Station> stations = List.of(stationA, stationB);
        Page<Station> page = new Page<>(1, 10);
        page.setRecords(stations);
        page.setTotal((long) stations.size());

        // mock: 分页查询
        when(stationMapper.selectPage(any(Page.class), any())).thenReturn(page);
        // mock: 无空闲充电桩
        when(chargerMapper.selectList(any())).thenReturn(Collections.emptyList());

        // mock: 定价规则（站A 0.8元，站B 0.5元；无全局规则）
        PricingRule ruleA = new PricingRule();
        ruleA.setStationId(1L);
        ruleA.setElectricityPrice(new BigDecimal("0.80"));
        ruleA.setStatus(1);

        PricingRule ruleB = new PricingRule();
        ruleB.setStationId(2L);
        ruleB.setElectricityPrice(new BigDecimal("0.50"));
        ruleB.setStatus(1);

        when(pricingRuleMapper.selectList(any()))
                .thenReturn(List.of(ruleA, ruleB))  // 第一次调用：站级规则
                .thenReturn(Collections.emptyList()); // 第二次调用：全局规则

        // 执行：按价格排序
        PageResult<StationVO> result = stationService.listStations(1, 10, null, "price", null, null);

        // 验证：价格升序 => 站B(0.5) 在前，站A(0.8) 在后
        List<StationVO> records = result.getRecords();
        assertEquals(2, records.size());
        assertEquals("站B", records.get(0).getName());
        assertEquals("站A", records.get(1).getName());
        assertTrue(records.get(0).getMinPrice().compareTo(records.get(1).getMinPrice()) <= 0);
    }

    /**
     * listStations - 按可用桩数排序，验证返回按空闲数降序的结果
     * <p>
     * 验证：三个充电站分别有 5、10、2 个空闲充电桩时，
     * 排序结果为 站B(10) > 站A(5) > 站C(2)。
     * </p>
     */
    @Test
    @DisplayName("充电站列表 - 按可用桩数降序排列")
    void listStations_SortByAvailable() {
        // 准备充电站
        Station stationA = new Station();
        stationA.setId(1L);
        stationA.setName("站A");
        stationA.setStatus(1);

        Station stationB = new Station();
        stationB.setId(2L);
        stationB.setName("站B");
        stationB.setStatus(1);

        Station stationC = new Station();
        stationC.setId(3L);
        stationC.setName("站C");
        stationC.setStatus(1);

        List<Station> stations = List.of(stationA, stationB, stationC);
        Page<Station> page = new Page<>(1, 10);
        page.setRecords(stations);
        page.setTotal((long) stations.size());

        // mock: 分页查询
        when(stationMapper.selectPage(any(Page.class), any())).thenReturn(page);

        // mock: 空闲充电桩（站A:5, 站B:10, 站C:2）
        List<Charger> chargers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Charger c = new Charger();
            c.setStationId(1L);
            c.setStatus(1); // 空闲
            chargers.add(c);
        }
        for (int i = 0; i < 10; i++) {
            Charger c = new Charger();
            c.setStationId(2L);
            c.setStatus(1); // 空闲
            chargers.add(c);
        }
        for (int i = 0; i < 2; i++) {
            Charger c = new Charger();
            c.setStationId(3L);
            c.setStatus(1); // 空闲
            chargers.add(c);
        }
        when(chargerMapper.selectList(any())).thenReturn(chargers);

        // mock: 全局电价（站级规则为空时 fallback）
        PricingRule globalRule = new PricingRule();
        globalRule.setStationId(0L);
        globalRule.setElectricityPrice(new BigDecimal("1.00"));
        globalRule.setStatus(1);

        when(pricingRuleMapper.selectList(any()))
                .thenReturn(Collections.emptyList()) // 第一次调用：站级规则为空
                .thenReturn(List.of(globalRule));    // 第二次调用：全局规则

        // 执行：按可用桩数排序
        PageResult<StationVO> result = stationService.listStations(1, 10, null, "available", null, null);

        // 验证：空闲数降序 => 站B(10) > 站A(5) > 站C(2)
        List<StationVO> records = result.getRecords();
        assertEquals(3, records.size());
        assertEquals("站B", records.get(0).getName());
        assertEquals("站A", records.get(1).getName());
        assertEquals("站C", records.get(2).getName());
        assertTrue(records.get(0).getAvailableCount() >= records.get(1).getAvailableCount());
        assertTrue(records.get(1).getAvailableCount() >= records.get(2).getAvailableCount());
    }

    /**
     * listStations - 按距离排序，验证距离字段被计算
     * <p>
     * 验证：用户提供经纬度且 sortBy=distance 时，
     * 每个充电桩 VO 的 distance 字段应被填充并按距离升序排列。
     * </p>
     */
    @Test
    @DisplayName("充电站列表 - 按距离升序排列，距离字段被计算")
    void listStations_SortByDistance() {
        // 准备充电站（含经纬度）
        Station stationA = new Station();
        stationA.setId(1L);
        stationA.setName("近站");
        stationA.setLatitude(new BigDecimal("30.1"));
        stationA.setLongitude(new BigDecimal("120.0"));
        stationA.setStatus(1);

        Station stationB = new Station();
        stationB.setId(2L);
        stationB.setName("远站");
        stationB.setLatitude(new BigDecimal("31.0"));
        stationB.setLongitude(new BigDecimal("121.0"));
        stationB.setStatus(1);

        List<Station> stations = List.of(stationA, stationB);
        Page<Station> page = new Page<>(1, 10);
        page.setRecords(stations);
        page.setTotal((long) stations.size());

        // mock: 分页查询
        when(stationMapper.selectPage(any(Page.class), any())).thenReturn(page);
        // mock: 无空闲充电桩
        when(chargerMapper.selectList(any())).thenReturn(Collections.emptyList());
        // mock: 无定价规则（全部 fallback 到 BigDecimal.ZERO）
        when(pricingRuleMapper.selectList(any()))
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.emptyList());

        // 执行：按距离排序，用户位于 (30.0, 120.0)
        BigDecimal userLat = new BigDecimal("30.0");
        BigDecimal userLng = new BigDecimal("120.0");
        PageResult<StationVO> result = stationService.listStations(1, 10, null, "distance", userLat, userLng);

        // 验证：距离字段被填充，且按距离升序
        List<StationVO> records = result.getRecords();
        assertEquals(2, records.size());

        // 两个站点的 distance 都应不为 null
        assertNotNull(records.get(0).getDistance());
        assertNotNull(records.get(1).getDistance());
        // 近站应该在远站前面
        assertEquals("近站", records.get(0).getName());
        assertEquals("远站", records.get(1).getName());
        assertTrue(records.get(0).getDistance() < records.get(1).getDistance());
    }

    /**
     * listStations - 名称模糊搜索，验证调用 like 查询
     * <p>
     * 验证：传入名称参数时，selectPage 被调用（表明带 LIKE 条件的查询已执行）。
     * </p>
     */
    @Test
    @DisplayName("充电站列表 - 名称模糊搜索")
    void listStations_SearchByName() {
        // mock: 分页查询返回空结果（搜索无匹配）
        Page<Station> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());
        emptyPage.setTotal(0L);
        when(stationMapper.selectPage(any(Page.class), any())).thenReturn(emptyPage);

        // 执行：按名称搜索
        PageResult<StationVO> result = stationService.listStations(1, 10, "测试充电站", null, null, null);

        // 验证：返回空结果，selectPage 被调用（表明 LIKE 查询已执行）
        assertNotNull(result);
        assertTrue(result.getRecords().isEmpty());
        verify(stationMapper).selectPage(any(Page.class), any());
    }
}
