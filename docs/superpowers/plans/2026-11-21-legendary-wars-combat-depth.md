# Legendary Wars Offline v0.3.0 구현 계획

- 작성일: 2026-11-21
- 설계 스펙: `docs/superpowers/specs/2026-11-21-legendary-wars-combat-depth-design.md`

## 단계 요약
1. **Phase 1**: `reference/native-combat-findings.md` 갱신, `asset-safety.mjs` `validateWav` 추가, `prepare-assets.mjs` 상성/버프/면역/18종 WAV SFX 추출, `audit-apk.mjs` WAV 재검증.
2. **Phase 2**: `UnitSpec`, `GameData`, `BattleEngine`, `OriginalBattle`, `Progress` 전투 심화 4종 및 `supportCost` 공식 구현.
3. **Phase 3**: `LocalAudio` (`SoundPool`), `MainActivity`, `WarView` UI/HUD 결함 6종 수정, 데드 코드 정리, SFX 연동.
4. **Phase 4**: `OriginalEngineTest`, `RecoveredScenarioTest`, `assets.test.mjs` 신규 테스트 추가, `v0.3.0` (`versionCode 3`) APK 빌드·서명·감사(`audit-apk.mjs` + `rea`), GitHub 업로드.
