# Acceptance-test map

Domain tests run on the JVM (`./gradlew :core:domain:test`) and were executed while building this version: **36/36 passing**.

| Test | Where | Status |
|---|---|---|
| T01 duplicate across phone + backup | `AcceptanceTest.t01_*` | ✅ JVM |
| T02 unapproved sender body never read | `t02_*` | ✅ JVM |
| T03 late salary keeps cycle open | `t03_*` | ✅ JVM |
| T04 allowance vs percentage limits | `t04_*` | ✅ JVM |
| T05 cross-profile transfer once | `t05_*` | ✅ JVM |
| T06 negative source budget + alert | `t06_*` | ✅ JVM |
| T07 tied transfers → review | `t07_*` | ✅ JVM |
| T08 card repayment only | `t08_*` | ✅ JVM |
| T09 refund corrects closed cycle | `t09_*` | ✅ JVM |
| T10 manual planned vs funded | `t10_*` | ✅ JVM |
| T11 child limits > parent | `t11_*` | ✅ JVM |
| T12 settlement rejected | `t12_*` | ✅ JVM |
| T13 provisional surplus | `t13_*` | ✅ JVM |
| T14 liquidity change vs approved plan | `t14_*` | ✅ JVM |
| T15 late message, earlier execution | `t15_*` | ✅ JVM |
| T16 reserve 20 % | `t16_*` | ✅ JVM |
| T17–T23 obligations & forecast | `t17_*` … `t23_*` | ✅ JVM |
| T24, T25, T26, T29, T31 theme/direction/motion switching | `app/androidTest/ThemeSwitchTest` | ⏳ emulator (CI) |
| T27 reduce motion | `LocalMotion` + `ThemeSwitchTest` | ⏳ emulator (CI) |
| T28 offline + large screen | No INTERNET permission (checked), NavigationRail ≥600dp | ⏳ device |
| T29/T33 contrast for all palettes/accents | `AcceptanceTest.t29_*`, `t33_*` (JVM ✅) + `app/test/ThemeContrastTest` | ✅ JVM / ⏳ CI |
| T30 dynamic colour fallback | `BudgetTheme` (API ≥ 31 only) | ⏳ device |
| T32 appearance persists (restart, backup) | `appearance_pref` table + backup restore | ⏳ device |
| Phase 1 encryption / missing key / uniqueness / backup | `core/data/androidTest/DatabaseTest` | ⏳ emulator (CI) |

Extra JVM tests: exact money & Arabic digits, overflow, HALF_EVEN, distinct valid transfers kept, idempotent recalculation, consolidation without duplicates, FX pending + separate fees, unknown balance ≠ 0, balance mismatch review, oldest-first split, month-end policy required.
