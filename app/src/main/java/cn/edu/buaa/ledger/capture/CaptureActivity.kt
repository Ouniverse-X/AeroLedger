package cn.edu.buaa.ledger.capture

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/** Separate task returns to the originating app after consent, instead of capturing our own editor. */
class CaptureActivity : ComponentActivity() {
    private var authorizationPending = false
    private val consent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            try {
                ContextCompat.startForegroundService(this, Intent(this, CaptureService::class.java)
                    .putExtra("consent", result.data).putExtra("resultCode", result.resultCode))
            } catch (failure: Exception) { CaptureStore(this).failure("无法启动截图，请使用相册或分享入口。") }
        } else CaptureStore(this).failure("已取消截图授权，没有新增账目。")
        finishAndRemoveTask()
    }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { requestCapture() }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        authorizationPending = state?.getBoolean("authorizationPending") ?: false
        if (!authorizationPending) AlertDialog.Builder(this).setTitle("截取一张账单")
            .setMessage("请先打开目标账单。授权后约 2 秒截取一帧，随后停止捕获。Android 14 及以上可选择目标应用。\n\n完成后点击通知，或回到图片记账页点击‘打开最近截图’。拒绝通知权限时仍可从页面打开结果。")
            .setNegativeButton("取消") { _, _ -> finishAndRemoveTask() }
            .setPositiveButton("继续授权") { _, _ ->
                authorizationPending = true
                if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else requestCapture()
            }.setOnCancelListener { finishAndRemoveTask() }.show()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("authorizationPending", authorizationPending)
        super.onSaveInstanceState(outState)
    }
    private fun requestCapture() {
        authorizationPending = true
        consent.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }
}
