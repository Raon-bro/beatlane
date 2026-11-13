# BeatLane (비트레인)

> 브레이크 조작과 속도 변화로 동승자를 울컥이게 하는 감속을 잡아내고,
> 리듬게임처럼 **PERFECT / GREAT / GOOD / MISS**로 판정해 부드러운 감속을 익히게 하는 운전 습관 코칭 앱.

Android Automotive OS(Pleos Connect) 센터 디스플레이용 앱입니다.
타깃은 초보 운전자, 가족을 태우는 운전자, (전기차) 택시 운전자입니다.

- 현재 버전: **v1.1** (`versionName 1.1`, `versionCode 2`)
- 설치 파일(APK): 저장소의 [Releases](../../releases/latest)에서 내려받을 수 있습니다.

---

## 주요 기능

### 출발 전
- **동승자 선택**: 없음 · 아이 · 어르신 · 일반 → 판정 기준이 자동으로 엄격해짐
- **차종 모드**: 가솔린(브레이크 코칭) / 전기·하이브리드(회생제동 코칭)
- **효과음** 켜기·끄기
- **데이터 소스**: 차량 데이터 / 시뮬레이션 선택과 속도·브레이크 수신 상태 표시
- 직전 주행 점수와 개선 포인트 (앱을 다시 켜도 유지)

### 판정 화면
- 속도 게이지 · 원근 차선 · 승차감 점수 · 콤보
- **롱노트**: 브레이크를 밟으면 판정선에 노트가 생기고, 밟는 동안 노트가 판정선을 지나 흘러감
- **HOLD 게이지**: START → HOLD → RELEASE(정지 직전 얼마나 풀었는지)
- **감속 따라 그리기**: 이상적인 감속 곡선(점선) 위에 실제 감속(실선)을 실시간으로 그림
- 판정 순간 빛 터짐 · 판정 글자 애니메이션 · 콤보 튐 · 10콤보 강조 · 등급별 효과음
- 급감속(감속도 3.0 m/s² 초과가 0.5초 이상) 경고
- 주행 중 화면은 2초 안에 읽을 수 있게: 큰 글자, 적은 요소, 터치 영역 76dp 이상

### 확장 단계 미리보기: 앞차 감속 예고 (v1.1, 시뮬레이션 전용)
- 시뮬레이션 시나리오 버튼을 누르면 **앞차**가 나타나 감속하고, 감속 노트가 판정선 쪽으로 **미리 다가옵니다**.
- 머리 문구로 **"브레이크 밟기 n초 전"**(회생제동이면 "발 떼기 n초 전")을 보여 줍니다 (6초 전부터).
- 화면 위에 **"앞차 있음"**, **"확장 단계 미리보기"** 칩이 함께 떠서, 실제 센서가 아닌 시연용 기능임을 밝힙니다.
- 예고 노트의 시작선·끝선과 앞차 위치는 센서가 아니라 **재생 중인 시나리오에서 계산**합니다(`data/sim/LeadCar.kt`).
- **실제 차량 데이터 모드에서는 예고가 없습니다.** 판정 엔진(`judge/`)은 이 기능과 무관하게 같은 방식으로 판정합니다.

### 주행 후 리포트
- 승차감 점수(★5)와 지난 주행 대비 변화
- 울렁임 기록: 시간에 따른 속도 그래프 위에 판정 구간과 MISS·급감속 지점 표시
- PERFECT 비율 · 급감속 횟수 · 최고 콤보 · 판정 분포 · 최근 5회 승차감 · 코칭 한마디

---

## 판정 방식

입력은 **차량 속도**와 **브레이크 페달 상태**뿐입니다 (카메라·레이더·ADAS 미사용).
속도를 5샘플 이동평균으로 보정한 뒤 가속도 *a*(m/s²)와 저크 *j*(m/s³)를 계산합니다.

| 노트 | 구간 | 측정값 | 의미 |
|---|---|---|---|
| 브레이크 (모든 차종) | START | 밟기 시작 1초 동안의 최대 \|j\| | 살살 밟았는지 |
| | HOLD | 유지 구간 감속도의 표준편차 | 일정하게 유지했는지 |
| | END | 정지 직전 0.5초 평균 \|a\| | 멈추기 직전 살짝 풀었는지 |
| 회생 (전기·하이브리드) | LIFT | 회생 시작 1초 동안의 최대 \|j\| | 가속 페달에서 천천히 발을 뗐는지 |

| 등급 | 저크 (START/LIFT) | 감속도 편차 (HOLD) | 정지 직전 감속 (END) |
|---|---|---|---|
| PERFECT | ≤ 1.5 | ≤ 0.4 | ≤ 0.8 |
| GREAT | ≤ 2.5 | ≤ 0.7 | ≤ 1.3 |
| GOOD | ≤ 4.0 | ≤ 1.0 | ≤ 2.0 |
| MISS | 그 이상 | 그 이상 | 그 이상 |

- 노트 등급은 구간 중 가장 낮은 등급입니다.
- 동승자 보정 계수를 임계값에 곱합니다: 없음 1.0 · 일반 0.9 · 아이 0.75 · 어르신 0.6
- 승차감 점수는 최근 10개 노트의 가중 평균입니다(최근일수록 가중치 큼, P5·G4·G3·M1).
- 모든 임계값은 실차 보정 전 임시값이며 `judge/JudgeConfig.kt` 한 곳에서 바꿀 수 있습니다.

---

## 설치와 실행

### 요구 사항
- Pleos Connect Emulator: AVD `Pleos_Connect_v2.1.0` (API 34, 2560×1440)
  - 가상 차량 데이터(VHAL)가 동작하려면 **Cold boot**로 켜야 합니다.
- 소스에서 빌드하려면 Android Studio (JDK 17 이상)

### APK로 바로 설치
[Releases](../../releases/latest)에서 `DRIVEIN_BeatLane_release.apk`를 내려받아 설치합니다.
```bash
adb install -r DRIVEIN_BeatLane_release.apk
```

### 소스에서 빌드
```bash
# 단위 테스트
./gradlew :automotive:testDebugUnitTest

# 시연용 릴리스 빌드 (디버그 키로 서명됨)
./gradlew :automotive:assembleRelease
adb install -r automotive/build/outputs/apk/release/automotive-release.apk
```

- 앱 ID: `com.beatlane.app`
- 처음 실행 시 차량 속도 권한을 요청합니다. Pleos 에뮬레이터에서는 **"위치 정보" 권한**으로 표시됩니다 → 허용.

### 시연 (시뮬레이션)
차량 데이터 없이도 시연이 끝까지 돌아갑니다. 판정 화면 아래 **SIMULATION** 패널의 버튼을 누르세요.
각 시나리오는 50km/h까지 가속 → 4초 정속(이때 앞차와 예고 노트가 다가옴) → 감속 순서로 진행됩니다.

| 버튼 | 예상 결과 |
|---|---|
| 부드럽게 정지 | PERFECT |
| 급정지 | MISS + 급감속 1회 |
| 회생 천천히 (EV 모드) | PERFECT |
| 회생 급하게 (EV 모드) | MISS |

- 주행 중 시뮬레이션 버튼을 누르면 데이터 소스가 시뮬레이션으로 바뀝니다. 출발 전 화면으로 돌아가면 (차량 데이터를 쓸 수 있을 때) 다시 차량 데이터로 돌아옵니다.
- 화면 왼쪽 아래 **BEATLANE** 글자를 누르면 시뮬레이션 패널을 숨기거나 다시 보일 수 있습니다.
- 뒤로 가기: 판정 화면 → 리포트, 리포트 → 출발 전 화면.

### 실제 차량 데이터
출발 전 화면에서 **차량 데이터**를 고르고 주행을 시작하면, Pleos Virtual Console 등에서 들어오는 속도·브레이크 값으로 판정합니다.
들어오는 값과 판정 결과는 로그로 확인할 수 있습니다.
```bash
adb logcat -s BeatLane BeatLane/Vehicle
# input brake=ON/OFF, input speed=xx.x km/h, note start ..., judged ...
```
> 참고: Pleos 에뮬레이터는 `user` 빌드라 `cmd car_service`로 값을 주입할 수 없습니다. 실제 입력 판정은 아직 Virtual Console로 검증하지 못했고, 시연은 시뮬레이션 기준입니다.

---

## 구조

```
automotive/src/main/java/com/beatlane/app/
├── judge/        판정 엔진 (순수 Kotlin, Android 의존성 없음)
│   ├── JudgeEngine.kt       노트 판정 · 콤보 · 승차감 · 급감속 · 리포트
│   ├── JudgeConfig.kt       판정 임계값 (한 곳에서 조정)
│   ├── KinematicsFilter.kt  속도 보정 → 가속도 · 저크
│   ├── GuideCurve.kt        "따라 그리기" 이상적인 감속 곡선
│   └── Coach.kt             코칭 문구
├── data/
│   ├── VehicleDataSource.kt          속도 · 브레이크 · 판정용 샘플 흐름
│   ├── PleosVehicleDataSource.kt     실제 차량 (Pleos 브레이크 + AAOS 속도, 입력 로그)
│   ├── SimulatedVehicleDataSource.kt 시연용 가짜 데이터 + 앞차 예고
│   ├── sim/Scenario.kt               시나리오 4종
│   ├── sim/LeadCar.kt                앞차 위치 · 예고 노트 계산 (LeadCarPlan, SimForecast)
│   ├── SampleAssembler.kt            속도·브레이크를 한 샘플로 묶기
│   └── DriveHistoryStore.kt, AppSettings.kt  기기 안 저장 (기록 · 설정)
├── sound/        판정 효과음 (코드 합성 + 에셋 교체)
└── ui/           Compose 화면 (predrive · drive · report · theme)
```

- **차량 데이터**
  - 브레이크 페달: Pleos Vehicle SDK 2.3.2 `Brake` (`checkBrakePedalStatusCapabilities` → `registerBrakePedalStatus`), 권한 `pleos.car.permission.CAR_DRIVE`
  - 차량 속도: AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED`, 권한 `android.car.permission.CAR_SPEED`
  - 둘 다 지원 여부를 먼저 확인하고, 하나라도 안 되면 시뮬레이션으로 동작합니다.
- **단위 테스트 41개**: 판정 엔진(4가지 시나리오, 동승자 보정, 노트 경계, 리포트), 가이드 곡선, 앞차 예고 배치, 샘플 묶기, 시뮬레이션 재생, 기록 저장, 효과음 합성.

---

## 교체 가능한 에셋

내 차(`car_rear.png`) · 앞차(`lead_car.png`) 이미지와 판정 효과음(`res/raw/sfx_*`)은 파일만 넣으면 바뀝니다.
자세한 규격은 [`docs/assets.md`](docs/assets.md)를 참고하세요.

---

## 범위 밖 (확장 단계에서 다룰 기능)
카메라 · 전방 레이더 · ADAS · 신호등 인식 기반의 실제 예고, 내비 경로 기반 미리보기, 위치 기반 울렁임 지도는 포함하지 않았습니다.
앞차 감속 예고는 위에 적은 대로 **시뮬레이션 전용 미리보기**로만 제공합니다.

## 개인정보 · 네트워크
- 주행 기록과 설정은 기기 안(SharedPreferences)에만 저장하며, 점수와 코칭 문구 외의 정보는 담지 않습니다.
- 외부 네트워크 호출 · 분석 SDK · 광고 SDK를 사용하지 않습니다.

## 라이선스 고지
- 폰트 Outfit: SIL Open Font License 1.1 ([`docs/licenses/Outfit-OFL.txt`](docs/licenses/Outfit-OFL.txt))
- Pleos Connect SDK: Pleos Playground 이용 약관을 따릅니다.
- 차량 이미지(`car_rear.png`, `lead_car.png`)는 팀이 사용 권한을 확인한 이미지입니다.
