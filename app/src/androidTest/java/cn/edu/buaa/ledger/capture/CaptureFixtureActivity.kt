package cn.edu.buaa.ledger.capture

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.widget.TextView

/** Test-APK-only target: a different application from the ledger, containing only fictional data. */
class CaptureFixtureActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (intent.getBooleanExtra("secure", false)) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(TextView(this).apply {
            // Only this debug fixture contains public fictional data; production windows retain system protection.
            if (android.os.Build.VERSION.SDK_INT >= 35) contentSensitivity = android.view.View.CONTENT_SENSITIVITY_NOT_SENSITIVE
            text = "虚构截图测试\n\n早餐店\n\n实际支付 ￥18.50\n\n支付时间 2026-11-12"
            textSize = 28f; setTextColor(Color.BLACK); setBackgroundColor(Color.WHITE); setPadding(40, 120, 40, 40)
        })
    }
}
