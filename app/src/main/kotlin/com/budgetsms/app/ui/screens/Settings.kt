package com.budgetsms.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetsms.app.AppViewModel
import com.budgetsms.app.BuildConfig
import com.budgetsms.app.R
import com.budgetsms.app.ui.components.*
import com.budgetsms.app.ui.icons.AnimatedCheck
import com.budgetsms.app.ui.icons.AppIcons
import com.budgetsms.app.ui.icons.ProgressRing
import com.budgetsms.app.ui.icons.SyncIcon
import com.budgetsms.app.ui.theme.LocalSemantic
import com.budgetsms.app.ui.theme.buildScheme
import com.budgetsms.app.ui.theme.isDark
import com.budgetsms.data.SettingKeys
import com.budgetsms.domain.appearance.*
import com.budgetsms.domain.model.*
import com.budgetsms.domain.money.Money
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

@Composable
fun MoreScreen(onOpen: (String) -> Unit) {
    data class Entry(val route: String, val icon: ImageVector, val title: Int, val sub: Int)
    val entries = listOf(
        Entry("obligations", AppIcons.obligations, R.string.obligations, R.string.obligations_sub), Entry("forecast", AppIcons.forecast, R.string.forecast, R.string.forecast_sub),
        Entry("reports", AppIcons.reports, R.string.reports, R.string.reports_sub), Entry("transfers", AppIcons.transfer, R.string.transfers, R.string.transfers_sub),
        Entry("appearance", AppIcons.palette, R.string.appearance, R.string.appearance_sub), Entry("language", AppIcons.language, R.string.language, R.string.language_sub),
        Entry("profiles", AppIcons.profile, R.string.profiles_accounts, R.string.profiles_sub), Entry("senders", AppIcons.senders, R.string.senders, R.string.senders_sub),
        Entry("security", AppIcons.security, R.string.security, R.string.security_sub), Entry("data", AppIcons.backup, R.string.data_backup, R.string.data_sub),
        Entry("audit", AppIcons.history, R.string.audit_log, R.string.audit_sub),
    )
    ScreenColumn {
        SectionCard(null) { entries.forEach { e -> SettingRow(e.icon, stringResource(e.title), stringResource(e.sub), onClick = { onOpen(e.route) }) } }
        Text(stringResource(R.string.version_x, BuildConfig.VERSION_NAME, BuildConfig.GIT_SHA), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Appearance studio with a live preview (addendum): palettes, dynamic colour, custom accent, mode, contrast, corners, motion. */
@Composable
fun AppearanceScreen(vm: AppViewModel, pref: AppearancePreference) {
    val dark = isDark(pref)
    ScreenColumn {
        LivePreview(pref)
        SectionCard(stringResource(R.string.theme_mode), icon = AppIcons.darkMode) {
            ChoiceRow(ThemeMode.entries, pref.mode, { stringResource(when (it) { ThemeMode.SYSTEM -> R.string.mode_system; ThemeMode.LIGHT -> R.string.mode_light; ThemeMode.DARK -> R.string.mode_dark }) },
                { m -> vm.updateAppearance { it.copy(mode = m) } }, icon = { when (it) { ThemeMode.SYSTEM -> AppIcons.auto; ThemeMode.LIGHT -> AppIcons.lightMode; ThemeMode.DARK -> AppIcons.darkMode } })
            SwitchRow(stringResource(R.string.pure_black), stringResource(R.string.pure_black_sub), pref.pureBlack) { v -> vm.updateAppearance { it.copy(pureBlack = v) } }
        }
        SectionCard(stringResource(R.string.color_palette), icon = AppIcons.palette) {
            if (android.os.Build.VERSION.SDK_INT >= 31) SwitchRow(stringResource(R.string.dynamic_color), stringResource(R.string.dynamic_color_sub), pref.dynamicColor) { v -> vm.updateAppearance { it.copy(dynamicColor = v) } }
            else Text(stringResource(R.string.dynamic_unavailable), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.heightIn(max = 420.dp), userScrollEnabled = false) {
                items(Palette.entries.filter { it != Palette.CUSTOM }) { p ->
                    PaletteCard(p, p.seed, pref.palette == p && !pref.dynamicColor, dark) { vm.updateAppearance { it.copy(palette = p, dynamicColor = false) } }
                }
            }
        }
        SectionCard(stringResource(R.string.custom_accent), icon = AppIcons.wallpaper) {
            Text(stringResource(R.string.custom_accent_sub), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            ACCENT_SWATCHES.chunked(10).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                    row.forEach { sw ->
                        val selected = pref.palette == Palette.CUSTOM && pref.customSeed == sw && !pref.dynamicColor
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(sw)).border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { vm.updateAppearance { it.copy(palette = Palette.CUSTOM, customSeed = sw, dynamicColor = false) } }, contentAlignment = Alignment.Center) {
                            if (selected) Icon(AppIcons.check, null, Modifier.size(16.dp), tint = Color(ColorMath.ensureContrast(0xFFFFFFFF, sw)))
                        }
                    }
                }
            }
            if (pref.palette == Palette.CUSTOM) {
                val ratio = ColorMath.contrast(0xFFFFFFFF, pref.customSeed)
                if (ratio < 4.5) Text(stringResource(R.string.low_contrast_adjusted), style = MaterialTheme.typography.labelSmall, color = LocalSemantic.current.review)
            }
        }
        SectionCard(stringResource(R.string.gradients_background), icon = AppIcons.wallpaper) {
            Text(stringResource(R.string.gradient_level), style = MaterialTheme.typography.labelLarge)
            ChoiceRow(GradientLevel.entries, pref.gradient, { stringResource(when (it) { GradientLevel.OFF -> R.string.gradient_off; GradientLevel.SUBTLE -> R.string.gradient_subtle; GradientLevel.VIVID -> R.string.gradient_vivid }) },
                { v -> vm.updateAppearance { it.copy(gradient = v) } })
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.background_style), style = MaterialTheme.typography.labelLarge)
            ChoiceRow(BackgroundStyle.entries, pref.background, { stringResource(when (it) { BackgroundStyle.PLAIN -> R.string.bg_plain; BackgroundStyle.GRADIENT -> R.string.bg_gradient; BackgroundStyle.AURORA -> R.string.bg_aurora }) },
                { v -> vm.updateAppearance { it.copy(background = v) } })
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.gradient_second_color), style = MaterialTheme.typography.labelLarge)
            FilterChip(pref.gradientSeed == 0L, { vm.updateAppearance { it.copy(gradientSeed = 0L) } }, { Text(stringResource(R.string.auto)) })
            ACCENT_SWATCHES.chunked(10).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                    row.forEach { sw ->
                        val sel = pref.gradientSeed == sw
                        Box(Modifier.size(28.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, Color(sw))))
                            .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { vm.updateAppearance { it.copy(gradientSeed = sw) } })
                    }
                }
            }
        }
        SectionCard(stringResource(R.string.contrast_and_shape), icon = AppIcons.contrast) {
            Text(stringResource(R.string.contrast), style = MaterialTheme.typography.labelLarge)
            ChoiceRow(ContrastLevel.entries, pref.contrast, { stringResource(when (it) { ContrastLevel.STANDARD -> R.string.contrast_standard; ContrastLevel.MEDIUM -> R.string.contrast_medium; ContrastLevel.HIGH -> R.string.contrast_high }) },
                { c -> vm.updateAppearance { it.copy(contrast = c) } })
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.corners), style = MaterialTheme.typography.labelLarge)
            ChoiceRow(CornerStyle.entries, pref.corners, { stringResource(when (it) { CornerStyle.ROUNDED -> R.string.corners_rounded; CornerStyle.SOFT -> R.string.corners_soft; CornerStyle.SHARP -> R.string.corners_sharp }) },
                { c -> vm.updateAppearance { it.copy(corners = c) } })
        }
        SectionCard(stringResource(R.string.motion), icon = AppIcons.motion) {
            ChoiceRow(AnimationLevel.entries, pref.animation, { stringResource(when (it) { AnimationLevel.FULL -> R.string.motion_full; AnimationLevel.REDUCED -> R.string.motion_reduced; AnimationLevel.OFF -> R.string.motion_off }) },
                { a -> vm.updateAppearance { it.copy(animation = a) } })
            Text(stringResource(R.string.motion_sub), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                var k by remember { mutableIntStateOf(0) }
                key(k) { AnimatedCheck(size = 44.dp) }
                SyncIcon(true, null, MaterialTheme.colorScheme.primary)
                TextButton(onClick = { k++ }) { Text(stringResource(R.string.replay_animation)) }
            }
        }
        SectionCard(stringResource(R.string.privacy_display), icon = AppIcons.hidden) {
            SwitchRow(stringResource(R.string.mask_amounts), stringResource(R.string.mask_amounts_sub), pref.maskAmounts) { v -> vm.updateAppearance { it.copy(maskAmounts = v) } }
        }
    }
}

@Composable
private fun PaletteCard(p: Palette, seed: Long, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val scheme = remember(seed, dark) { buildScheme(seed, dark, ContrastLevel.STANDARD, false) }
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "b")
    Column(Modifier.padding(4.dp).clip(MaterialTheme.shapes.medium).border(2.dp, border, MaterialTheme.shapes.medium).clickable(onClick = onClick)
        .background(scheme.surfaceContainer).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach { c -> Box(Modifier.size(22.dp).clip(CircleShape).background(c).border(2.dp, scheme.surfaceContainer, CircleShape)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(paletteName(p), style = MaterialTheme.typography.labelMedium, color = scheme.onSurface)
        if (selected) Icon(AppIcons.check, null, Modifier.size(16.dp), tint = scheme.primary)
    }
}

/** Live preview card: balance, chart and buttons rendered with the current theme. */
@Composable
private fun LivePreview(pref: AppearancePreference) {
    val sem = LocalSemantic.current; val cs = MaterialTheme.colorScheme
    SectionCard(stringResource(R.string.live_preview), icon = AppIcons.visible, container = cs.surfaceContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.remaining_budget), style = MaterialTheme.typography.labelMedium)
                Text(money(Money.sar(4250, 75)), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = cs.primary)
                Text(stringResource(R.string.preview_sample), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            ProgressRing(0.62f, Modifier.size(64.dp), cs.primary, cs.surfaceContainerHighest) { Text("62%", style = MaterialTheme.typography.labelMedium) }
        }
        Spacer(Modifier.height(8.dp))
        TrendChart(listOf(5, 9, 12, 20, 24, 31, 36).mapIndexed { i, v -> ChartPoint("$i", Money.sar(v * 100L)) }, Money.sar(4000), cs.primary, Modifier.height(80.dp))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text(stringResource(R.string.confirm)) }
            OutlinedButton(onClick = {}) { Text(stringResource(R.string.cancel)) }
            FilledTonalButton(onClick = {}) { Icon(AppIcons.add, null) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(sem.income to R.string.prov_confirmed, sem.expense to R.string.spent, sem.review to R.string.prov_review, sem.savings to R.string.tab_goals).forEach { (c, l) ->
                Row(Modifier.clip(CircleShape).background(c.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) { Text(stringResource(l), style = MaterialTheme.typography.labelSmall, color = c) }
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
fun LanguageScreen(vm: AppViewModel, pref: AppearancePreference) {
    ScreenColumn {
        SectionCard(stringResource(R.string.language), icon = AppIcons.language) {
            AppLanguage.entries.forEach { l ->
                Row(Modifier.fillMaxWidth().clickable { vm.updateAppearance { it.copy(language = l) } }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(pref.language == l, { vm.updateAppearance { it.copy(language = l) } })
                    Text(stringResource(when (l) { AppLanguage.SYSTEM -> R.string.lang_system; AppLanguage.ARABIC -> R.string.lang_ar; AppLanguage.ENGLISH -> R.string.lang_en }))
                }
            }
            Text(stringResource(R.string.language_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SecurityScreen(vm: AppViewModel, pref: AppearancePreference) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    var pinDialog by remember { mutableStateOf(false) }
    val lock = s.settings[SettingKeys.LOCK_ENABLED] == "true"
    ScreenColumn {
        SectionCard(stringResource(R.string.app_lock), icon = AppIcons.lock) {
            SwitchRow(stringResource(R.string.lock_with_pin), stringResource(R.string.lock_sub), lock) { if (it) pinDialog = true else vm.disableLock() }
            if (lock) SwitchRow(stringResource(R.string.use_biometric), null, s.settings[SettingKeys.BIOMETRIC] == "true") { vm.setSetting(SettingKeys.BIOMETRIC, it.toString()) }
            if (lock) TextButton(onClick = { pinDialog = true }) { Text(stringResource(R.string.change_pin)) }
        }
        SectionCard(stringResource(R.string.privacy_display), icon = AppIcons.hidden) {
            SwitchRow(stringResource(R.string.mask_amounts), stringResource(R.string.mask_amounts_sub), pref.maskAmounts) { v -> vm.updateAppearance { it.copy(maskAmounts = v) } }
            SwitchRow(stringResource(R.string.secure_screen), stringResource(R.string.secure_screen_sub), s.settings[SettingKeys.SECURE_SCREEN] == "true") { vm.setSetting(SettingKeys.SECURE_SCREEN, it.toString()) }
        }
        MessageBanner(stringResource(R.string.encryption_note), AppIcons.security, MaterialTheme.colorScheme.primary)
    }
    if (pinDialog) {
        var pin by remember { mutableStateOf("") }; var again by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { pinDialog = false }, title = { Text(stringResource(R.string.set_pin)) }, text = {
            Column {
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text(stringResource(R.string.pin)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                OutlinedTextField(again, { again = it.filter(Char::isDigit).take(8) }, label = { Text(stringResource(R.string.pin_again)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            }
        }, confirmButton = { TextButton(enabled = pin.length >= 4 && pin == again, onClick = { vm.setPin(pin); pinDialog = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { pinDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

/** Approved-sender allowlist management (spec §04). */
@Composable
fun SendersScreen(vm: AppViewModel) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    var approving by remember { mutableStateOf<String?>(null) }
    val approved = s.approvedSenders.associateBy { it.senderId }
    ScreenColumn {
        MessageBanner(stringResource(R.string.senders_explain), AppIcons.info, MaterialTheme.colorScheme.primary)
        Button(onClick = { vm.discoverSenders() }) { Icon(AppIcons.sync, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.discover_senders)) }
        SectionCard(stringResource(R.string.approved_senders), icon = AppIcons.senders) {
            if (s.approvedSenders.isEmpty()) Text(stringResource(R.string.none_yet))
            s.approvedSenders.forEach { a ->
                val active = a.revokedAt == null
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Fmt.ltr(a.senderId), style = MaterialTheme.typography.bodyLarge)
                            Text((s.banks.firstOrNull { it.id == a.bankId }?.name ?: "") + " · " + stringResource(if (active) R.string.active else R.string.revoked), style = MaterialTheme.typography.bodySmall)
                        }
                        if (active) TextButton(onClick = { vm.revokeSender(a.senderId) }) { Text(stringResource(R.string.revoke)) }
                        else TextButton(onClick = { approving = a.senderId }) { Text(stringResource(R.string.reapprove)) }
                    }
                    if (active) SwitchRow(stringResource(R.string.trust_format), stringResource(R.string.trust_format_sub), a.autoConfirm) { vm.setAutoConfirm(a.senderId, it) }
                }
            }
        }
        SectionCard(stringResource(R.string.discovered_senders), icon = AppIcons.sms) {
            val pending = s.discoveredSenders.filter { it.senderId !in approved || approved[it.senderId]?.revokedAt != null }
            if (pending.isEmpty()) Text(stringResource(R.string.none_yet))
            pending.forEach { d ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(Fmt.ltr(d.senderId)); Text(stringResource(R.string.messages_n, d.messageCount), style = MaterialTheme.typography.labelSmall) }
                    TextButton(onClick = { approving = d.senderId }) { Text(stringResource(R.string.approve)) }
                }
            }
        }
    }
    approving?.let { id -> ApproveSenderDialog(vm, id) { approving = null } }
}

@Composable
fun ApproveSenderDialog(vm: AppViewModel, senderId: String, onDone: () -> Unit) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    var bank by remember { mutableStateOf(s.banks.firstOrNull()) }
    var newBank by remember { mutableStateOf("") }
    var createNew by remember { mutableStateOf(s.banks.isEmpty()) }
    AlertDialog(onDismissRequest = onDone, title = { Text(stringResource(R.string.approve_sender_x, senderId)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.approve_sender_body), style = MaterialTheme.typography.bodySmall)
            if (s.banks.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(createNew, { createNew = it }); Text(stringResource(R.string.new_bank)) }
            if (createNew) OutlinedTextField(newBank, { newBank = it }, label = { Text(stringResource(R.string.bank_name)) }, singleLine = true)
            else Picker(stringResource(R.string.bank), s.banks, bank, { it.name }, { bank = it })
        }
    }, confirmButton = { TextButton(enabled = (createNew && newBank.isNotBlank()) || (!createNew && bank != null), onClick = {
        vm.approveSender(senderId, if (createNew) newBank else null, if (createNew) null else bank?.id); onDone() }) { Text(stringResource(R.string.approve)) } },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) } })
}

/** Profiles, cycles settings and accounts, including reassignment with an impact preview (§03). */
@Composable
fun ProfilesScreen(vm: AppViewModel) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    var editProfile by remember { mutableStateOf<Profile?>(null) }
    var newProfile by remember { mutableStateOf(false) }
    var newAccount by remember { mutableStateOf(false) }
    var reassign by remember { mutableStateOf<Account?>(null) }
    ScreenColumn {
        SectionCard(stringResource(R.string.profiles), icon = AppIcons.profile, action = { TextButton(onClick = { newProfile = true }) { Icon(AppIcons.add, null); Text(stringResource(R.string.add)) } }) {
            s.profiles.forEach { p -> SettingRow(AppIcons.profile, p.profile.name, cycleTypeLabel(p.profile.cycleType), onClick = { editProfile = p.profile }) }
            if (s.profiles.isEmpty()) Text(stringResource(R.string.no_profiles_body))
        }
        SectionCard(stringResource(R.string.accounts), icon = AppIcons.bank, action = { TextButton(onClick = { newAccount = true }, enabled = s.profiles.isNotEmpty() && s.banks.isNotEmpty()) { Icon(AppIcons.add, null); Text(stringResource(R.string.add)) } }) {
            if (s.banks.isEmpty()) Text(stringResource(R.string.approve_sender_first), style = MaterialTheme.typography.bodySmall)
            s.accounts.forEach { a -> SettingRow(AppIcons.bank, a.displayName, Fmt.ltr("••" + a.maskedNumber) + " · " + accountTypeLabel(a.type) + " · " + (s.profile(a.profileId)?.profile?.name ?: ""), onClick = { reassign = a }) }
        }
    }
    if (newProfile || editProfile != null) ProfileDialog(vm, editProfile) { newProfile = false; editProfile = null }
    if (newAccount) AccountDialog(vm) { newAccount = false }
    reassign?.let { a -> ReassignDialog(vm, a) { reassign = null } }
}

@Composable
fun ProfileDialog(vm: AppViewModel, existing: Profile?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.cycleType ?: CycleType.SALARY) }
    var renewal by remember { mutableStateOf(existing?.renewalMode ?: RenewalMode.MANUAL) }
    var every by remember { mutableStateOf(existing?.scheduledEveryDays?.toString() ?: "30") }
    var horizon by remember { mutableIntStateOf((existing?.defaultForecastHorizon as? ForecastHorizon.Days)?.days ?: 30) }
    var color by remember { mutableLongStateOf(ACCENT_SWATCHES[0]) }
    AlertDialog(onDismissRequest = onDone, title = { Text(stringResource(if (existing == null) R.string.new_profile else R.string.edit_profile)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
            Picker(stringResource(R.string.cycle_type), CycleType.entries, type, { cycleTypeLabel(it) }, { type = it })
            if (type != CycleType.SALARY) Picker(stringResource(R.string.renewal), RenewalMode.entries, renewal, { renewalLabel(it) }, { renewal = it })
            if (type == CycleType.SCHEDULED) OutlinedTextField(every, { every = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.every_days)) }, singleLine = true)
            Text(stringResource(R.string.default_horizon), style = MaterialTheme.typography.labelMedium)
            ChoiceRow(listOf(7, 14, 30, 60), horizon, { stringResource(R.string.days_n, it) }, { horizon = it })
            SwatchRow(ACCENT_SWATCHES.take(8), color) { color = it }
            Text(stringResource(R.string.cycle_rules_note), style = MaterialTheme.typography.labelSmall)
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
        val p = (existing ?: Profile(vm.newProfileId(), name)).copy(name = name, cycleType = type, renewalMode = renewal,
            scheduledEveryDays = every.toIntOrNull(), defaultForecastHorizon = ForecastHorizon.Days(horizon))
        vm.saveProfile(p, color, "person", horizon); onDone() }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun AccountDialog(vm: AppViewModel, onDone: () -> Unit) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }; var masked by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.CURRENT) }
    var bank by remember { mutableStateOf(s.banks.firstOrNull()) }
    var profile by remember { mutableStateOf(s.profiles.firstOrNull()) }
    AlertDialog(onDismissRequest = onDone, title = { Text(stringResource(R.string.new_account)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
            OutlinedTextField(masked, { masked = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.last_digits)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Picker(stringResource(R.string.account_type), AccountType.entries, type, { accountTypeLabel(it) }, { type = it })
            Picker(stringResource(R.string.bank), s.banks, bank, { it.name }, { bank = it })
            Picker(stringResource(R.string.profile), s.profiles, profile, { it.profile.name }, { profile = it })
            Text(stringResource(R.string.no_opening_balance_note), style = MaterialTheme.typography.labelSmall)
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank() && masked.length >= 3 && bank != null && profile != null, onClick = {
        vm.saveAccount(Account(UUID.randomUUID().toString(), bank!!.id, profile!!.profile.id, type, masked, name, isMain = type == AccountType.CURRENT)); vm.reprocess(); onDone() }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun ReassignDialog(vm: AppViewModel, a: Account, onDone: () -> Unit) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf(s.profiles.firstOrNull { it.profile.id != a.profileId }) }
    val from = remember { Instant.now() }
    val preview = target?.let { vm.previewReassign(a.id, it.profile.id, from) }
    AlertDialog(onDismissRequest = onDone, title = { Text(stringResource(R.string.move_account, a.displayName)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(stringResource(R.string.profile), s.profiles.filter { it.profile.id != a.profileId }, target, { it.profile.name }, { target = it })
            Text(stringResource(R.string.impact_preview), style = MaterialTheme.typography.titleSmall)
            preview?.let { (before, after) -> s.profiles.forEach { p ->
                val b = before[p.profile.id]; val af = after[p.profile.id]
                Text(p.profile.name + ": " + (b?.remaining?.let { money(it) } ?: "—") + " → " + (af?.remaining?.let { money(it) } ?: "—"), style = MaterialTheme.typography.bodySmall)
            } }
            Text(stringResource(R.string.reassign_note), style = MaterialTheme.typography.labelSmall)
        }
    }, confirmButton = { TextButton(enabled = target != null, onClick = { vm.reassignAccount(a.id, target!!.profile.id, from); onDone() }) { Text(stringResource(R.string.apply)) } },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) } })
}

/** Import original SMS backups and manage password-protected app backups (§04, §13). */
@Composable
fun DataScreen(vm: AppViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var pwFor by remember { mutableStateOf<Pair<Boolean, Uri>?>(null) }
    val importSms = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importSmsBackup) }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let { u -> pwFor = true to u } }
    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> pwFor = false to u } }
    ScreenColumn {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        SectionCard(stringResource(R.string.sms_sources), icon = AppIcons.sms) {
            SettingRow(AppIcons.sync, stringResource(R.string.sync_now), stringResource(R.string.sync_sub), onClick = { vm.syncDevice() })
            SettingRow(AppIcons.upload, stringResource(R.string.import_sms_backup), stringResource(R.string.import_sms_sub), onClick = { importSms.launch(arrayOf("text/xml", "application/xml", "*/*")) })
            SettingRow(AppIcons.review, stringResource(R.string.reprocess), stringResource(R.string.reprocess_sub), onClick = { vm.reprocess() })
        }
        SectionCard(stringResource(R.string.app_backup), icon = AppIcons.backup) {
            SettingRow(AppIcons.download, stringResource(R.string.export_backup), stringResource(R.string.export_backup_sub), onClick = { exportBackup.launch("budgetsms-backup.bsms") })
            SettingRow(AppIcons.upload, stringResource(R.string.restore_backup), stringResource(R.string.restore_backup_sub), onClick = { restoreBackup.launch(arrayOf("*/*")) })
            Text(stringResource(R.string.cloud_backup_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    pwFor?.let { (export, uri) ->
        var pw by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { pwFor = null }, title = { Text(stringResource(if (export) R.string.export_backup else R.string.restore_backup)) }, text = {
            OutlinedTextField(pw, { pw = it }, label = { Text(stringResource(R.string.backup_password)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true,
                supportingText = { Text(stringResource(R.string.backup_password_hint)) })
        }, confirmButton = { TextButton(enabled = pw.length >= 8, onClick = { if (export) vm.exportBackup(uri, pw) else vm.restoreBackup(uri, pw); pwFor = null }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { pwFor = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
fun AuditScreen(vm: AppViewModel) {
    val items by vm.audit.collectAsStateWithLifecycle()
    val locale = currentLocale()
    ScreenColumn {
        if (items.isEmpty()) EmptyState(AppIcons.history, stringResource(R.string.audit_log), stringResource(R.string.none_yet))
        items.forEach { a -> Column(Modifier.padding(vertical = 4.dp)) {
            Text(Fmt.ltr("${a.action} · ${a.entityType}"), style = MaterialTheme.typography.bodyMedium)
            Text(Fmt.dateTime(Instant.ofEpochMilli(a.at), locale), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
}

/** PDF / Excel export from the same accounting results, shared via FileProvider. */
@Composable
fun ReportsScreen(vm: AppViewModel) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val sel by vm.selectedProfile.collectAsStateWithLifecycle()
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val locale = currentLocale()
    var cycle by remember { mutableStateOf<com.budgetsms.domain.accounting.Cycle?>(null) }
    val cycles = s.ledger?.cycles.orEmpty().filter { sel == null || it.profileId == sel }.reversed()
    fun share(pdf: Boolean) = scope.launch {
        val f = vm.exportReport(pdf, sel, cycle?.id)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(if (pdf) "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
    }
    ScreenColumn {
        ProfileSelector(vm)
        SectionCard(stringResource(R.string.report_filters), icon = AppIcons.calendar) {
            Picker(stringResource(R.string.cycle), cycles, cycle, { Fmt.day(it.openedAt, locale) + " · " + cycleTypeLabel(it.type) }, { cycle = it }, allowNone = true, noneText = stringResource(R.string.open_cycles), onNone = { cycle = null })
        }
        SectionCard(stringResource(R.string.export), icon = AppIcons.reports) {
            Text(stringResource(R.string.export_note), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { share(true) }) { Icon(AppIcons.pdf, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.export_pdf)) }
                FilledTonalButton(onClick = { share(false) }) { Icon(AppIcons.table, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.export_excel)) }
            }
        }
    }
}
