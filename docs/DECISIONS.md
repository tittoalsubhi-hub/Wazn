# Decision records and open decision points

| ID | Topic | Decision | Status |
|---|---|---|---|
| D-01 | Percentage rounding | Exact BigDecimal, HALF_EVEN to the halala. Percent limits use the first confirmed salary / opening amount only. | Implemented |
| D-02 | Days 29–31 in short months | No default. The user must choose “last day of month” or “1st of next month” before a schedule is generated (`PolicyRequiredException`). | **Needs owner confirmation** |
| D-03 | Holidays | No date shifting is applied; due dates are shown as entered. | **Needs owner confirmation** |
| D-04 | Non-SAR obligations | Shown, excluded from SAR forecasts and counted separately; no assumed exchange rate. | Implemented |
| D-05 | Generic parser | `GenericKeywordParser` is not a production bank parser. Its results are capped at MEDIUM and routed to review unless the user enables “trust this sender’s format” (a user-approved learned rule). | Implemented |
| D-06 | Cycle boundary | Events with receipt-time only within 12 h of a boundary are flagged for review. | Implemented |
| D-07 | Bank-specific parsers | Require anonymised samples per bank (spec §20). Add implementations of `SmsParser` with `verifiedForBank = true` and fixture tests. | **Needs samples** |
| D-08 | Transfer matching window | Incoming leg accepted from −10 min to +72 h; ties and amount differences go to review; missing leg alerts once after 24 h. | Implemented |
| D-09 | Savings settlement direction | Approved settlement moves budget from the goal’s profile to the funding profile, at the transfer time. Kept separate from ordinary reallocation. | **Needs owner confirmation** |
| D-10 | Cloud backup | Not configured; manual password-protected backup only. No silent upload. | Deferred |
| D-11 | Google Play | SMS permissions are restricted on Google Play; approval is not guaranteed. Debug builds are for personal installation. | Open |
| D-12 | Backup format | `BSMSBK1` + PBKDF2-SHA256 (210k) + AES-256-GCM + inner SHA-256; restore is INSERT OR IGNORE on stable keys. | Implemented |
| D-13 | SMS import format | SMS Backup & Restore XML; ≤50 MB, ≤200k records, ≤2000 chars per body; DOCTYPE rejected; inbox only. | Implemented |
| D-14 | Import window (owner request, v1.3) | Device sync and backup import take only the latest salary message and everything after it; if none is found, the last 30 days. | Implemented |
| D-15 | Verification codes (v1.3) | OTP / “do not share” messages are ignored and never stored. | Implemented |
| D-16 | Automatic processing (v1.3) | Approved bank senders are processed automatically (supersedes the review-first default of D-05). Missing amount, possible duplicates, foreign currency without SAR debit and unrecognised messages still go to review. Incoming transfers default to “fund the budget” and can be changed. | Implemented |
| D-17 | Bank-level account (v1.3) | All messages of a bank are booked to one bank-level account created on approval, regardless of card digits. Credit-card purchases stay excluded from the budget. | Implemented |
| D-18 | Amount source | The amount is the number written next to a currency code (SAR, ر.س …); card digits and bill numbers are never used. | Implemented |
| D-19 | Ignore rules (v1.4, owner) | Messages containing "الرمز" / "رمز المرور" (or other code keywords), credit-card movements (anything mentioning a credit card that is not a repayment), and general notices without an amount next to a currency are ignored and never stored. Only credit-card repayments are counted, as an expense from the bank account. Existing undecided events matching these rules are marked ignored at startup. | Implemented |
| D-20 | Amount next to SAR only (v1.5) | The amount must be on the same line as the currency; numbers touching "*" (masked card/account) are never amounts. Outgoing local transfers ("حوالة داخلية/محلية") are recognised. Parser v2 re-reads undecided auto-processed events. | Implemented |
| D-21 | Manual amount (v1.5, owner) | For messages under review the user may correct the amount before confirming; the original and new values are written to the audit log. | Implemented |
