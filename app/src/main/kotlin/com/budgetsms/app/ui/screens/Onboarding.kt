package com.budgetsms.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetsms.app.AppViewModel
import com.budgetsms.app.R
import com.budgetsms.app.ui.components.Fmt
import com.budgetsms.app.ui.components.IconBadge
import com.budgetsms.app.ui.components.MessageBanner
import com.budgetsms.app.ui.icons.AnimatedCheck
import com.budgetsms.app.ui.icons.AppIcons
import com.budgetsms.app.ui.icons.FloatingIcon
import com.budgetsms.app.ui.icons.ProgressRing
import com.budgetsms.app.ui.theme.LocalMotion
import com.budgetsms.app.ui.theme.appBackground
import com.budgetsms.domain.appearance.AnimationLevel
import com.budgetsms.domain.appearance.AppLanguage
import com.budgetsms.domain.appearance.Palette
import com.budgetsms.domain.appearance.ThemeMode
import com.budgetsms.domain.model.CycleType
import com.budgetsms.domain.model.Profile

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val pref by vm.appearance.collectAsStateWithLifecycle()
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val motion = LocalMotion.current
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize().appBackground(), color = Color.Transparent) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            LinearProgressIndicator(progress = { (step + 1) / 5f }, Modifier.fillMaxWidth().padding(16.dp), drawStopIndicator = {})
            AnimatedContent(step, Modifier.weight(1f), label = "step", transitionSpec = {
                if (motion == AnimationLevel.OFF) fadeIn() togetherWith fadeOut() else (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut()
            }) { st ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (st) {
                        0 -> {
                            Box(Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.extraLarge)
                                .background(Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer))), contentAlignment = Alignment.Center) {
                                ProgressRing(0.72f, Modifier.size(150.dp), cs.primary, cs.surface.copy(alpha = 0.5f), 14.dp) { FloatingIcon(AppIcons.goals.first, cs.primary, 64.dp) }
                            }
                            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge)
                            Feature(AppIcons.sms, R.string.feat_sms); Feature(AppIcons.security, R.string.feat_private); Feature(AppIcons.palette, R.string.feat_custom)
                        }
                        1 -> {
                            Text(stringResource(R.string.make_it_yours), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(AppLanguage.ARABIC to R.string.lang_ar, AppLanguage.ENGLISH to R.string.lang_en).forEach { (l, r) ->
                                    FilterChip(pref?.language == l, { vm.updateAppearance { it.copy(language = l) } }, { Text(stringResource(r)) }) }
                            }
                            Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(ThemeMode.SYSTEM to R.string.mode_system, ThemeMode.LIGHT to R.string.mode_light, ThemeMode.DARK to R.string.mode_dark).forEach { (m, r) ->
                                    FilterChip(pref?.mode == m, { vm.updateAppearance { it.copy(mode = m) } }, { Text(stringResource(r)) }) }
                            }
                            Text(stringResource(R.string.color_palette), style = MaterialTheme.typography.titleSmall)
                            Palette.entries.filter { it != Palette.CUSTOM }.chunked(5).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { p ->
                                    val sel = pref?.palette == p
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { vm.updateAppearance { it.copy(palette = p, dynamicColor = false) } }) {
                                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(p.seed)).border(if (sel) 3.dp else 0.dp, cs.onSurface, CircleShape), contentAlignment = Alignment.Center) {
                                            if (sel) Icon(AppIcons.check, null, tint = Color.White) }
                                        Text(paletteName(p), style = MaterialTheme.typography.labelSmall)
                                    }
                                } }
                            }
                            Text(stringResource(R.string.more_in_settings), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                        2 -> SmsStep(vm)
                        3 -> ProfileStep(vm)
                        else -> {
                            Spacer(Modifier.height(40.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AnimatedCheck(size = 120.dp) }
                            Text(stringResource(R.string.all_set), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.all_set_body), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (step > 0) TextButton(onClick = { step-- }) { Text(stringResource(R.string.back)) } else Spacer(Modifier.width(1.dp))
                Button(onClick = { if (step < 4) step++ else vm.completeOnboarding() }, enabled = step != 3 || s.profiles.isNotEmpty()) {
                    Text(stringResource(if (step < 4) R.string.next else R.string.start))
                }
            }
        }
    }
}

@Composable
private fun Feature(icon: androidx.compose.ui.graphics.vector.ImageVector, text: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) { IconBadge(icon, MaterialTheme.colorScheme.primary, 40); Spacer(Modifier.width(12.dp)); Text(stringResource(text), style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun SmsStep(vm: AppViewModel) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    var approving by remember { mutableStateOf<String?>(null) }
    var denied by remember { mutableStateOf(false) }
    val perms = buildList { add(Manifest.permission.READ_SMS); add(Manifest.permission.RECEIVE_SMS); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.READ_SMS] == true) vm.discoverSenders() else denied = true
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importSmsBackup) }
    Text(stringResource(R.string.sms_step_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.sms_step_body), style = MaterialTheme.typography.bodyMedium)
    MessageBanner(stringResource(R.string.senders_explain), AppIcons.info, MaterialTheme.colorScheme.primary)
    Button(onClick = { launcher.launch(perms.toTypedArray()) }, Modifier.fillMaxWidth()) { Icon(AppIcons.sms, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.allow_sms)) }
    OutlinedButton(onClick = { importer.launch(arrayOf("text/xml", "application/xml", "*/*")) }, Modifier.fillMaxWidth()) { Icon(AppIcons.upload, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.import_sms_backup)) }
    if (denied) MessageBanner(stringResource(R.string.permission_denied), AppIcons.warning, MaterialTheme.colorScheme.error)
    val approved = s.approvedSenders.filter { it.revokedAt == null }.map { it.senderId }.toSet()
    s.discoveredSenders.take(30).forEach { d ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(Fmt.ltr(d.senderId)); Text(stringResource(R.string.messages_n, d.messageCount), style = MaterialTheme.typography.labelSmall) }
            if (d.senderId in approved) Icon(AppIcons.check, stringResource(R.string.active), tint = MaterialTheme.colorScheme.primary)
            else TextButton(onClick = { approving = d.senderId }) { Text(stringResource(R.string.approve)) }
        }
    }
    approving?.let { ApproveSenderDialog(vm, it) { approving = null } }
}

@Composable
private fun ProfileStep(vm: AppViewModel) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CycleType.SALARY) }
    var addAccount by remember { mutableStateOf(false) }
    Text(stringResource(R.string.profile_step_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.profile_step_body), style = MaterialTheme.typography.bodyMedium)
    if (s.profiles.isEmpty()) {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.name)) }, singleLine = true)
        Picker(stringResource(R.string.cycle_type), CycleType.entries, type, { cycleTypeLabel(it) }, { type = it })
        Button(onClick = { vm.saveProfile(Profile(vm.newProfileId(), name, type), 0xFF0F8A8F, "person") }, enabled = name.isNotBlank()) { Text(stringResource(R.string.create)) }
    } else s.profiles.forEach { p -> Row(verticalAlignment = Alignment.CenterVertically) { Icon(AppIcons.check, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text(p.profile.name + " · " + cycleTypeLabel(p.profile.cycleType)) } }
    HorizontalDivider()
    Text(stringResource(R.string.accounts), style = MaterialTheme.typography.titleSmall)
    s.accounts.forEach { a -> Text("• " + a.displayName + " " + Fmt.ltr("••" + a.maskedNumber)) }
    OutlinedButton(onClick = { addAccount = true }, enabled = s.profiles.isNotEmpty() && s.banks.isNotEmpty()) { Icon(AppIcons.add, null); Text(stringResource(R.string.new_account)) }
    if (s.banks.isEmpty()) Text(stringResource(R.string.approve_sender_first), style = MaterialTheme.typography.labelSmall)
    if (addAccount) AccountDialog(vm) { addAccount = false }
}
