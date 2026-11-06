# key | English | Arabic  — single source for values/strings.xml and values-ar/strings.xml
S = r"""
app_name|BudgetSMS|ميزانية الرسائل
tab_home|Home|الرئيسية
tab_activity|Activity|العمليات
tab_budget|Budget|الميزانية
tab_goals|Savings|الادخار
tab_more|More|المزيد
back|Back|رجوع
next|Next|التالي
start|Start|ابدأ
save|Save|حفظ
cancel|Cancel|إلغاء
confirm|Confirm|تأكيد
approve|Approve|موافقة
apply|Apply|تطبيق
create|Create|إنشاء
edit|Edit|تعديل
add|Add|إضافة
open|Open|فتح
link|Link|ربط
match|Match|مطابقة
reject|Reject|رفض
revoke|Revoke|إلغاء الموافقة
reapprove|Approve again|موافقة من جديد
choose|Choose…|اختر…
see_all|See all|عرض الكل
none_yet|Nothing here yet.|لا يوجد شيء بعد.
not_available|Not available|غير متاح
active|Active|نشط
revoked|Revoked|ملغى
name|Name|الاسم
notes|Notes|ملاحظات
amount|Amount|المبلغ
currency|Currency|العملة
currency_sar|SAR|ر.س
icon|Icon|الأيقونة
color|Color|اللون
profile|Profile|الملف
profiles|Profiles|الملفات
bank|Bank|البنك
bank_name|Bank name|اسم البنك
new_bank|Add a new bank|إضافة بنك جديد
accounts|Accounts|الحسابات
account_type|Account type|نوع الحساب
acc_current|Current|جاري
acc_savings|Savings|ادخار
acc_card|Credit card|بطاقة ائتمانية
last_digits|Last 4 digits as in bank SMS|آخر ٤ أرقام كما تظهر في رسائل البنك
new_account|New account|حساب جديد
new_profile|New profile|ملف جديد
edit_profile|Edit profile|تعديل الملف
all_profiles|All profiles|كل الملفات
manage_profiles|Profiles & accounts|الملفات والحسابات
transactions|Transactions|العمليات
transaction_details|Transaction details|تفاصيل العملية
sync_now|Read new messages|قراءة الرسائل الجديدة
sync_sub|Process messages from approved senders on this phone|معالجة رسائل الجهات المعتمدة على هذا الجهاز
alerts|Alerts|التنبيهات
alert_open_app|Open the app to see details.|افتح التطبيق لعرض التفاصيل.
alert_budget_negative|A profile budget is below zero|ميزانية أحد الملفات أصبحت بالسالب
alert_limit|A category limit was exceeded|تم تجاوز حد إحدى الفئات
alert_child_limits|Subcategory limits exceed their main category|حدود الفئات الفرعية تتجاوز حد الفئة الرئيسية
alert_transfer_missing|Incoming transfer message not received after 24 hours|لم تصل رسالة الحوالة الواردة بعد ٢٤ ساعة
alert_balance_mismatch|Bank balance differs from the calculated balance|رصيد البنك يختلف عن الرصيد المحسوب
alert_installment|Liquidity cannot cover the next planned installment|السيولة لا تغطي القسط المخطط القادم
alert_overdue|An obligation is overdue|يوجد التزام متأخر
alert_liquidity|Liquidity crossed your alert threshold|تجاوزت السيولة حد التنبيه
channel_transactions|New transactions|عمليات جديدة
channel_alerts|Financial alerts|تنبيهات مالية
notif_new|%1$d new transactions recorded|تم تسجيل عمليات جديدة: %1$d
notif_review|%1$d transactions need your review|عمليات تحتاج مراجعتك: %1$d
prov_confirmed|Confirmed by bank|مؤكد من البنك
prov_calculated|Calculated|محسوب
prov_planned|Planned|مخطط
prov_forecast|Forecast|متوقع
prov_review|Under review|قيد المراجعة
remaining_budget|Remaining budget|المتبقي من الميزانية
remaining|Remaining|المتبقي
funded|Funded|الممول
spent|Spent|المصروف
planned|Planned|المخطط
under_review|Under review|قيد المراجعة
unallocated|Unallocated|غير مخصص
unallocated_note|Unallocated: %1$s — not savings until you decide.|غير مخصص: %1$s — ليس ادخاراً حتى تقرر.
budget_negative_note|Budget is negative after a confirmed transfer. Nothing was reversed.|الميزانية سالبة بعد تحويل مؤكد. لم يُلغَ أي شيء.
no_cycle_title|No open cycle yet|لا توجد دورة مفتوحة بعد
no_cycle_body|A cycle opens with your first confirmed salary SMS, an approved incoming transfer, or a manual opening.|تبدأ الدورة مع أول رسالة راتب مؤكدة أو تحويل وارد معتمد أو فتح يدوي.
review_needed|%1$d transactions need review|عمليات تحتاج مراجعة: %1$d
review_needed_body|Nothing uncertain is counted silently. Tap to review.|لا يُحتسب أي شيء غير مؤكد بصمت. اضغط للمراجعة.
spending_by_category|Spending by category|المصروف حسب الفئة
spending_trend|Spending this cycle|المصروف في هذه الدورة
dashed_is_funded|Dashed line: funded budget|الخط المتقطع: الميزانية الممولة
show_values|Show exact values|عرض القيم الدقيقة
hide_values|Hide values|إخفاء القيم
last_stated|Bank stated %1$s on %2$s|رصيد البنك %1$s بتاريخ %2$s
balance_unavailable|Balance unavailable|الرصيد غير متاح
net_movement|Known net movement %1$s|صافي الحركة المعروفة %1$s
upcoming_obligations|Due soon|مستحقة قريباً
savings_requests|Savings requests|طلبات الادخار
savings_requests_body|%1$d closed salary cycles have a surplus to save.|دورات راتب مغلقة لديها فائض للادخار: %1$d
overspent_by|Over by %1$s|تجاوز بمقدار %1$s
filter_all|All|الكل
filter_review|To review|للمراجعة
filter_expenses|Expenses|المصروفات
filter_income|Income|الدخل
filter_transfers|Transfers|التحويلات
search_hint|Search reference, category…|ابحث بالمرجع أو الفئة…
no_transactions|No transactions yet|لا توجد عمليات بعد
no_transactions_body|Approve your bank senders and read messages to begin.|اعتمد جهات البنك واقرأ الرسائل للبدء.
kind_salary|Salary|راتب
kind_allowance|Allowance|بدل
kind_incoming|Incoming transfer|حوالة واردة
kind_refund|Refund|استرداد
kind_purchase|Card purchase|شراء بالبطاقة
kind_cc_purchase|Credit card purchase|شراء بالبطاقة الائتمانية
kind_atm|Cash withdrawal|سحب نقدي
kind_outgoing|Outgoing transfer|حوالة صادرة
kind_repayment|Card repayment|سداد البطاقة
kind_fee|Bank fee|رسوم بنكية
kind_reversal|Reversal|عكس عملية
kind_failed|Failed transaction|عملية فاشلة
kind_balance|Balance notice|إشعار رصيد
kind_unknown|Unrecognised|غير معروف
state_discovered|Discovered|مكتشفة
state_unapproved|Unapproved sender|جهة غير معتمدة
state_review|Under review|قيد المراجعة
state_confirmed|Confirmed|مؤكدة
state_reversed|Reversed|معكوسة
state_duplicate|Duplicate|مكررة
state_pending_fx|Waiting for SAR amount|بانتظار المبلغ بالريال
reason_unsupported|Message format is not supported yet|صيغة الرسالة غير مدعومة بعد
reason_low_confidence|Not processed automatically — please confirm|لم تُعالج تلقائياً — يرجى التأكيد
reason_missing_amount|Amount not found in the message|لم يُعثر على المبلغ في الرسالة
reason_missing_account|Account not linked|الحساب غير مربوط
reason_duplicate|Looks like a possible duplicate|قد تكون مكررة
reason_timestamp|No trusted bank time; receipt time used|لا يوجد وقت بنكي موثوق؛ استُخدم وقت الاستلام
reason_boundary|Near the boundary between two cycles|قريبة من الحد بين دورتين
reason_fx|Foreign currency without the actual SAR debit|عملة أجنبية دون المبلغ المخصوم بالريال
reason_balance|Bank balance differs from calculation|رصيد البنك يختلف عن الحساب
reason_tie|Several transfers match equally|عدة تحويلات متطابقة بالتساوي
reason_amount_mismatch|Incoming amount differs|المبلغ الوارد مختلف
reason_income|Choose how this income is treated|اختر طريقة معاملة هذا الدخل
reason_obligation_tie|Could pay several obligations|قد تسدد عدة التزامات
why_review|Why it needs review|سبب المراجعة
original_message|Original message|الرسالة الأصلية
parser_info|Reader %1$s v%2$d · %3$d message(s) as evidence|القارئ %1$s إصدار %2$d · رسائل الإثبات: %3$d
extracted_fields|Extracted details|التفاصيل المستخرجة
field_account|Account|الحساب
field_reference|Reference|المرجع
field_time|Time|الوقت
field_balance_after|Balance after|الرصيد بعد العملية
field_fee|Fee|الرسوم
field_original|Original amount|المبلغ الأصلي
field_type|Type|النوع
field_category|Category|الفئة
field_own_counterparty|Other own account|حسابك الآخر
field_income_treatment|Treat this income as|عامل هذا الدخل كـ
external_party|External party|طرف خارجي
time_bank|bank time|وقت البنك
time_receipt|receipt time|وقت الاستلام
time_user|corrected by you|صححته أنت
correct_date|Correct the date|تصحيح التاريخ
your_decision|Your decision|قرارك
earmark_note|Earmarking does not mean a savings transfer happened.|التخصيص للادخار لا يعني أن تحويل ادخار قد تم.
cannot_confirm_without_amount|This message has no amount. It stays under review; no manual amount can be entered.|لا يوجد مبلغ في هذه الرسالة. تبقى قيد المراجعة ولا يمكن إدخال مبلغ يدوياً.
mark_duplicate|Mark duplicate|تعليم كمكررة
match_candidates|Matching incoming messages|رسائل واردة مطابقة
link_original|Original transaction|العملية الأصلية
original_transaction|Corrects this transaction|تصحح هذه العملية
pays_obligation|Pays an obligation?|تسدد التزاماً؟
choose_one_obligation|Several obligations match. Choose one — a movement pays only one.|عدة التزامات مطابقة. اختر واحداً — الحركة تسدد التزاماً واحداً فقط.
treat_fund|Fund the budget|تمويل الميزانية
treat_outside|Income outside the budget|دخل خارج الميزانية
treat_earmark|Earmark for savings|مخصص للادخار
current_cycle|Current cycle|الدورة الحالية
open_cycle|Open cycle|فتح دورة
no_open_cycle_body|No open cycle for this profile.|لا توجد دورة مفتوحة لهذا الملف.
opened_on|opened %1$s|فُتحت %1$s
percent_base|Percentage base: %1$s (first confirmed salary or opening amount)|أساس النسب: %1$s (أول راتب مؤكد أو مبلغ الافتتاح)
reallocations|Reallocated in %1$s · out %2$s|وارد بالتخصيص %1$s · صادر %2$s
income_outside|Income outside budget: %1$s|دخل خارج الميزانية: %1$s
earmarked|Earmarked for savings: %1$s|مخصص للادخار: %1$s
child_limits_warning|Subcategory limits exceed: %1$s. Caps are not added as extra money.|حدود الفئات الفرعية تتجاوز: %1$s. الحدود لا تُضاف كأموال إضافية.
category_limits|Category limits|حدود الفئات
add_category|Category|فئة
past_cycles|Past cycles|الدورات السابقة
no_rollover_note|Surplus or deficit is kept for reports and never rolled into the next budget.|يُحفظ الفائض أو العجز للتقارير ولا يُرحَّل إلى الميزانية التالية.
limit|Limit|الحد
limit_for|Limit for %1$s|حد %1$s
limit_none|None|بلا حد
limit_fixed|Fixed SAR|مبلغ ثابت
limit_percent|Percentage|نسبة مئوية
percent_of_base|Calculated from %1$s and not increased by allowances.|تُحسب من %1$s ولا تزيد مع البدلات.
percent_no_base|Applies once the cycle has a base amount.|تُطبّق عند وجود مبلغ أساس للدورة.
planned_base_hint|Planned base (optional, not a deposit)|المبلغ المخطط (اختياري، ليس إيداعاً)
name_ar|Name (Arabic)|الاسم (عربي)
name_en|Name (English)|الاسم (إنجليزي)
parent_category|Parent category|الفئة الرئيسية
main_category|Main category|فئة رئيسية
uncategorized|Uncategorized|غير مصنف
no_profiles|No profiles yet|لا توجد ملفات بعد
no_profiles_body|Create a profile to group accounts and cycles.|أنشئ ملفاً لتجميع الحسابات والدورات.
forecast|Forecast|التوقعات
transfers|Transfers|التحويلات
no_requests_body|Requests appear when a confirmed-salary cycle closes with a surplus.|تظهر الطلبات عند إغلاق دورة راتب مؤكدة بفائض.
provisional_warning|Provisional: %1$s is still under review and may change the surplus.|مبدئي: %1$s ما زال قيد المراجعة وقد يغيّر الفائض.
create_goal|Create goal|إنشاء هدف
approve_anyway|Approve despite uncertainty|الموافقة رغم عدم التأكد
provisional_confirm_body|%1$s is unresolved. The target may change later; corrections will be tracked.|%1$s غير محسوم. قد يتغير الهدف لاحقاً وسيتم تتبع التصحيحات.
no_goals|No savings goals|لا توجد أهداف ادخار
no_goals_body|Goals come from the surplus of closed salary cycles.|تأتي الأهداف من فائض دورات الراتب المغلقة.
goal|Goal|هدف
goal_name|Goal name|اسم الهدف
target|Target|الهدف
saved|Saved|المدخر
target_x|Target %1$s|الهدف %1$s
saved_x|Saved %1$s (bank-verified)|المدخر %1$s (موثّق بنكياً)
remaining_x|Remaining %1$s|المتبقي %1$s
additional_savings|Additional savings %1$s|ادخار إضافي %1$s
pending_allocation|Pending allocation %1$s|بانتظار التخصيص %1$s
destination_x|Destination: %1$s|الوجهة: %1$s
destination_account|Destination savings account|حساب الادخار المستهدف
no_savings_accounts|Add a savings account first.|أضف حساب ادخار أولاً.
verified_transfers|Verified savings transfers|تحويلات ادخار موثّقة
incoming_missing|Incoming message not yet received|لم تصل رسالة الاستلام بعد
incoming_confirmed|Incoming message confirmed|رسالة الاستلام مؤكدة
allocate|Allocate|تخصيص
available_to_allocate|Available to allocate: %1$s|المتاح للتخصيص: %1$s
oldest_first_note|Suggested oldest goal first; you can change or split.|مقترح: الأقدم أولاً، ويمكنك التعديل أو التقسيم.
cross_profile_settlement|Funded from another profile — budget settlement:|ممول من ملف آخر — تسوية الميزانية:
settle_approve|Approve settlement between profiles|الموافقة على التسوية بين الملفات
settle_reject_allocate|No settlement, allocate to the goal|بلا تسوية، خصصه للهدف
settle_reject_pending|No settlement, leave allocation pending|بلا تسوية، اترك التخصيص معلقاً
allocation_exceeds|Allocations cannot exceed the transfer amount.|لا يمكن أن يتجاوز التخصيص مبلغ التحويل.
horizon|Forecast horizon|مدى التوقع
days_n|%1$d days|%1$d يوماً
accounts_in_estimate|Accounts in the estimate|الحسابات في التقدير
unknown_balances_excluded|%1$d accounts have no trusted balance and are excluded.|حسابات بلا رصيد موثوق ومستبعدة: %1$d
reserve|Reserve|الاحتياطي
reserve_none|None|بدون
reserve_fixed|Fixed SAR|مبلغ ثابت
reserve_percent|Percent of liquidity|نسبة من السيولة
override_note|Changes here apply to this estimate only.|التغييرات هنا لهذا التقدير فقط.
estimate|Estimate|التقدير
calculated_balances|Calculated balances|الأرصدة المحسوبة
obligations_in_horizon|Unpaid obligations in horizon|الالتزامات غير المسددة ضمن المدى
liquidity_after|Liquidity after obligations|السيولة بعد الالتزامات
available_for_savings|Available for savings|المتاح للادخار
non_sar_excluded|%1$d non-SAR obligations excluded (no assumed exchange rate).|التزامات بغير الريال مستبعدة (بلا سعر صرف مفترض): %1$d
expected_salary_note|An expected salary is never counted as a deposit.|الراتب المتوقع لا يُحتسب إيداعاً أبداً.
suggested_plan|Suggested savings plan|خطة الادخار المقترحة
no_plan_items|No installment can be suggested right now.|لا يمكن اقتراح قسط حالياً.
liquidity_gap|Gap %1$s — the full target %2$s is kept.|الفجوة %1$s — يبقى الهدف الكامل %2$s.
approved_plan_compare|Approved plan %1$s · new proposal %2$s|الخطة المعتمدة %1$s · المقترح الجديد %2$s
plan_changed_needs_approval|Liquidity changed. The approved plan stays until you approve the new one.|تغيرت السيولة. تبقى الخطة المعتمدة حتى توافق على الجديدة.
approve_plan|Approve plan|اعتماد الخطة
no_bank_transfer_note|The app never moves money. Make transfers in your bank app.|التطبيق لا ينقل الأموال. نفّذ التحويلات من تطبيق البنك.
no_transfers|No internal transfers|لا توجد تحويلات داخلية
no_transfers_body|Transfers between your own accounts appear here.|تظهر هنا التحويلات بين حساباتك.
cross_profile|between profiles|بين الملفات
obligations|Obligations|الالتزامات
ob_monthly|Monthly|شهري
ob_one_time|One-time|مرة واحدة
occ_upcoming|Upcoming|قادم
occ_due|Due today|مستحق اليوم
occ_overdue|Overdue|متأخر
occ_partial|Partially paid|مسدد جزئياً
occ_paid|Paid|مسدد
occ_cancelled|Cancelled|ملغى
occ_paused|Paused|موقوف
commitments_12m|Known commitments · next 12 months|الالتزامات المعروفة · ١٢ شهراً القادمة
month_end_policy_needed|%1$s needs a month-end policy before its schedule is generated.|%1$s يحتاج سياسة نهاية الشهر قبل إنشاء جدوله.
no_obligations|No obligations|لا توجد التزامات
no_obligations_body|Add rent, installments or subscriptions to forecast them.|أضف الإيجار أو الأقساط أو الاشتراكات لتوقعها.
days_overdue|%1$d days overdue|متأخر %1$d يوماً
days_left|%1$d days left|متبقٍ %1$d يوماً
add_obligation|Obligation|التزام
expected|Expected|المتوقع
confirmed_paid|Confirmed paid|المسدد المؤكد
due_not_proof|Reaching the due date is not proof of payment; only a confirmed SMS pays it.|بلوغ تاريخ الاستحقاق ليس دليلاً على السداد؛ فقط رسالة مؤكدة تسدده.
payment_candidates|Possible payments|مدفوعات محتملة
change_this_amount|Change this occurrence|تعديل هذا الاستحقاق
cancel_occurrence|Cancel occurrence|إلغاء الاستحقاق
pause|Pause|إيقاف مؤقت
resume|Resume|استئناف
end_series|End series|إنهاء السلسلة
expected_amount|Expected amount|المبلغ المتوقع
non_sar_note|Non-SAR obligations are shown but excluded from SAR forecasts.|الالتزامات بغير الريال تُعرض لكنها تُستبعد من توقعات الريال.
first_due_date|First due date|تاريخ أول استحقاق
due_date|Due date|تاريخ الاستحقاق
date_format_hint|Format: YYYY-MM-DD|الصيغة: سنة-شهر-يوم
installments_count|Number of installments|عدد الأقساط
empty_open_ended|Leave empty for an open-ended series.|اتركه فارغاً لسلسلة مفتوحة.
month_end_policy|This day does not exist in every month. Choose:|هذا اليوم غير موجود في كل الأشهر. اختر:
policy_last_day|Use the last day of shorter months|استخدم آخر يوم في الأشهر الأقصر
policy_next_first|Move to the 1st of the next month|انقله إلى أول الشهر التالي
remind_days_before|Remind days before|التذكير قبل (أيام)
profile_change_prospective|Profile change applies to future occurrences only.|تغيير الملف يُطبّق على الاستحقاقات القادمة فقط.
reports|Reports|التقارير
report_filters|Filters|عوامل التصفية
cycle|Cycle|الدورة
open_cycles|Open cycles|الدورات المفتوحة
export|Export|تصدير
export_note|Reports separate confirmed, calculated, planned and forecast amounts and never double-count internal transfers.|تفصل التقارير المبالغ المؤكدة والمحسوبة والمخططة والمتوقعة ولا تكرر التحويلات الداخلية.
export_pdf|PDF|PDF
export_excel|Excel|Excel
report_title|BudgetSMS financial report|تقرير ميزانية الرسائل المالي
report_summary|Summary|الملخص
report_item|Item|البند
report_amount|Amount|المبلغ
report_provenance|Source|المصدر
report_state|State|الحالة
appearance|Appearance|المظهر
appearance_sub|Colors, dark mode, accent, motion|الألوان والوضع الداكن واللون المميز والحركة
language|Language|اللغة
language_sub|Arabic or English|العربية أو الإنجليزية
profiles_accounts|Profiles & accounts|الملفات والحسابات
profiles_sub|Cycles, accounts and cards|الدورات والحسابات والبطاقات
senders|Bank senders|جهات البنك
senders_sub|Approved SMS senders|جهات الرسائل المعتمدة
security|Security & privacy|الأمان والخصوصية
security_sub|Lock, masking, screenshots|القفل والإخفاء ولقطات الشاشة
data_backup|Data & backup|البيانات والنسخ الاحتياطي
data_sub|Import, backup, restore|استيراد ونسخ واستعادة
audit_log|Audit log|سجل التدقيق
audit_sub|Every decision and change|كل قرار وتغيير
obligations_sub|Rent, installments, subscriptions|الإيجار والأقساط والاشتراكات
forecast_sub|Liquidity and savings plan|السيولة وخطة الادخار
reports_sub|PDF and Excel|PDF و Excel
transfers_sub|Matching between your accounts|المطابقة بين حساباتك
version_x|Version %1$s (%2$s)|الإصدار %1$s (%2$s)
live_preview|Live preview|معاينة مباشرة
preview_sample|Sample figures for preview|أرقام توضيحية للمعاينة
theme_mode|Theme|السمة
mode_system|System|حسب النظام
mode_light|Light|فاتح
mode_dark|Dark|داكن
pure_black|Pure black|أسود خالص
pure_black_sub|Deeper dark mode for OLED screens|وضع داكن أعمق لشاشات OLED
color_palette|Color palette|لوحة الألوان
dynamic_color|Wallpaper colors|ألوان الخلفية
dynamic_color_sub|Use Material You colors from your wallpaper|استخدام ألوان Material You من خلفية الجهاز
dynamic_unavailable|Wallpaper colors need Android 12 or later.|ألوان الخلفية تتطلب أندرويد ١٢ أو أحدث.
custom_accent|Custom accent|لون مميز مخصص
custom_accent_sub|Pick any color; the full theme is generated from it.|اختر أي لون وسيُبنى المظهر كاملاً منه.
low_contrast_adjusted|Low contrast detected: text tones are adjusted automatically for readability.|تباين منخفض: تُعدَّل درجات النص تلقائياً لسهولة القراءة.
contrast_and_shape|Contrast & shape|التباين والشكل
contrast|Contrast|التباين
contrast_standard|Standard|عادي
contrast_medium|Medium|متوسط
contrast_high|High|عالٍ
corners|Corners|الزوايا
corners_rounded|Rounded|دائرية
corners_soft|Soft|ناعمة
corners_sharp|Sharp|حادة
motion|Motion & animated icons|الحركة والأيقونات المتحركة
motion_full|Full|كاملة
motion_reduced|Reduced|مخففة
motion_off|Off|متوقفة
motion_sub|The system "Remove animations" setting always takes priority.|إعداد "إزالة الرسوم المتحركة" في النظام له الأولوية دائماً.
replay_animation|Replay|إعادة التشغيل
privacy_display|Privacy|الخصوصية
mask_amounts|Hide amounts|إخفاء المبالغ
mask_amounts_sub|Show •••• instead of figures|عرض •••• بدل الأرقام
lang_system|System language|لغة النظام
lang_ar|العربية|العربية
lang_en|English|English
language_note|Changing language never changes your data or results.|تغيير اللغة لا يغيّر بياناتك أو النتائج.
app_lock|App lock|قفل التطبيق
lock_with_pin|Lock with PIN|القفل برمز PIN
lock_sub|Asks after 30 seconds in the background|يُطلب بعد ٣٠ ثانية في الخلفية
change_pin|Change PIN|تغيير الرمز
set_pin|Set PIN|تعيين الرمز
pin|PIN|الرمز
pin_again|Repeat PIN|أعد إدخال الرمز
pin_wrong|Wrong PIN|رمز غير صحيح
unlock|Unlock|فتح
unlock_title|Unlock BudgetSMS|افتح ميزانية الرسائل
use_pin|Use PIN|استخدم الرمز
use_biometric|Use fingerprint or face|استخدم البصمة أو الوجه
secure_screen|Hide in recent apps|إخفاء في التطبيقات الأخيرة
secure_screen_sub|Blocks screenshots and previews|يمنع لقطات الشاشة والمعاينات
encryption_note|Your data is encrypted on this device with a key protected by Android Keystore.|بياناتك مشفرة على هذا الجهاز بمفتاح محمي بمخزن مفاتيح أندرويد.
senders_explain|Only messages from senders you approve are read. Import starts from your latest salary. Codes, credit-card purchases and general notices are ignored; only card repayments count.|لا تُقرأ إلا رسائل الجهات التي تعتمدها. يبدأ الاستيراد من آخر راتب. تُتجاهل الرموز ومشتريات البطاقة الائتمانية والرسائل العامة، ويُحتسب سداد البطاقة فقط.
discover_senders|Find senders on this phone|البحث عن الجهات في هذا الجهاز
approved_senders|Approved senders|الجهات المعتمدة
discovered_senders|Discovered senders|الجهات المكتشفة
messages_n|%1$d messages|عدد الرسائل: %1$d
trust_format|Process automatically|معالجة تلقائية
trust_format_sub|Confirm this bank's messages automatically for its account (any card). Codes are ignored.|اعتماد رسائل هذا البنك تلقائياً على حسابه (أي بطاقة). تُتجاهل رموز التحقق.
approve_sender_x|Approve %1$s|اعتماد %1$s
approve_sender_body|Approving lets the app read this sender's past and future messages. It creates no transaction by itself.|الاعتماد يسمح بقراءة رسائل هذه الجهة السابقة والقادمة، ولا ينشئ أي عملية بحد ذاته.
approve_sender_first|Approve a bank sender first to add accounts.|اعتمد جهة بنكية أولاً لإضافة الحسابات.
cycle_type|Cycle type|نوع الدورة
cycle_salary|Salary cycle|دورة راتب
cycle_transfer|Transfer cycle|دورة تحويل
cycle_manual|Manual cycle|دورة يدوية
cycle_scheduled|Scheduled cycle|دورة مجدولة
renewal|Renewal|التجديد
renew_manual|Manual|يدوي
renew_scheduled|Scheduled|مجدول
renew_next_transfer|At next approved transfer|عند التحويل المعتمد التالي
every_days|Every (days)|كل (أيام)
default_horizon|Default forecast horizon|مدى التوقع الافتراضي
cycle_rules_note|A salary cycle closes only when the next confirmed salary arrives.|تُغلق دورة الراتب فقط عند وصول الراتب المؤكد التالي.
no_opening_balance_note|No opening balance is assumed; balances come from bank SMS.|لا يُفترض رصيد افتتاحي؛ الأرصدة تأتي من رسائل البنك.
move_account|Move %1$s|نقل %1$s
impact_preview|Impact preview (open cycles)|معاينة الأثر (الدورات المفتوحة)
reassign_note|Applies from now; executed savings transfers stay unchanged. The change is audited.|يُطبّق من الآن؛ تبقى تحويلات الادخار المنفذة كما هي. يُسجَّل التغيير.
sms_sources|Message sources|مصادر الرسائل
import_sms_backup|Import SMS backup file|استيراد ملف نسخة الرسائل
import_sms_sub|SMS Backup & Restore XML; duplicates are prevented|ملف XML من SMS Backup & Restore؛ يُمنع التكرار
reprocess|Re-read undecided messages|إعادة قراءة الرسائل غير المحسومة
reprocess_sub|Uses the latest reader and your accounts|يستخدم أحدث قارئ وحساباتك
app_backup|App backup|نسخة التطبيق الاحتياطية
export_backup|Export encrypted backup|تصدير نسخة مشفرة
export_backup_sub|Password-protected file you keep|ملف محمي بكلمة مرور تحتفظ به
restore_backup|Restore backup|استعادة نسخة
restore_backup_sub|Merges without duplicates|دمج دون تكرار
cloud_backup_note|Cloud backup is off. Nothing is uploaded unless you configure it.|النسخ السحابي متوقف. لا يُرفع شيء ما لم تقم بإعداده.
backup_password|Backup password|كلمة مرور النسخة
backup_password_hint|At least 8 characters. It cannot be recovered.|٨ أحرف على الأقل. لا يمكن استرجاعها.
welcome_title|Your budget, written by your bank|ميزانيتك تكتبها رسائل بنكك
welcome_body|BudgetSMS builds your budget only from real bank messages — no manual postings, no guesses.|يبني التطبيق ميزانيتك من رسائل البنك الحقيقية فقط — بلا إدخال يدوي ولا تخمين.
feat_sms|Reads only senders you approve|يقرأ فقط الجهات التي تعتمدها
feat_private|Encrypted and offline on your phone|مشفر ويعمل دون إنترنت على جهازك
feat_custom|Colors, themes and motion your way|ألوان وسمات وحركة كما تحب
make_it_yours|Make it yours|اجعله على ذوقك
more_in_settings|More options in Settings › Appearance.|خيارات أكثر في الإعدادات › المظهر.
sms_step_title|Connect your bank messages|اربط رسائل بنكك
sms_step_body|Allow SMS access to find bank senders by name, then approve each one.|اسمح بالوصول للرسائل للبحث عن جهات البنك بالاسم، ثم اعتمد كل جهة.
allow_sms|Allow SMS access|السماح بالوصول للرسائل
permission_denied|Permission denied. You can import an SMS backup instead or allow it later in Android settings.|رُفض الإذن. يمكنك استيراد نسخة رسائل بدلاً من ذلك أو السماح لاحقاً من إعدادات أندرويد.
profile_step_title|Create your first profile|أنشئ ملفك الأول
profile_step_body|A profile groups accounts and has its own cycles and budget.|الملف يجمع الحسابات وله دوراته وميزانيته الخاصة.
all_set|You're all set|كل شيء جاهز
all_set_body|New bank messages will update your budget automatically.|رسائل البنك الجديدة ستحدّث ميزانيتك تلقائياً.
db_error_title|Your data could not be opened|تعذر فتح بياناتك
db_error_body|Nothing was deleted. Restore a backup or contact support; the app will not recreate the database silently.|لم يُحذف شيء. استعد نسخة احتياطية أو تواصل مع الدعم؛ لن يعيد التطبيق إنشاء قاعدة البيانات بصمت.
msg_saved|Saved|تم الحفظ
msg_confirmed|Transaction confirmed|تم تأكيد العملية
msg_error|Something went wrong: %1$s|حدث خطأ: %1$s
msg_discovered|Senders discovered|تم العثور على الجهات
msg_sender_approved|Sender approved|تم اعتماد الجهة
msg_sender_revoked|Approval revoked; history kept|أُلغي الاعتماد مع حفظ السجل
msg_synced|Messages processed|تمت معالجة الرسائل
msg_imported|Backup imported without duplicates|تم الاستيراد دون تكرار
msg_reprocessed|Messages re-read|أعيدت قراءة الرسائل
msg_matched|Transfer matched|تمت مطابقة التحويل
msg_cycle_opened|Cycle opened|تم فتح الدورة
msg_goal_created|Goal created|تم إنشاء الهدف
msg_allocated|Allocation saved|تم حفظ التخصيص
msg_plan_approved|Plan approved|تم اعتماد الخطة
msg_payment_linked|Payment linked|تم ربط الدفعة
msg_backup_done|Encrypted backup saved|تم حفظ النسخة المشفرة
msg_restored|Restored (new/duplicates skipped): %1$s|تمت الاستعادة (جديد/متجاوز): %1$s
msg_wrong_password|Wrong password or damaged file|كلمة مرور خاطئة أو ملف تالف
"""
def rows():
    for line in [l for l in S.strip().splitlines() if l.strip()]:
        k, en, ar = line.split("|", 2)
        yield k.strip(), en, ar
S += r"""
gradients_background|Gradients & background|التدرجات والخلفية
gradient_level|Gradient on icons and cards|التدرج في الأيقونات والبطاقات
gradient_off|Off|بدون
gradient_subtle|Subtle|خفيف
gradient_vivid|Vivid|حيوي
background_style|Background|الخلفية
bg_plain|Plain|سادة
bg_gradient|Gradient|متدرجة
bg_aurora|Aurora|شفق
gradient_second_color|Second gradient color|اللون الثاني للتدرج
auto|Automatic|تلقائي
state_ignored|Ignored (code)|متجاهلة (رمز)
default_profile_name|My account|حسابي
msg_import_from_salary|Imported from last salary (%1$s)|تم الاستيراد من آخر راتب (%1$s)
msg_import_no_salary|No salary message found; imported the last 30 days (%1$s)|لم يُعثر على رسالة راتب؛ تم استيراد آخر ٣٠ يوماً (%1$s)
"""
S += r"""
field_amount_manual|Amount (edit if wrong)|المبلغ (عدّله إن كان خطأ)
manual_amount_note|Use the amount written next to SAR in the message. Changes are recorded in the audit log.|استخدم المبلغ المكتوب بجانب SAR في الرسالة. يُسجَّل التعديل في سجل التدقيق.
"""
