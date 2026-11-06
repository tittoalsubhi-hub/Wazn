package com.budgetsms.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetsms.app.AppViewModel
import com.budgetsms.app.R
import com.budgetsms.app.ui.components.*
import com.budgetsms.app.ui.icons.AppIcons
import com.budgetsms.app.ui.icons.PulseDot
import com.budgetsms.app.ui.theme.LocalSemantic
import com.budgetsms.domain.model.*
import com.budgetsms.domain.money.Money
import java.time.ZoneId

private enum class ActivityFilter(val label: Int) { ALL(R.string.filter_all), REVIEW(R.string.filter_review), EXPENSES(R.string.filter_expenses), INCOME(R.string.filter_income), TRANSFERS(R.string.filter_transfers) }

@Composable
fun ActivityScreen(vm: AppViewModel, onOpenEvent: (String) -> Unit) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val sel by vm.selectedProfile.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(ActivityFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    val locale = currentLocale(); val zone = ZoneId.systemDefault()
    val reviewIds = s.ledger?.reviews?.map { it.eventId }?.toSet().orEmpty()
    val events = s.events.filter { e -> e.state != EventState.IGNORED &&
        (sel == null || s.account(e.accountId)?.profileId == sel || e.accountId == null) &&
            when (filter) {
                ActivityFilter.ALL -> true
                ActivityFilter.REVIEW -> e.id in reviewIds
                ActivityFilter.EXPENSES -> e.kind.direction == EventKind.Direction.DEBIT || e.kind.direction == EventKind.Direction.CARD
                ActivityFilter.INCOME -> e.kind.direction == EventKind.Direction.CREDIT && e.counterpartyAccountId == null
                ActivityFilter.TRANSFERS -> e.kind == EventKind.OUTGOING_TRANSFER || e.kind == EventKind.INCOMING_TRANSFER
            } && (query.isBlank() || listOfNotNull(e.reference, e.counterpartyHint, e.originalAmountText, categoryNamePlain(s, e.categoryId, locale.language)).any { it.contains(query, true) })
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            ProfileSelector(vm)
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityFilter.entries.forEach { f -> FilterChip(filter == f, { filter = f }, { Text(stringResource(f.label) + if (f == ActivityFilter.REVIEW && reviewIds.isNotEmpty()) " (${reviewIds.size})" else "") }) }
            }
        }
        if (events.isEmpty()) {
            EmptyState(AppIcons.activity.first, stringResource(R.string.no_transactions), stringResource(R.string.no_transactions_body)) {
                OutlinedButton(onClick = { vm.syncDevice() }) { Text(stringResource(R.string.sync_now)) }
            }
            return@Column
        }
        val grouped = events.groupBy { it.executedAt.atZone(zone).toLocalDate() }.toSortedMap(compareByDescending { it })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            grouped.forEach { (day, list) ->
                item(key = "h$day") { Text(Fmt.date(day, locale), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) }
                items(list, key = { it.id }) { e -> EventRow(s, e, e.id in reviewIds) { onOpenEvent(e.id) } }
            }
        }
    }
}

private fun categoryNamePlain(s: com.budgetsms.data.AppSnapshot, id: String?, lang: String) = s.category(id)?.let { if (lang == "ar") it.nameAr else it.nameEn }

@Composable
private fun EventRow(s: com.budgetsms.data.AppSnapshot, e: BankEvent, needsReview: Boolean, onClick: () -> Unit) {
    val sem = LocalSemantic.current
    val color = kindColor(e.kind)
    ListItem(
        modifier = Modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick),
        leadingContent = { IconBadge(if (e.categoryId != null) com.budgetsms.app.ui.icons.AppIcons.category(s.category(e.categoryId)?.iconKey) else AppIcons.kind(e.kind), color, 40) },
        headlineContent = { Text(kindLabel(e.kind) + (e.categoryId?.let { " · " + categoryName(s, it) } ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (needsReview) { PulseDot(sem.review, 8.dp); Spacer(Modifier.width(6.dp)) }
                Text(stateLabel(e.state) + (s.account(e.accountId)?.let { " · " + it.displayName } ?: ""), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                val sign = when (e.kind.direction) { EventKind.Direction.CREDIT -> "+"; EventKind.Direction.DEBIT -> "−"; else -> "" }
                if (e.amount != null) Text(Fmt.ltr(sign) + money(e.amount!!), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), color = color, fontWeight = FontWeight.SemiBold)
                else Text(e.originalAmountText?.let { Fmt.ltr(it) } ?: stringResource(R.string.not_available), style = MaterialTheme.typography.labelMedium)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}
