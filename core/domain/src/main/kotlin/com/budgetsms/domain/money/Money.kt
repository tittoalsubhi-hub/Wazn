package com.budgetsms.domain.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Exact SAR amount stored as integer halalas (1 SAR = 100 halalas).
 * All arithmetic is overflow-checked. Float/Double are never used for money.
 */
@JvmInline
value class Money(val halalas: Long) : Comparable<Money> {
    operator fun plus(o: Money): Money = Money(Math.addExact(halalas, o.halalas))
    operator fun minus(o: Money): Money = Money(Math.subtractExact(halalas, o.halalas))
    operator fun unaryMinus(): Money = Money(Math.negateExact(halalas))
    operator fun times(n: Int): Money = Money(Math.multiplyExact(halalas, n.toLong()))
    override fun compareTo(other: Money): Int = halalas.compareTo(other.halalas)

    val isNegative get() = halalas < 0
    val isZero get() = halalas == 0L
    val isPositive get() = halalas > 0
    fun abs(): Money = if (halalas < 0) -this else this
    fun coerceAtLeast(min: Money) = if (this < min) min else this
    fun coerceAtMost(max: Money) = if (this > max) max else this

    /** Percentage of this amount using [PercentPolicy] (half-even, documented in docs/DECISIONS.md). */
    fun percent(bp: BasisPoints): Money = PercentPolicy.apply(this, bp)

    /** Plain invariant representation, e.g. "1234.50". Localised formatting lives in the UI layer. */
    fun toPlainString(): String {
        val neg = halalas < 0
        val a = if (neg) -(halalas.toBigInteger()) else halalas.toBigInteger()
        val s = BigDecimal(a, 2).toPlainString()
        return if (neg) "-$s" else s
    }

    override fun toString(): String = "SAR ${toPlainString()}"

    companion object {
        val ZERO = Money(0)
        fun sar(whole: Long, halalas: Long = 0): Money =
            Money(Math.addExact(Math.multiplyExact(whole, 100L), halalas))

        /** Parses an exact decimal; rejects more than two fraction digits rather than rounding silently. */
        fun parse(text: String): Money {
            val normalized = NumberInput.normalize(text)
            require(normalized.isNotEmpty()) { "empty amount" }
            val bd = BigDecimal(normalized)
            require(bd.scale() <= 2 || bd.stripTrailingZeros().scale() <= 2) { "more than two decimal places" }
            val minor = bd.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2)
            return Money(minor.longValueExact())
        }

        fun parseOrNull(text: String): Money? = runCatching { parse(text) }.getOrNull()
        fun sum(items: Iterable<Money>): Money = items.fold(ZERO) { a, b -> a + b }
    }
}

/** 1 basis point = 0.01 %. 10000 bp = 100 %. */
@JvmInline
value class BasisPoints(val value: Int) {
    init { require(value in 0..1_000_000) { "basis points out of range: $value" } }
    companion object {
        fun percent(p: Int) = BasisPoints(Math.multiplyExact(p, 100))
        fun parsePercent(text: String): BasisPoints {
            val bd = BigDecimal(NumberInput.normalize(text)).setScale(2, RoundingMode.UNNECESSARY)
            return BasisPoints(bd.movePointRight(2).intValueExact())
        }
    }
}

/**
 * Documented percentage-rounding policy: exact BigDecimal multiplication, rounded to the nearest
 * halala using HALF_EVEN (banker's rounding). Percentage limits never create money; they cap spending.
 */
object PercentPolicy {
    val MODE: RoundingMode = RoundingMode.HALF_EVEN
    fun apply(base: Money, bp: BasisPoints): Money {
        val r = BigDecimal.valueOf(base.halalas)
            .multiply(BigDecimal.valueOf(bp.value.toLong()))
            .divide(BigDecimal.valueOf(10_000), 0, MODE)
        return Money(r.longValueExact())
    }
}

/** Converts Arabic-Indic / Eastern Arabic-Indic digits and separators to ASCII for exact parsing. */
object NumberInput {
    private const val ARABIC_INDIC = '٠'
    private const val EXT_ARABIC_INDIC = '۰'

    fun normalize(input: String): String {
        val sb = StringBuilder()
        for (ch in input.trim()) {
            when {
                ch in ARABIC_INDIC..(ARABIC_INDIC + 9) -> sb.append('0' + (ch - ARABIC_INDIC))
                ch in EXT_ARABIC_INDIC..(EXT_ARABIC_INDIC + 9) -> sb.append('0' + (ch - EXT_ARABIC_INDIC))
                ch == '٫' -> sb.append('.')           // Arabic decimal separator
                ch == '٬' || ch == ',' || ch == '،' -> {} // thousands separators
                ch == Char(0x200F) || ch == Char(0x200E) || ch == Char(0x00A0) || ch == ' ' -> {}
                ch == '−' -> sb.append('-')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}

/** Explicit balance state: an unknown balance is never represented as zero. */
sealed interface Balance {
    data class Known(val amount: Money, val referenceAmount: Money, val referenceAt: Instant) : Balance
    data class Unknown(val knownNetMovement: Money) : Balance
}
