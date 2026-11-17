# 원작 전투 정적 분석 근거

원본 APK SHA-256: `062e16b02595cc577527cd0f15c0a9f4d6cb36b6575bedf7cd226e5e0ab28b48`.

APK 안 `lib/armeabi/libverde.so` SHA-256: `846914306a231072372c7376ee3d82bfbb17c21fa4c56693339cdc06ee3da0da`.

ARM ELF의 섹션·Objective-C 클래스/메서드 테이블을 바이트로 읽고 Capstone으로 명령을 디코딩했습니다. 라이브러리를 import/load/link/run하지 않았습니다. 이 저장소에는 `native-timing-evidence.txt`의 제한된 명령 근거를 포함합니다. 원본 바이너리와 대규모 중간 분석 산출물은 포함하지 않습니다.

| 항목 | 정적 근거 | 새 코드 적용 |
| --- | --- | --- |
| 기본 애니메이션 FPS | 파서 `0x4e950c`/`0x4e9510`은 누락 시 `0x41700000`(15.0f)을 구성. `0x4e9564` 이후 프레임 수/FPS를 duration에 저장 | 명시 FPS 우선, 누락 시 15 |
| 양수 보정 | `0x37f920`~`0x37f930`: 1을 더해 base와 float 곱셈 | `base * (1 + modifier)` |
| 음수 보정 | `0x37f904`~`0x37f918`: 1 - modifier를 분모로 float 나눗셈 | `base / (1 - modifier)` |
| 속도 입력 | WaveCustomizer scaleSpeed offset28 → `0x4f6fec`~`0x4f7024` → helper `0x480a20`, unit offset `0x94`에 가산. 이동 경로 `0x4c8d2c`~`0x4c8d30`에서 보정 helper 사용 | 원본 0.1은 10% 증가 |
| haste | WaveCustomizer offset16 → `0x480a4c`, unit offset `0x98`에 가산. `0x488f38`~`0x488fac`에서 dt 보정 | 애니메이션 시계와 주기에 동일 보정 |
| 공격 입력 | WaveCustomizer offset32 → `0x480ad8`, unit offset `0xa0`에 가산 | 일반/특수 공격 양쪽 보정 |
| HP 보정 | `0x480808`~`0x48084c`: 기존 HP 배율과 입력의 float 곱셈, HP 재계산 | HP 배율은 곱셈 |
| 방어 | `0x483bd0`~`0x483ca8`: 상태0이면100-defense, 다른 상태는100-(defense>>1). 남은 비율25 이상, 최종 roundf | ready 상태 전체 방어, 이동/공격 절반 방어, 최소25% 피해 |
| 공격 난수 | `0x483f6c` 이후 기본값의0.75 + arc4random 비율*0.25를 roundf; `0x484094` 치명타 두 배 | 같은 범위/배수, PRNG는 새 Java 구현 |

`Units.plist` 기본 수치와 등급 애니메이션의 FPS·프레임 수·hit/fire 이벤트·투사체 속도를 가져왔습니다. Knighty는 HP100, 공격20, 방어1, 속도25, 소환 비용10/쿨다운5초입니다. 첫 튜토리얼의 scaleAttack1.2 / scaleSpeed0.1 적용 시 공격44, 속도27.5가 됩니다.

전투 엔진 전체를 복구했다는 뜻은 아닙니다. 피해 타입/방어 타입 후속 보정(`0x4b297c`), 모든 상태·버프·AI 전환·특수 공격 처리, 이벤트 프레임의 네이티브 스케줄링 전체를 확인하지 못했습니다. 타격 시간 배치, 레인 이동/충돌 허용 폭, 돌진 접촉 처리 일부는 새 엔진의 해석입니다. 모든 극단값/정수 폭도 동일하다고 주장하지 않습니다.

재현용 디코더: `tools/native-combat.mjs`, `tools/disassemble-combat.py`, `tools/static-timing-review.py`. 공식 출처: [Python](https://www.python.org/downloads/release/python-31316/), [Capstone 문서](https://www.capstone-engine.org/documentation.html). Capstone wheel은 PyPI의 SHA-256과 대조했고 Python의 PSF Authenticode 서명을 확인했습니다. 분석 도구는 새 APK에 포함하지 않습니다.
