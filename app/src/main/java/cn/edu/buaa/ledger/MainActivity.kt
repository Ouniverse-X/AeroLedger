package cn.edu.buaa.ledger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import cn.edu.buaa.ledger.ui.*

class MainActivity : ComponentActivity() {
    private val model: LedgerViewModel by viewModels()
    private var openedPlan by mutableStateOf<Long?>(null)
    override fun attachBaseContext(base:android.content.Context) {
        val config=android.content.res.Configuration(base.resources.configuration)
        config.setLocale(java.util.Locale.SIMPLIFIED_CHINESE)
        super.attachBaseContext(base.createConfigurationContext(config))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        if(savedInstanceState==null && intent.hasExtra("open_plan")) openedPlan=intent.getLongExtra("open_plan",-1)
        setContent { LedgerRoot(model,openedPlan,{openedPlan=null}, {dark->
            WindowCompat.getInsetsController(window,window.decorView).apply {
                isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark
            }
        }) }
    }
    override fun onNewIntent(intent:Intent) {super.onNewIntent(intent);setIntent(intent);if(intent.hasExtra("open_plan")) openedPlan=intent.getLongExtra("open_plan",-1)}
    override fun onResume() {super.onResume();if(model.data.value.ready) model.restoreReminders()}
}
