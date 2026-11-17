# 로컬 APK 검증 기록 · 0.2.0

검증일: 2026-11-16, Asia/Kuala_Lumpur. 원작 전투 우선 복원판이며 전체 원작과의 동작 동등성 검증은 아닙니다.

## 산출물

- 파일: `../dist/legendary-wars-offline.apk`
- 버전: `0.2.0` / versionCode 2 · `com.legendary.offline`
- minSdk 26 / targetSdk 35
- 크기: 93891416 bytes
- SHA-256: `a4285b47ad6d1290abcc9c6e7e2c766eab001231fe90673c8dc566442f5b4744`
- APK v2/v3 서명 검증 통과. 기존 0.1.0과 같은 개인 로컬 인증서.
- 0.1.0 백업: `../dist/legendary-wars-offline-v0.1.0.apk`, SHA-256 `51542cf76759fd815d399488a92ca15e5f840ddc68acfbcf49dbc2925183a71c`.

## 실제 실행한 검사

| 검사 | 결과 |
| --- | --- |
| Node 안전 파서/캠페인/배치 | 20개 통과 |
| 이전 순수 Java 엔진/저장 회귀 | 26개 항목 통과 |
| 새 시나리오·경제·조작·저장 이관 | 22개 항목 통과 |
| APK와 같은 GameData 로더 + 실제 원작 자료 | 23개 항목 통과 |
| 원작 기본 체력/공격/방어/속도 | 원본에 명시된 235개 숫자와 생성 데이터 일치. 0인 NPC 체력도 보존 |
| 첫 튜토리얼 | 새 저장/기본 수치로 승리. 합법적 조작 API로 레인 회피/타깃/필살기 사용. HP/게임 상태를 직접 수정하지 않음 |
| 두 번째 채굴·소환 미션 | 원작의 mining 이벤트, 보석 조건, 소환, finalSkully 승리 연결 확인 |
| 다섯 번째 엘프 구조 미션 | 원작 스크롤125, 구역 충돌과 구조 목표10을 적용해 107.2167초에 승리 |
| 반복 목표/광부 구조 | 서로 다른 적 6회 사망 → 카운터6; 광부는 채굴 구매 대신 1회 구조 구역으로 처리 |
| 스크롤 함정 표시 | 원작 스크롤용 아틀라스에서 바퀴 이미지가 검색되는지 실제 렌더러의 데이터 경로로 확인 |
| 이전 50개 전투 시뮬레이션 | 별도 구형 재구성 엔진의 50/50 회귀 통과. 새 원작 73개 미션의 완주 검증으로 간주하지 않음 |
| Android 빌드 | aapt2 → javac → D8 → jar → zipalign → apksigner 통과 |
| Manifest | REA/JADX 재확인: 요청 권한0, Launcher Activity1, 서비스/수신기/제공자0 |
| 실행 코드 | DEX 클래스 정의 32개 모두 새 com.legendary.offline 네임스페이스. 원본 실행 코드/네이티브 라이브러리0 |
| 금지 API | 네트워크/WebView/Telephony/reflection/dynamic loader/ProcessBuilder 타입 및 셸 실행 메서드/HTTP URL 없음 |
| 자료 출처 | 승인 자료200개 모두 해시 일치: PNG185, WAV2, Ogg Vorbis10, TTF2, 생성 JSON1 |

REA의 class_count는 최상위 클래스 집계이고, DEX 감사의 클래스 정의 개수는 내부/합성 클래스를 포함하므로 다를 수 있습니다. REA는 서명 검사를 수행하지 않으며 위 서명 결과는 Android SDK apksigner의 별도 결과입니다.

## 코드/자료 검토와 수정

`UnitSpec`/`OriginalBattle`/`BattlePlan`: 원본 기본값, 15fps, 원본 비용/쿨다운, 양·음수 보정, 일반·특수 공격 보정, 비율 방어, 피해 난수, 타격/발사 이벤트, 투사체 시간, 필살기 이동 보정. AI는 새 guard/attack/hunt 구현입니다. 상한160 유닛, 제한된 시간 간격, 금지된 원본 표현식의 평가 없음.

`GameData`/캠페인 컴파일러: 자료 크기 제한, typed 명령/조건/구역, 명시된 수치만 파싱. 반복 사망 목표는 매 이벤트마다 한 번, 구조 구역은 소비 후 재사용 불가. 모든 조건 문자열을 eval/스크립트로 실행하지 않습니다. 미지원 동작은 coverage에 남깁니다.

`Progress`: v1의 50개 별/7개 강화 저장을 v2의 73개/21개 슬롯으로 이관하고 범위를 검증합니다. 강화 경제 전체가 원작과 같다는 주장은 하지 않습니다.

`WarView`/`Artwork`: 원본 배치 자료·폰트·이미지, 직접 선택/타깃/이동, 스크롤과 조작 좌표, 모달 입력, 별도 아이콘 캐시와 제한된 아틀라스 캐시. 스크롤 함정의 잘못된 아틀라스 조회를 수정했습니다. 메뉴 공통 배치 자료를 브라우저에서 시각 확인했으나 Android 실행 캡처는 아닙니다.

`LocalAudio`/`MainActivity`: 번들 음악 파일 디스크립터, 앱 전용 저장소, pause/destroy 정리. 원격 URL, 계정, 마이크 입력 없음.

자원 파서는 원본 APK/OBB의 정확한 SHA-256, ZIP CRC, PNG 청크 CRC/범위, plist 외부 엔티티 금지, WAV 청크 경계, Ogg 페이지 CRC/Vorbis 식별자, TTF 테이블 경계를 검사합니다. .mp3라는 원본 파일명이 실제로는 Ogg인 것을 확인해 확장자만 .ogg로 바꾸고 바이트는 보존했습니다. 원본의 잘못된 프레임/비표준 이름18개는 제외했습니다. 상세 목록은 asset-provenance.json에 있습니다.

별도 검토에서 확인한 mining 이벤트 불일치, 과거 입력 재소비, 명시 타깃 덮어쓰기, 대화 버튼 누락, 반복 카운터/구조/필살기 속도/함정 표시 문제를 수정하고 회귀 검사했습니다. 빌드 감사는 한때 D8의 다차원 배열 생성용 reflect.Array 참조도 거부했고, 검사 기준을 완화하지 않고 배열 행을 개별 초기화하도록 수정했습니다.

## 남은 범위와 실행 검증 한계

원본 APK/ARM 라이브러리는 실행하거나 설치하지 않았습니다. 정적 자료73개를 가져왔지만 모든 미션/모드의 플레이를 완성하거나 검증한 것은 아닙니다. 특히 후반 컨트롤러 오브젝트와 별도 조작 모드, 탑 수비병 생성, 모든 버프/스킬/피격 경직/방어 타입 상성과 원본 난수·정수 폭은 남아 있습니다. 일부 후반 미션은 진행이 막힐 수 있습니다. 원본 AI 전체와 동일하다고 주장하지 않습니다.

새 0.2.0 APK도 실기기에서 실행하지 못했습니다. 이 PC에서 에뮬레이터는 가속을 사용할 수 없고, 소프트웨어 부팅은 멈추거나 호스트 예외0xC0000005로 종료됐습니다. 드라이버/Windows 기능은 변경하지 않았습니다. 실제 Android 화면·터치·음악·성능과 모든 단말 호환성은 미검증입니다. 순수 Java 승리를 기기 플레이 검증으로 표시하지 않습니다.

검사 범위 내 원본 실행 코드와 의도하지 않은 통신/코드 로딩 기능이 포함되지 않았음을 확인했습니다. 다중 백신·동적 통신 검사나 무감염 인증, 원본 다운로드 전체/기기의 감염 판정은 수행하지 않았습니다.

## 재현 자료

- `asset-provenance.json`, `reference/campaign-recovery.json`, `reference/layout-recovery.json`
- `reference/native-combat-findings.md`, `reference/native-timing-evidence.txt`
- `../verification/0.2.0/apk-security-audit.json`, `../verification/0.2.0/final-apk-rea.json`
- `../verification/0.2.0/signature-verification.txt`, `../verification/0.2.0/packaged-permissions.txt`
- `test.ps1`, `tests/OriginalEngineTest.java`, `tests/RecoveredScenarioTest.java`

Context7에서 Android Canvas/View와 JSON 파싱 문서를 확인했습니다. [Android Canvas](https://developer.android.com/reference/android/graphics/Canvas), [JSON-java의 Android 호환 범위](https://github.com/stleary/JSON-java/wiki/JSON-Java-for-Android-developers). Context7은 문서 도구이며 악성코드 검사 엔진이 아닙니다. JSON-java 라이브러리는 JVM 테스트 전용이고 APK는 Android 내장 org.json을 사용합니다.