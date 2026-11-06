package com.budgetsms.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetsms.app.AppViewModel
import com.budgetsms.app.R
import com.budgetsms.app.ui.components.*
import com.budgetsms.app.ui.icons.AppIcons
import com.budgetsms.app.ui.theme.LocalSemantic
import com.budgetsms.domain.model.EventKind
import com.budgetsms.domain.money.Money
import com.budgetsms.domain.sms.SampleType
import com.budgetsms.domain.sms.TemplateLearner
import java.time.ZoneId

@Composable
fun sampleLabel(t: SampleType) = stringResource(when (t) {
    SampleType.PURCHASE -> R.string.sample_purchase; SampleType.SALARY -> R.string.sample_salary
    SampleType.CARD_REPAYMENT -> R.string.sample_card_repayment; SampleType.OUTGOING_TRANSFER -> R.string.sample_outgoing
    SampleType.INCOMING_TRANSFER -> R.string.sample_incoming; SampleType.BILL_PAYMENT -> R.string.sample_bill
})

private fun sampleIcon(t: SampleType): ImageVector = when (t) {
    SampleType.PURCHASE -> AppIcons.kind(EventKind.PURCHASE_DEBIT); SampleType.SALARY -> AppIcons.kind(EventKind.SALARY)
    SampleType.CARD_REPAYMENT -> AppIcons.kind(EventKind.CARD_REPAYMENT); SampleType.OUTGOING_TRANSFER -> AppIcons.kind(EventKind.OUTGOING_TRANSFER)
    SampleType.INCOMING_TRANSFER -> AppIcons.kind(EventKind.INCOMING_TRANSFER); SampleType.BILL_PAYMENT -> AppIcons.category("bolt")
}

/** The six sample slots with their status; used in onboarding (before SMS permission) and in More › Message samples. */
@Composable
fun SampleList(vm: AppViewModel) {
    val templates by vm.templates.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SampleType?>(null) }
    val sem = LocalSemantic.current
    SampleType.entries.forEach { type ->
        val mine = templates.filter { it.type == type }
        Card(onClick = { editing = type }, shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(sampleIcon(type), if (mine.isNotEmpty()) sem.income else MaterialTheme.colorScheme.primary, 40)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sampleLabel(type), style = MaterialTheme.typography.titleSmall)
                        Text(if (mine.isNotEmpty()) stringResource(R.string.sample_saved_count, mine.size) else stringResource(R.string.sample_add),
                            style = MaterialTheme.typography.bodySmall, color = if (mine.isNotEmpty()) sem.income else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (mine.isNotEmpty()) AppIcons.check else AppIcons.add, null, tint = if (mine.isNotEmpty()) sem.income else MaterialTheme.colorScheme.primary)
                }
                mine.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text("• " + t.firstLine.ifBlank { "—" }, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        IconButton(onClick = { vm.deleteSample(t.id) }) { Icon(AppIcons.close, stringResource(R.string.cancel), Modifier.size(18.dp)) }
                    }
                }
            }
        }
    }
    editing?.let { type -> SampleDialog(type, onDismiss = { editing = null }) { text, amount -> vm.saveSample(type, text, amount); editing = null } }
}

@Composable
private fun SampleDialog(type: SampleType, onDismiss: () -> Unit, onSave: (String, Money?) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var touched by remember { mutableStateOf(false) }
    LaunchedEffect(text) {
        if (!touched) amountText = TemplateLearner.detectAmount(text, ZoneId.systemDefault())?.toPlainString() ?: ""
    }
    val amount = Money.parseOrNull(amountText)
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(sampleIcon(type), null) }, title = { Text(sampleLabel(type)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text(stringResource(R.string.sample_paste_hint)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
            TextButton(onClick = { clipboard.getText()?.text?.let { text = it } }) { Icon(AppIcons.download, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.paste)) }
            OutlinedTextField(amountText, { amountText = it; touched = true }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.sample_amount)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), suffix = { Text(stringResource(R.string.currency_sar)) },
                isError = amountText.isNotEmpty() && amount == null, supportingText = { Text(stringResource(R.string.sample_amount_hint)) })
        }
    }, confirmButton = { TextButton(enabled = text.isNotBlank() && amount != null, onClick = { onSave(text, amount) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun SamplesScreen(vm: AppViewModel) {
    ScreenColumn {
        Text(stringResource(R.string.samples_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.samples_body), style = MaterialTheme.typography.bodyMedium)
        SampleList(vm)
    }
}
