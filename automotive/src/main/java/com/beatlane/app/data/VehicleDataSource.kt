package com.beatlane.app.data

import com.beatlane.app.judge.Powertrain
import com.beatlane.app.judge.VehicleSample
import kotlinx.coroutines.flow.Flow

/** 판정에 필요한 차량 데이터. */
interface VehicleDataSource {
    /** 차량 속도 (m/s). */
    val speedFlow: Flow<Float>

    /** 브레이크 페달 눌림 여부. */
    val brakePressedFlow: Flow<Boolean>

    val powertrain: Powertrain

    /**
     * 판정 엔진 입력용: 속도와 브레이크를 한 시각으로 묶은 샘플.
     * 두 흐름을 따로 합치면 샘플 타이밍이 어긋나 저크가 튀므로,
     * 구현체가 직접 시각을 붙여서 내보낸다.
     */
    val samples: Flow<VehicleSample>
}
