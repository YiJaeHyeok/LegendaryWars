# Legendary Wars Offline

원작 이미지·음악·효과음과 새 Java 코드로 만든 Android 오프라인 복원 프로젝트입니다. 현재 버전은 **0.3.0 전투 심화 및 UI/SFX 보완판**입니다.

**[APK 다운로드](https://github.com/YiJaeHyeok/LegendaryWars/releases/download/v0.3.0/legendary-wars-offline.apk)** · [릴리스/체크섬](https://github.com/YiJaeHyeok/LegendaryWars/releases/tag/v0.3.0) · [검증 기록](legendary-wars-reborn/VERIFICATION.ko.md)

## 설치

Android 8.0 이상에서 APK를 내려받아 설치합니다. 별도 OBB, 로그인, 인터넷 연결이 필요하지 않습니다. 기존 개인 복원판 0.1.0 / 0.2.0은 같은 서명키를 사용하는 이 릴리스로 업데이트할 수 있습니다.

APK SHA-256:

```text
d8cb51bb5eee9beb70be868a372676e2cdc7f822ecf6ee469c212a74bda345e1
```

## v0.3.0 포함한 작업

- **전투 심화 4종**: 원작 피해/방어 상성(`0x4b297c`), 피격 경직·하드 스턴, 버프/디버프(화상·독·빙결·공포·매혹·오라·흡혈), 원작 AI 상태 전환(피격 시 Hunt 전환·주변 아군 지원 호출·원복·FormUp 집결).
- **UI/HUD 보완 및 전투 효과음(SFX)**: 광부 아이콘 중복 수정, 지역별 성 아틀라스 적용, 적 성 체력바·필살기 버튼 활성 피드백, 무기고 성벽·광부 레벨/비용 표시 및 공식 수정, 도움말 정합성·연속 전투 지역 BGM 전환, 데드 코드 정리, 정적 검증된 원작 `mono_*.wav` 18종 SFX 재생.
- 첫 튜토리얼, 채굴 미션, 엘프 구조 미션 및 전투 심화 단위/시나리오 테스트 전 항목 통과.

**원작 전체와 완전히 같은 완성판은 아닙니다.** 생존 모드, 몬스터 캠페인, 후반 특수 컨트롤러/미니게임 오브젝트와 Android 실기기 런타임 검증은 범위 외입니다.

## 소스와 빌드

Android 프로젝트는 [legendary-wars-reborn/](legendary-wars-reborn)에 있습니다. 준비된 리소스를 포함하므로 앱 빌드에 원본 APK는 필요하지 않습니다. Android Studio에서 이 폴더를 열고 JDK 17 이상/Android SDK 35로 Gradle 동기화 후 Build APK(s)를 사용할 수 있습니다. AGP는 8.7.3입니다.

## 보안과 출처

원본 앱은 실행·설치하지 않았고 새 APK에 원본 DEX/SO/JAR를 넣지 않았습니다. 새 APK는 요청 권한 0, 네트워크/WebView/광고 SDK 제외를 정적 검사했습니다. [APK 검사 결과](verification/0.3.0/apk-security-audit.json)와 [자료 해시](legendary-wars-reborn/asset-provenance.json)를 포함합니다. 무감염 인증은 아닙니다.

원본 APK/XAPK/OBB, 분석용 실행 파일, 개인 서명키, 개발 도구와 로컬 설정은 이 저장소에 올리지 않았습니다. 원작 명칭·그림·음악의 저작자는 원 제작자이며 이 프로젝트는 공식 배포본이 아닙니다. 원작 자료에 새로운 오픈소스 라이선스를 부여하지 않습니다.
