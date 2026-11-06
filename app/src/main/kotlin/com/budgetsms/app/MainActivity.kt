package com.budgetsms.app

import android.graphics.Color as AColor
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetsms.app.ui.AppRoot
import com.budgetsms.app.ui.icons.AppIcons
import com.budgetsms.app.ui.theme.BudgetTheme
import com.budgetsms.app.ui.theme.isDark
import com.budgetsms.data.SettingKeys
import com.budgetsms.data.db.DatabaseUnavailableException
import com.budgetsms.domain.appearance.AppLanguage
import com.budgetsms.domain.appearance.AppearancePreference

class MainActivity : AppCompatActivity() {
    private val app get() = application as BudgetApp
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val error = app.startupError
        if (app.container == null) {
            enableEdgeToEdge()
            setContent { BudgetTheme(AppearancePreference()) { StartupError(error) } }
            return
        }
        splash.setKeepOnScreenCondition { vm.appearance.value == null }
        setContent {
            val pref by vm.appearance.collectAsStateWithLifecycle()
            val p = pref ?: return@setContent
            val snapshot by vm.snapshot.collectAsStateWithLifecycle()
            val dark = isDark(p)
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(AColor.TRANSPARENT, AColor.TRANSPARENT) { dark },
                )
            }
            LaunchedEffect(p.language) { applyLanguage(p.language) }
            val secure = snapshot.settings[SettingKeys.SECURE_SCREEN] == "true"
            LaunchedEffect(secure) {
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            BudgetTheme(p) { AppRoot(vm, p) }
        }
    }

    private fun applyLanguage(lang: AppLanguage) {
        val wanted = if (lang == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(lang.tag)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != wanted.toLanguageTags()) AppCompatDelegate.setApplicationLocales(wanted)
    }

    override fun onStop() { super.onStop(); if (app.container != null) vm.backgroundedAt = SystemClock.elapsedRealtime() }

    override fun onStart() {
        super.onStart()
        if (app.container != null && vm.backgroundedAt > 0 && SystemClock.elapsedRealtime() - vm.backgroundedAt > 30_000) vm.lockIfNeeded()
    }
}

/** Shown when the encrypted store cannot be opened. Nothing is deleted or recreated. */
@Composable
private fun StartupError(e: DatabaseUnavailableException?) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(AppIcons.lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.db_error_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.db_error_body), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(e?.reason?.name ?: "", style = MaterialTheme.typography.labelSmall)
        }
    }
}
