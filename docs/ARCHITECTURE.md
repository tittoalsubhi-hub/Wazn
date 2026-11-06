# Architecture

```
SMS sources (device inbox, SMS receiver, backup XML)
        │  header-only discovery (sender, date)
        ▼
ConsentGate ── body supplier is invoked only for approved, non-revoked senders (T02)
        ▼
IngestionPipeline  (core:domain, pure Kotlin)
  • fingerprint dedup across sources (T01)  • bank-reference identity  • similar → review, never deleted
  • ParserRegistry → bank parser or GenericKeywordParser (always routed to review)
        ▼
Room + SQLCipher (core:data)  raw_sms · sms_source · bank_event · decisions · plans · audit_log
        ▼
LedgerEngine.compute(LedgerInput) → cycles, assignment, budgets, balances, transfers, reviews, alerts
SavingsEngine / ObligationScheduler / ObligationLedger / LiquidityForecaster / SavingsPlanner
        ▼
AppSnapshot (StateFlow) → Compose UI (theme engine, animated icons, AR/EN)
```

* **Derived, not stored, results.** Cycles, budgets, balances and surpluses are recomputed from original events plus user decisions on every change, so recalculation is idempotent and late messages correct closed cycles automatically (T09, T15).
* **No manual bank postings.** The UI can confirm, classify or link events but cannot create a deposit, purchase or withdrawal.
* **Atomic writes.** Every multi-row decision uses `db.withTransaction`; uniqueness constraints protect transfer matches (`outgoingId`, `incomingId`) and obligation payments (`eventId`).
* **Provenance everywhere.** Each amount on screen and in reports carries Confirmed / Calculated / Planned / Forecast / Under review with icon + label.
* **Security.** SQLCipher DB, 256-bit random passphrase wrapped with an AES-GCM Keystore key in no-backup storage. Missing key or corruption → safe error screen; nothing is deleted or recreated. PIN stored as PBKDF2 hash. Optional FLAG_SECURE, amount masking, private notifications without amounts.
* **Theme engine.** `buildScheme(seed, dark, contrast, pureBlack)` generates full Material 3 schemes from any seed; `ColorMath.ensurePair` guarantees ≥4.5:1 (≥7:1 in high contrast) for every on-colour. Semantic colours (income, expense, overspend, savings, forecast, review) are fixed hues independent of the palette.
* **Motion.** `LocalMotion` = user level (Full/Reduced/Off) overridden by the system “remove animations” setting. Every animated icon renders its static final frame when motion is off.
