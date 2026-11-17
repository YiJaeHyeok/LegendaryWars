# Legendary Wars Offline

원작 이미지·음악과 새 Java 코드로 만든 Android 복원 프로젝트입니다. 현재 버전은 **0.2.0 전투 우선 복원판**입니다.

**[APK 다운로드](https://github.com/YiJaeHyeok/LegendaryWars/releases/download/v0.2.0/legendary-wars-offline.apk)** · [릴리스/체크섬](https://github.com/YiJaeHyeok/LegendaryWars/releases/tag/v0.2.0) · [검증 기록](legendary-wars-reborn/VERIFICATION.ko.md)

## 설치

Android 8.0 이상에서 APK를 내려받아 설치합니다. 별도 OBB, 로그인, 인터넷 연결이 필요하지 않습니다. 기존 개인 복원판 0.1.0은 같은 서명키를 사용하는 이 릴리스로 업데이트할 수 있습니다.

APK SHA-256:

```text
a4285b47ad6d1290abcc9c6e7e2c766eab001231fe90673c8dc566442f5b4744
```

## 포함한 작업

- 원작 영웅 12종, 유닛 정의 69개와 원본 그래픽·폰트·음악.
- 원본 기본 수치/소환 비용/쿨다운, 15fps 기본값, 주요 속도·피해·방어 공식 반영.
- 직접 선택/추적/레인 이동, 부대 명령, 채굴·소환·구조 목표.
- 첫 튜토리얼, 채굴 미션, 다섯 번째 엘프 구조 미션을 새 엔진에서 승리까지 검증.

**원작 전체와 완전히 같은 완성판은 아닙니다.** 73개 스테이지 설정을 가져왔지만 모든 미션의 플레이를 완성하거나 검증하지 않았습니다. 후반 특수 모드/컨트롤러/일부 버프·AI는 남아 있고, 특정 후반 미션은 진행이 막힐 수 있습니다. 0.2.0의 Android 실기기 실행·터치·음악·성능은 아직 검증하지 못했습니다.

## 소스와 빌드

Android 프로젝트는 [legendary-wars-reborn/](legendary-wars-reborn)에 있습니다. 준비된 리소스를 포함하므로 앱 빌드에 원본 APK는 필요하지 않습니다. Android Studio에서 이 폴더를 열고 JDK 17 이상/Android SDK 35로 Gradle 동기화 후 Build APK(s)를 사용할 수 있습니다. AGP는 8.7.3입니다.

PowerShell의 로컬 빌드/검증 스크립트는 작성 환경의 프로젝트 상위 `.tools/jdk`, `.tools/android-sdk`, `.tools/combat-static/json-20240303.jar` 구성을 사용합니다. 분석/원본 회수 테스트에는 제외된 원본 샘플이 필요합니다. [자세한 설명](legendary-wars-reborn/README.md)을 참고하세요. 소스에서 자체 빌드한 APK는 본인의 키로 서명되므로 릴리스 서명과 다를 수 있습니다.

## 보안과 출처

원본 앱은 실행·설치하지 않았고 새 APK에 원본 DEX/SO/JAR를 넣지 않았습니다. 새 APK는 요청 권한0, 네트워크/WebView/광고 SDK 제외를 정적 검사했습니다. [APK 검사 결과](verification/0.2.0/apk-security-audit.json)와 [자료 해시](legendary-wars-reborn/asset-provenance.json)를 포함합니다. 무감염 인증은 아닙니다.

원본 APK/XAPK/OBB, 분석용 실행 파일, 개인 서명키, 개발 도구와 로컬 설정은 이 저장소에 올리지 않았습니다. 원작 명칭·그림·음악의 저작자는 원 제작자이며 이 프로젝트는 공식 배포본이 아닙니다. 원작 자료에 새로운 오픈소스 라이선스를 부여하지 않습니다.
