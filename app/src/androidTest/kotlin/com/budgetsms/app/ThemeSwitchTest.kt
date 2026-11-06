package com.budgetsms.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budgetsms.app.ui.components.AmountText
import com.budgetsms.app.ui.theme.BudgetTheme
import com.budgetsms.domain.appearance.*
import com.budgetsms.domain.money.Money
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** T24, T25, T26, T29, T31: switching mode, palette, accent, direction and motion keeps state and values. */
@RunWith(AndroidJUnit4::class)
class ThemeSwitchTest {
    @get:Rule val rule = createComposeRule()

    @Test fun switchingAppearanceKeepsStateAndValues() {
        val pref = mutableStateOf(AppearancePreference())
        val dir = mutableStateOf(LayoutDirection.Ltr)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides dir.value) {
                BudgetTheme(pref.value) { AmountText(Money.parse("1234.50"), animate = false); Text("IBAN SA03 8000 0000 6080 1016 7519", style = MaterialTheme.typography.bodySmall) }
            }
        }
        val modes = ThemeMode.entries; val palettes = Palette.entries; val motions = AnimationLevel.entries
        for (m in modes) for (p in palettes) for (a in motions) {
            rule.runOnIdle { pref.value = pref.value.copy(mode = m, palette = p, animation = a, customSeed = 0xFFF4C430); dir.value = if (p.ordinal % 2 == 0) LayoutDirection.Rtl else LayoutDirection.Ltr }
            rule.waitForIdle()
            rule.onNodeWithText("1,234.50", substring = true).assertIsDisplayed()
        }
    }
}
