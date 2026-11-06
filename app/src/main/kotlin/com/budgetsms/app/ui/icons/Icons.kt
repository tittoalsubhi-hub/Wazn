package com.budgetsms.app.ui.icons

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.budgetsms.app.ui.theme.LocalMotion
import com.budgetsms.domain.appearance.AnimationLevel
import com.budgetsms.domain.model.EventKind

/** One local icon family (Material Symbols Rounded, bundled in the APK; works offline). */
object AppIcons {
    val categoryKeys = listOf("groceries", "restaurant", "car", "bolt", "home", "health", "bag", "school", "family", "movie",
        "flight", "cash", "send", "fee", "card", "category", "pets", "gift", "sports", "phone", "coffee", "fuel", "charity", "baby")

    fun category(key: String?): ImageVector = when (key) {
        "groceries" -> Icons.Rounded.LocalGroceryStore; "restaurant" -> Icons.Rounded.Restaurant; "car" -> Icons.Rounded.DirectionsCar
        "bolt" -> Icons.Rounded.Bolt; "home" -> Icons.Rounded.Home; "health" -> Icons.Rounded.LocalHospital
        "bag" -> Icons.Rounded.ShoppingBag; "school" -> Icons.Rounded.School; "family" -> Icons.Rounded.FamilyRestroom
        "movie" -> Icons.Rounded.Movie; "flight" -> Icons.Rounded.Flight; "cash" -> Icons.Rounded.Payments
        "send" -> Icons.AutoMirrored.Rounded.Send; "fee" -> Icons.AutoMirrored.Rounded.ReceiptLong; "card" -> Icons.Rounded.CreditCard
        "pets" -> Icons.Rounded.Pets; "gift" -> Icons.Rounded.CardGiftcard; "sports" -> Icons.Rounded.FitnessCenter
        "phone" -> Icons.Rounded.PhoneAndroid; "coffee" -> Icons.Rounded.LocalCafe; "fuel" -> Icons.Rounded.LocalGasStation
        "charity" -> Icons.Rounded.VolunteerActivism; "baby" -> Icons.Rounded.ChildCare
        else -> Icons.Rounded.Category
    }

    fun kind(k: EventKind): ImageVector = when (k) {
        EventKind.SALARY -> Icons.Rounded.Work; EventKind.ALLOWANCE -> Icons.Rounded.AddCard
        EventKind.INCOMING_TRANSFER -> Icons.AutoMirrored.Rounded.CallReceived; EventKind.REFUND -> Icons.Rounded.Replay
        EventKind.PURCHASE_DEBIT -> Icons.Rounded.ShoppingCart; EventKind.PURCHASE_CREDIT_CARD -> Icons.Rounded.CreditCard
        EventKind.ATM_WITHDRAWAL -> Icons.Rounded.LocalAtm; EventKind.OUTGOING_TRANSFER -> Icons.AutoMirrored.Rounded.CallMade
        EventKind.CARD_REPAYMENT -> Icons.Rounded.CreditScore; EventKind.FEE -> Icons.AutoMirrored.Rounded.ReceiptLong
        EventKind.REVERSAL -> Icons.AutoMirrored.Rounded.Undo; EventKind.FAILED -> Icons.Rounded.ErrorOutline
        EventKind.BALANCE_ONLY -> Icons.Rounded.AccountBalanceWallet; EventKind.UNKNOWN -> Icons.AutoMirrored.Rounded.HelpOutline
    }

    val home = Icons.Rounded.SpaceDashboard to Icons.Outlined.SpaceDashboard
    val activity = Icons.AutoMirrored.Rounded.ReceiptLong to Icons.AutoMirrored.Outlined.ReceiptLong
    val budget = Icons.Rounded.DonutLarge to Icons.Outlined.DonutLarge
    val goals = RiyalSymbol to RiyalSymbol
    val more = Icons.Rounded.GridView to Icons.Outlined.GridView
    val bank = Icons.Rounded.AccountBalance; val profile = Icons.Rounded.Person; val obligations = Icons.Rounded.EventRepeat
    val forecast = Icons.AutoMirrored.Rounded.TrendingUp; val reports = Icons.Rounded.Assessment; val security = Icons.Rounded.Shield
    val settings = Icons.Rounded.Settings; val palette = Icons.Rounded.Palette; val language = Icons.Rounded.Translate
    val senders = Icons.Rounded.MarkEmailRead; val backup = Icons.Rounded.CloudSync; val review = Icons.Rounded.FactCheck
    val transfer = Icons.Rounded.SyncAlt; val lock = Icons.Rounded.Lock; val fingerprint = Icons.Rounded.Fingerprint
    val add = Icons.Rounded.Add; val check = Icons.Rounded.CheckCircle; val warning = Icons.Rounded.WarningAmber
    val info = Icons.Rounded.Info; val hidden = Icons.Rounded.VisibilityOff; val visible = Icons.Rounded.Visibility
    val sync = Icons.Rounded.Sync; val bell = Icons.Rounded.Notifications; val calendar = Icons.Rounded.CalendarMonth
    val upload = Icons.Rounded.FileUpload; val download = Icons.Rounded.FileDownload; val pdf = Icons.Rounded.PictureAsPdf
    val table = Icons.Rounded.TableChart; val pause = Icons.Rounded.PauseCircle; val play = Icons.Rounded.PlayCircle
    val stop = Icons.Rounded.StopCircle; val edit = Icons.Rounded.Edit; val close = Icons.Rounded.Close
    val darkMode = Icons.Rounded.DarkMode; val lightMode = Icons.Rounded.LightMode; val auto = Icons.Rounded.BrightnessAuto
    val motion = Icons.Rounded.Animation; val contrast = Icons.Rounded.Contrast; val corners = Icons.Rounded.RoundedCorner
    val wallpaper = Icons.Rounded.Wallpaper; val sms = Icons.Rounded.Sms; val history = Icons.Rounded.History
    val delete = Icons.Rounded.DeleteOutline; val tour = Icons.Rounded.Explore; val addProfile = Icons.Rounded.PersonAdd
    val chevron = Icons.AutoMirrored.Rounded.KeyboardArrowRight; val back = Icons.AutoMirrored.Rounded.ArrowBack
}

private val motion @Composable get() = LocalMotion.current

/** Bottom-navigation icon: bouncy scale + filled/outlined swap; static swap when motion is off. */
@Composable
fun AnimatedNavIcon(selected: Boolean, icons: Pair<ImageVector, ImageVector>, contentDescription: String?) {
    val m = motion
    val scale by animateFloatAsState(
        targetValue = if (selected && m != AnimationLevel.OFF) 1.12f else 1f,
        animationSpec = if (m == AnimationLevel.FULL) spring(dampingRatio = 0.42f, stiffness = 420f) else snap(), label = "nav",
    )
    Icon(if (selected) icons.first else icons.second, contentDescription, Modifier.scale(scale))
}

/** Success confirmation: ring grows, then the check mark is drawn. Static final frame when motion is off. */
@Composable
fun AnimatedCheck(modifier: Modifier = Modifier, size: Dp = 64.dp, color: Color = MaterialTheme.colorScheme.primary, play: Boolean = true) {
    val m = motion
    val progress = remember { Animatable(if (m == AnimationLevel.OFF || !play) 1f else 0f) }
    LaunchedEffect(play, m) { if (play && m != AnimationLevel.OFF) { progress.snapTo(0f); progress.animateTo(1f, tween(if (m == AnimationLevel.FULL) 600 else 250, easing = FastOutSlowInEasing)) } }
    Canvas(modifier.size(size)) {
        val p = progress.value
        val stroke = this.size.minDimension * 0.08f
        drawArc(color, -90f, 360f * (p * 1.6f).coerceAtMost(1f), false, style = Stroke(stroke, cap = StrokeCap.Round))
        val w = this.size.width; val h = this.size.height
        val path = Path().apply { moveTo(w * 0.28f, h * 0.52f); lineTo(w * 0.44f, h * 0.68f); lineTo(w * 0.74f, h * 0.36f) }
        val pm = PathMeasure().apply { setPath(path, false) }
        val seg = Path(); val t = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
        pm.getSegment(0f, pm.length * t, seg, true)
        drawPath(seg, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Sync / import indicator: rotates only while real work is in progress (no idle loops). */
@Composable
fun SyncIcon(active: Boolean, contentDescription: String?, tint: Color = LocalContentColor.current) {
    val m = motion
    val angle = if (active && m != AnimationLevel.OFF) {
        val inf = rememberInfiniteTransition(label = "sync")
        inf.animateFloat(0f, -360f, infiniteRepeatable(tween(if (m == AnimationLevel.FULL) 900 else 1400, easing = LinearEasing)), label = "a").value
    } else 0f
    Icon(AppIcons.sync, contentDescription, Modifier.rotate(angle), tint = tint)
}

/** Alert bell that shakes once when new alerts arrive. */
@Composable
fun AlertBell(count: Int, contentDescription: String?) {
    val m = motion
    val rot = remember { Animatable(0f) }
    var last by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count > last && m != AnimationLevel.OFF) {
            val amp = if (m == AnimationLevel.FULL) 18f else 8f
            for (a in listOf(amp, -amp, amp * 0.6f, -amp * 0.6f, 0f)) rot.animateTo(a, tween(70))
        }
        last = count
    }
    Box(contentAlignment = Alignment.TopEnd) {
        Icon(AppIcons.bell, contentDescription, Modifier.graphicsLayer { rotationZ = rot.value })
        if (count > 0) Canvas(Modifier.size(9.dp)) { drawCircle(Color(0xFFE53935)) }
    }
}

/** Ring progress with animated sweep; used for budgets and savings goals. */
@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, color: Color, track: Color, stroke: Dp = 10.dp, content: @Composable () -> Unit = {}) {
    val m = motion
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), if (m == AnimationLevel.OFF) snap() else tween(if (m == AnimationLevel.FULL) 900 else 300, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val s = stroke.toPx(); val inset = s / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - s, size.height - s)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(s))
            drawArc(color, -90f, 360f * p, false, Offset(inset, inset), arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Gentle floating motion for empty states; decorative, so disabled for Reduced and Off. */
@Composable
fun FloatingIcon(icon: ImageVector, tint: Color, size: Dp = 56.dp) {
    val m = motion
    val dy = if (m == AnimationLevel.FULL) rememberInfiniteTransition(label = "float")
        .animateFloat(-4f, 4f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dy").value else 0f
    Icon(icon, null, Modifier.size(size).graphicsLayer { translationY = dy * density }, tint = tint)
}

/** Small pulsing dot drawing attention to items awaiting review. */
@Composable
fun PulseDot(color: Color, size: Dp = 10.dp) {
    val m = motion
    val a = if (m == AnimationLevel.FULL) rememberInfiniteTransition(label = "pulse")
        .animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a").value else 1f
    Canvas(Modifier.size(size)) { drawCircle(color.copy(alpha = a)) }
}
