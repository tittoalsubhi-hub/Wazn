# Wazn (BudgetSMS) — وازن

تطبيق أندرويد أصلي (Kotlin + Jetpack Compose) يبني ميزانيتك **فقط** من رسائل البنك الأصلية المعتمدة، يعمل دون إنترنت، ومشفّر على الجهاز، ويدعم العربية والإنجليزية بالكامل (RTL/LTR)، مع لوحات ألوان متعددة وتخصيص كامل للمظهر وأيقونات ثابتة ومتحركة.

Native Android app (Kotlin + Jetpack Compose) that builds your budget **only** from approved original bank SMS. Offline-first, encrypted, fully bilingual (Arabic/English), with multiple colour palettes, a custom accent, light/dark/system mode and static + animated icons.

Implements *BudgetSMS Application Logic v1.1* and *Development Prompts v1.2* (with the appearance addendum, tests T29–T33).

## Modules
| Module | Contents |
|---|---|
| `core/domain` | Pure-Kotlin financial engine: exact halala money, consent gate, ingestion & dedup, cycles, budgets, transfers, balances, savings, obligations, forecasts, colour math. No Android dependency. |
| `core/data` | Room + SQLCipher (key wrapped by Android Keystore), DAOs for all 17 spec tables (+ supporting tables), repository, encrypted backup, SMS-backup XML import. |
| `app` | Compose UI, theme engine (9 palettes + custom accent + dynamic colour), animated icons, SMS reader/receiver, notifications, PDF/Excel reports, lock. |

## Build the APK
GitHub Actions builds and tests everything on each push (`.github/workflows/android.yml`) and uploads `BudgetSMS-debug-apk` (APK + SHA-256) only if every check passes.

Locally (Android SDK + JDK 17):
```
python3 scripts/check_project.py
./gradlew :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :core:data:connectedDebugAndroidTest :app:connectedDebugAndroidTest --no-parallel --max-workers=1
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Strings are generated from `tools/strings_data.py` → `python3 tools/gen_strings.py`.

See `docs/ARCHITECTURE.md`, `docs/DECISIONS.md` (open decision points), `docs/TESTING.md` (T01–T33 map).
