# 로컬 APK 검증 기록 · 0.3.0

검증일: 2026-11-21. 원작 전투 심화(상성·경직·버프·AI 전환) 및 UI/HUD 보완·전투 효과음(SFX) 추가판입니다.

## 산출물

- 파일: `../dist/legendary-wars-offline.apk`
- 버전: `0.3.0` / versionCode 3 · `com.legendary.offline`
- minSdk 26 / targetSdk 35
- 크기: 92897369 bytes
- SHA-256: `d8cb51bb5eee9beb70be868a372676e2cdc7f822ecf6ee469c212a74bda345e1`
- APK v2/v3 서명 검증 통과. 기존 0.1.0 / 0.2.0과 동일한 개인 로컬 인증서 (`b68bcdb3e08321ac851d131d82f4225eb3695374ee9a27e5dbc410c040eefb18`).

## 실제 실행한 검사

| 검사 | 결과 |
| --- | --- |
| Node 안전 파서/WAV 검증/캠페인/배치 | 21개 통과 |
| 이전 순수 Java 엔진/저장 회귀 | 26개 항목 통과 |
| 원작 전투·상성·경직·버프·AI·경제·SFX 시나리오 | 44개 항목 통과 |
| APK와 같은 GameData 로더 + 실제 원작 자료 | 28개 항목 통과 |
| 첫 튜토리얼 | 새 저장/기본 수치 + 상성/경직 적용 상태에서 승리 |
| 두 번째 채굴·소환 미션 | 원작 mining 이벤트, 보석 조건, 소환, finalSkully 승리 연결 확인 |
| 다섯 번째 엘프 구조 미션 | 원작 스크롤 125, 구역 충돌과 구조 목표 10을 적용해 107.2167초에 승리 |
| 이전 50개 전투 시뮬레이션 | 50/50 회귀 통과 |
| Android 빌드 | aapt2 → javac → D8 → jar → zipalign → apksigner 통과 |
| Manifest (REA/JADX) | 요청 권한 0, Launcher Activity 1 (`MainActivity`), 서비스/수신기/제공자 0 |
| 실행 코드 | DEX 클래스 정의 33개 모두 새 `com.legendary.offline` 네임스페이스. 원본 실행 코드/네이티브 라이브러리 0 |
| 금지 API | 네트워크/WebView/Telephony/reflection/dynamic loader/ProcessBuilder 타입 및 셸 실행 메서드/HTTP URL 없음 |
| 자료 출처 | 승인 자료 216개 모두 해시 일치: PNG 185, 검증 WAV 18, Ogg Vorbis 10, TTF 2, 생성 JSON 1 |

## v0.3.0 주요 변경 사항

1. **전투 심화 4종 (`reference/native-combat-findings.md` 근거)**:
   - **피해/방어 상성 (`0x4b297c`)**: 근접→원거리 `1.30x`, 원거리→마법 `1.40x`, 마법→근접 `1.15x`, 공성(`siege`)의 `armorType==3` 면역 관통, `float32` 곱셈 후 정수 절삭(`0x483d30`).
   - **피격 경직 및 하드 스턴**: 치명타·강타(최대 체력 25% 이상) 시 `0.18s` 경직(플린치), 원본 `stun`/`freeze`/`stone`/`web` 버프 스턴, `buffImmune`(`0x480e04`) 적용.
   - **버프·디버프 시스템**: DoT(화상·독), HoT(FaunAura), 스탯 스케일러(속도·공속·공격·방어·사거리), 공포(후퇴)·매혹(진영 반전 및 `charmImmune`), 흡혈(`lifeDrainPercent`), 스펠 버프(화염폭풍 화상·빙결폭풍 5초 빙결).
   - **원작 AI 상태 전환 (`0x4836e4`~`0x483ac8`, `0x4c9e10`)**: 피격 시 `guard`/`attack` → 임시 `hunt` 전환(`savedAi` 보존), 주변 180u 경계 아군 지원 호출(`callsForHelp`), 표적 상실 시 `savedAi` 복귀, `FormUp` 전선 집결 명령 구현.
2. **UI/HUD 결함 수정 및 SFX 추가**:
   - HUD 광부 아이콘 중복 렌더링 수정 (`WarView.java`)
   - 지역별 성(Castle) 아틀라스 우선 적용 및 기본 폴백
   - 적 성 체력바 표시 및 필살기 버튼 활성/비활성 시각 피드백 보완
   - 무기고 성벽(최대 6)·광부(최대 5) 강화 레벨/비용 표시 및 `Progress.supportCost` 공식 수정 (`250*(rank+1)`, `125*(rank+1)`)
   - 도움말 스펠/전투 설명 정합성 수정 및 연속 전투 지역 BGM 전환(`lastMusicTrack`) 보완
   - `WarView.java` 내 v0.1.0 미사용 데드 코드 제거
   - 원본 OBB `mono_*.wav` 18종 정적 무결성 검증(`validateWav`) 후 패키징 및 `LocalAudio` `SoundPool` 전투/UI 효과음 재생 추가

## 남은 범위와 실행 검증 한계

- 생존(Survival) 모드, 몬스터 캠페인, 후반 스테이지 미지원 컨트롤러 오브젝트, 20:9 와이드 전면 리디자인은 v0.3.0 범위 외입니다.
- 현 PC 가속 불가 환경 제약으로 Android 실기기·에뮬레이터 런타임 자동 검증은 수행하지 않았습니다.

## 재현 자료

- `asset-provenance.json`, `reference/native-combat-findings.md`, `reference/native-timing-evidence.txt`
- `../verification/0.3.0/apk-security-audit.json`, `../verification/0.3.0/final-apk-rea.json`
- `../verification/0.3.0/signature-verification.txt`, `../verification/0.3.0/packaged-permissions.txt`
- `test.ps1`, `tests/OriginalEngineTest.java`, `tests/RecoveredScenarioTest.java`
