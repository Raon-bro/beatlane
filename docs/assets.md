# 교체 가능한 에셋 (차량 이미지 · 효과음)

파일을 아래 위치에 넣고 다시 빌드하면 코드 수정 없이 바로 적용됩니다. 없으면 코드로 만든 기본값을 씁니다.

## 1. 내 차 이미지 (판정 화면)

| 파일 | 필수 | 설명 |
|---|---|---|
| `automotive/src/main/res/drawable-nodpi/car_rear.png` | 선택 | 뒤에서 본 차. 없으면 코드로 그린 흰색 SUV |
| `automotive/src/main/res/drawable-nodpi/car_rear_brake.png` | 선택 | 브레이크등이 켜진 같은 그림. 없으면 램프 위치에 붉은 빛을 덧그림 |

- **배경 투명 PNG**, 가로 **800px 이상** 권장 (2560×1440 센터 디스플레이에서 약 300px로 표시).
- 차의 **바닥 가운데**가 이미지 아래 가운데에 오도록 여백 없이 잘라 주세요. 그림자는 코드가 그립니다.
- 두 파일(`car_rear`, `car_rear_brake`)은 크기와 구도가 같아야 합니다.
- `car_rear_brake.png`가 없을 때 빛을 덧그리는 위치는
  `automotive/src/main/java/com/beatlane/app/ui/drive/CarSprite.kt`의 `CarAssetSpec.lampY`(이미지 높이 대비 0~1),
  `lampWidth`(이미지 폭 대비)로 맞춥니다.
- 파일 이름은 소문자·숫자·밑줄만 가능합니다 (안드로이드 리소스 규칙).
- 저작권: 팀이 직접 만든 렌더나 사용 허가가 있는 이미지만 넣어 주세요.

## 2. 판정 효과음

| 파일 | 등급 |
|---|---|
| `automotive/src/main/res/raw/sfx_perfect.ogg` | PERFECT |
| `automotive/src/main/res/raw/sfx_great.ogg` | GREAT |
| `automotive/src/main/res/raw/sfx_good.ogg` | GOOD |
| `automotive/src/main/res/raw/sfx_miss.ogg` | MISS |

- 형식은 ogg 또는 wav. 확장자는 자유지만 **이름(sfx_perfect 등)은 정확히** 맞춰야 합니다.
- 운전 중 소리이므로 **0.3초 이내**, 놀라지 않을 정도의 부드러운 소리를 권장합니다.
- 일부만 넣어도 됩니다. 없는 등급은 코드로 합성한 기본음(`sound/ToneSynth.kt`)을 씁니다.
- 효과음은 출발 전 화면의 SOUND 카드나 판정 화면 오른쪽 아래 스피커 버튼으로 끌 수 있고, 설정은 저장됩니다.
