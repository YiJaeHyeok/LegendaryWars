# Legendary Wars Offline v0.3.0 — 전투 심화 및 UI/SFX 보완 설계 스펙

- 작성일: 2026-11-21
- 기준 버전: `v0.2.0` (`com.legendary.offline`, `versionCode 2`) -> 목표 버전: `v0.3.0` (`versionCode 3`)
- 근거 문서: `docs/intent/2026-11-21-legendary-wars-combat-depth.md`, `legendary-wars-reborn/reference/native-combat-findings.md`

## 1. 아키텍처 원칙 및 보안 제약

1. **원본 미실행 원칙**: 원본 XAPK/APK/OBB/`libverde.static-only.so`는 절대 실행/로드하지 않고 정적 바이트 분석(Capstone ARM 디코딩 및 안전 plist 파서)만 사용한다.
2. **클린룸 유지**: 요청 권한 0개, 광고/추적/네트워크/WebView/동적 코드 로딩/원본 실행코드 0%.
3. **데이터/코드 분리**: `OriginalBattle`은 Android API 의존성 없는 순수 Java 시뮬레이션으로 유지하고, SFX 재생은 `sfxEvents` 큐를 통해 `WarView` -> `MainActivity` -> `LocalAudio(SoundPool)`로 전달한다.
4. **하위 호환성**: 패키지명(`com.legendary.offline`), 기존 로컬 개발 서명키(`.local/legendary-development.keystore`), `Progress` 저장 포맷(`v1`/`v2`) 완전 호환.

## 2. 전투 심화 메커니즘 4종

- **피해/방어 상성 (`0x4b297c`)**: `MELEE(0)→RANGED(1) 1.30x`, `RANGED(1)→MAGIC(2) 1.40x`, `MAGIC(2)→MELEE(0) 1.15x`, `SIEGE(3)` 면역 관통, `float32` 곱셈 후 정수 절삭(`0x483d30`).
- **피격 경직 및 하드 스턴**: 치명타/강타(25% HP 이상) 시 `0.18s` 플린치 + 원본 `stun`/`freeze`/`stone`/`web` 버프 스턴 (`buffImmune` `0x480e04` 존중).
- **버프·디버프 시스템**: DoT(화상·독), HoT(FaunAura), 스탯 스케일러(속도·공속·공격·방어·사거리), 공포(후퇴)·매혹(진영 반전 및 `charmImmune`), 흡혈(`lifeDrainPercent`), 스펠 버프(화염폭풍 화상·빙결폭풍 5초 빙결).
- **원작 AI 상태 전환 (`0x4836e4`~`0x483ac8`, `0x4c9e10`)**: 피격 시 `guard`/`attack` → 임시 `hunt`(`savedAi` 보존), 주변 180u 경계 아군 지원 호출(`callsForHelp`), 표적 상실 시 `savedAi` 복귀, `FormUp` 전선 집결.

## 3. UI/HUD 결함 수정 및 전투 효과음(SFX)

1. HUD 광부 아이콘 중복 수정 (`WarView.java`)
2. 지역별 성(Castle) 아틀라스 우선 적용 및 기본 폴백
3. 적 성 체력바 표시 및 필살기 버튼 활성/비활성 시각 피드백
4. 무기고 성벽(최대 6)·광부(최대 5) 강화 레벨/비용 표시 및 `Progress.supportCost` 공식 수정 (`250*(rank+1)`, `125*(rank+1)`)
5. 도움말 스펠/전투 설명 정합성 수정 및 연속 전투 지역 BGM 전환(`lastMusicTrack`) 보완
6. `WarView.java` 내 v0.1.0 미사용 데드 코드 제거
7. 원본 OBB `mono_*.wav` 18종 정적 무결성 검증(`validateWav`) 후 패키징 및 `LocalAudio` `SoundPool` 전투/UI 효과음 재생 추가
