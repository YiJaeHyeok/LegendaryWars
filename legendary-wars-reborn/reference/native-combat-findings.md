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
| 피해/방어 상성 | `0x4b297c` 점프 테이블(`0x4b29a0`) 및 `takeDamage` 호출부 `0x483cf4`~`0x483d48`: `melee(0)→ranged(1)` `1.30f`(`0x3fa66666`), `ranged(1)→magic(2)` `1.40f`(`0x3fb33333`), `magic(2)→melee(0)` `1.15f`(`0x3f933333`), `siege(3)`는 `armorType==3` 면역 관통(`1.0f`), 비-siege vs `armorType==3`은 `0.0f`. 최종 `__fixunssfsi` 정수 절삭 | `UnitSpec.typeModifier`로 방어 감쇄 직후 적용 |
| 버프 면역·적용 | `ApplyBuffHitHandler` `0x48d054` → `0x480e04`(`ldrb r0, [r0, #0xf2]` `buffImmune` 체크), `DrainHealthHitHandler` `0x524fd0`, `FearBuff` `0x4296ac`, `CharmBuff` `0x524ce4`, `BlizzardBuff` `0x533eec`, `FaunAura` `0x4640f0` | `Units.plist`/`*Animations_*.plist`의 DoT/HoT/스탯 스케일러/스턴/공포/매혹/흡혈 및 `buffImmune`/`charmImmune` 반영 |
| AI 피격·협공·복귀 | `takeDamage` `0x4836e4`(`callsForHelp` → `wakesUpToHelpFriends` `0x483740`), `0x4837dc`(`wakesUpGuardingFriends`), `0x483970`(`switchesToHuntWhenHurtForUnit:`, `GuardAI` `0x4931c0`=1, `AdvanceAI` `0x4c9554`=1) → 임시 `HuntAI` 생성 후 `setNextAIObject:`(`0x483a64`); `HuntAI::currentTargetLost` `0x4c9e10`에서 원 AI 복귀 | `OriginalBattle`의 `savedAi` 스택 전환, 주변 경계 아군 지원 호출, 표적 상실 시 원복, `FormUp` 집결 반영 |

`Units.plist` 기본 수치와 등급 애니메이션의 FPS·프레임 수·hit/fire 이벤트·투사체 속도·버프/스턴 정의를 가져왔습니다. Knighty는 HP100, 공격20, 방어1, 속도25, 소환 비용10/쿨다운5초입니다. 첫 튜토리얼의 scaleAttack1.2 / scaleSpeed0.1 적용 시 공격44, 속도27.5가 됩니다.

v0.3.0에서는 v0.2.0에서 미확인이었던 피해/방어 상성(`0x4b297c`), 버프/디버프(`BuffBase`/`ApplyBuffHitHandler`/`DrainHealthHitHandler`), AI 피격·협공·복귀 전환(`0x4836e4`~`0x483ac8`, `0x4c9e10`)을 정적 근거로 보강했습니다. 다만 전체 73개 스테이지의 후반 특수 컨트롤러/미니게임 오브젝트와 모든 네이티브 이벤트 프레임 스케줄링까지 100% 동일하다고 주장하지는 않습니다.

재현용 디코더: `tools/native-combat.mjs`, `tools/disassemble-combat.py`, `tools/static-timing-review.py`. 공식 출처: [Python](https://www.python.org/downloads/release/python-31316/), [Capstone 문서](https://www.capstone-engine.org/documentation.html). Capstone wheel은 PyPI의 SHA-256과 대조했고 Python의 PSF Authenticode 서명을 확인했습니다. 분석 도구는 새 APK에 포함하지 않습니다.
