package com.budgetsms.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetsms.app.AppViewModel
import com.budgetsms.app.R
import com.budgetsms.app.ui.components.Fmt
import com.budgetsms.app.ui.components.IconBadge
import com.budgetsms.app.ui.components.MessageBanner
import com.budgetsms.app.ui.icons.AnimatedCheck
import com.budgetsms.app.ui.icons.AppIcons
import com.budgetsms.app.ui.icons.FloatingIcon
import com.budgetsms.app.ui.theme.appBackground
import com.budgetsms.domain.model.CycleType
import kotlinx.coroutines.launch

/**
 * Add another profile from the home page: name and cycle → pick the bank's SMS sender → samples again
 * (optional) → the new profile gets its own bank-level account and the sender's messages are imported into it.
 */
@Composable
fun AddProfileWizard(vm: AppViewModel, onDone: () -> Unit) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CycleType.SALARY) }
    var sender by remember { mutableStateOf<String?>(null) }
    var bankName by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> if (r[Manifest.permission.READ_SMS] == true) vm.discoverSenders() }
    LaunchedEffect(Unit) { vm.discoverSenders() }
    val active = s.approvedSenders.filter { it.revokedAt == null }.map { it.senderId }.toSet()
    val available = s.discoveredSenders.filter { it.senderId !in active }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().appBackground(), color = androidx.compose.ui.graphics.Color.Transparent, contentColor = cs.onBackground) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDone) { Icon(AppIcons.close, stringResource(R.string.cancel)) }
                    Text(stringResource(R.string.new_profile), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                LinearProgressIndicator(progress = { (step + 1) / 3f }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), drawStopIndicator = {})
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (step) {
                        0 -> {
                            Text(stringResource(R.string.profile_step_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.wizard_profile_body), style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.name)) }, singleLine = true)
                            Picker(stringResource(R.string.cycle_type), CycleType.entries, type, { cycleTypeLabel(it) }, { type = it })
                        }
                        1 -> {
                            Text(stringResource(R.string.wizard_bank_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.wizard_bank_body), style = MaterialTheme.typography.bodyMedium)
                            if (available.isEmpty()) OutlinedButton(onClick = { perms.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) }) {
                                Icon(AppIcons.sms, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.discover_senders)) }
                            available.take(40).forEach { d ->
                                Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { sender = d.senderId; if (bankName.isBlank()) bankName = d.senderId }
                                    .background(if (sender == d.senderId) cs.primaryContainer else cs.surfaceContainerLow).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(sender == d.senderId, { sender = d.senderId; if (bankName.isBlank()) bankName = d.senderId })
                                    Column(Modifier.weight(1f)) { Text(Fmt.ltr(d.senderId)); Text(stringResource(R.string.messages_n, d.messageCount), style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                            if (sender != null) OutlinedTextField(bankName, { bankName = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.bank_name)) }, singleLine = true)
                            MessageBanner(stringResource(R.string.wizard_sender_note), AppIcons.info, cs.primary)
                        }
                        else -> {
                            Text(stringResource(R.string.samples_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.samples_body), style = MaterialTheme.typography.bodyMedium)
                            SampleList(vm)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (step > 0) TextButton(onClick = { step-- }) { Text(stringResource(R.string.back)) } else Spacer(Modifier.width(1.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (step == 2) OutlinedButton(onClick = { vm.addProfileWithBank(name, type, sender!!, bankName); onDone() }) { Text(stringResource(R.string.samples_skip)) }
                        Button(enabled = when (step) { 0 -> name.isNotBlank(); 1 -> sender != null && bankName.isNotBlank(); else -> true }, onClick = {
                            if (step < 2) step++ else { vm.addProfileWithBank(name, type, sender!!, bankName); onDone() }
                        }) { Text(stringResource(if (step < 2) R.string.next else R.string.create)) }
                    }
                }
            }
        }
    }
}

/** Guided tour of the app's sections (Settings › Tour guide). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TourScreen(onFinish: () -> Unit) {
    data class Page(val icon: ImageVector, val title: Int, val body: Int)
    val pages = listOf(
        Page(AppIcons.home.first, R.string.tour_home_t, R.string.tour_home_b),
        Page(AppIcons.activity.first, R.string.tour_activity_t, R.string.tour_activity_b),
        Page(AppIcons.budget.first, R.string.tour_budget_t, R.string.tour_budget_b),
        Page(AppIcons.goals.first, R.string.tour_goals_t, R.string.tour_goals_b),
        Page(AppIcons.obligations, R.string.tour_obligations_t, R.string.tour_obligations_b),
        Page(AppIcons.sms, R.string.tour_samples_t, R.string.tour_samples_b),
        Page(AppIcons.profile, R.string.tour_profiles_t, R.string.tour_profiles_b),
        Page(AppIcons.palette, R.string.tour_appearance_t, R.string.tour_appearance_b),
        Page(AppIcons.security, R.string.tour_security_t, R.string.tour_security_b),
    )
    val state = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(state, Modifier.weight(1f)) { i ->
            val p = pages[i]
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(150.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer))), contentAlignment = Alignment.Center) {
                    FloatingIcon(p.icon, cs.primary, 72.dp)
                }
                Spacer(Modifier.height(24.dp))
                Text(stringResource(p.title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(p.body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                if (i == pages.lastIndex) { Spacer(Modifier.height(20.dp)); AnimatedCheck(size = 56.dp) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.indices.forEach { i -> Box(Modifier.size(if (i == state.currentPage) 22.dp else 8.dp, 8.dp).clip(CircleShape)
                    .background(if (i == state.currentPage) cs.primary else cs.outlineVariant)) }
            }
            Button(onClick = { if (state.currentPage < pages.lastIndex) scope.launch { state.animateScrollToPage(state.currentPage + 1) } else onFinish() }) {
                Text(stringResource(if (state.currentPage < pages.lastIndex) R.string.next else R.string.tour_done))
            }
        }
    }
}
