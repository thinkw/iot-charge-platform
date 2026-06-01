package com.iot.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 充电站详情 VO
 * <p>
 * 包含充电站基本信息及其下所有充电桩列表。
 * </p>
 *
 * @author IoT Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationDetailVO {

    /** 充电站基本信息 */
    private StationVO station;

    /** 充电桩列表 */
    private List<ChargerVO> chargers;
}
