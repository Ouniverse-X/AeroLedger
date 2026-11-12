package cn.edu.buaa.ledger

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import cn.edu.buaa.ledger.capture.CaptureActivity
import cn.edu.buaa.ledger.capture.CaptureStore
import cn.edu.buaa.ledger.capture.CaptureFixtureActivity
import cn.edu.buaa.ledger.recognition.ChineseOcrEngine
import cn.edu.buaa.ledger.recognition.ReceiptImageReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CaptureDeviceTests {
    private val app get() = ApplicationProvider.getApplicationContext<LedgerApp>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun startConsent() {
        val fixture = InstrumentationRegistry.getInstrumentation().context
        fixture.startActivity(Intent(fixture, CaptureFixtureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.textContains("虚构截图测试")), 5000))
        device.executeShellCommand("cmd statusbar add-tile cn.edu.buaa.ledger/.capture.CaptureTileService")
        device.openQuickSettings()
        // Shell add-tile returns before the first TileService binding completes.
        Thread.sleep(1000)
        device.executeShellCommand("cmd statusbar click-tile cn.edu.buaa.ledger/.capture.CaptureTileService")
        requireNotNull(device.wait(Until.findObject(By.text("继续授权")), 10000)) { "Tile did not open capture consent" }.click()
    }
    @Test fun declinedConsentDoesNotCreateImageOrEntry() = runBlocking {
        app.ensureInitialized()
        val before = app.repository.dao.transactions().map { it.id }.toSet()
        CaptureStore(app).clear()
        startConsent()
        device.wait(Until.findObject(By.res("android", "button2")), 5000)!!.click()
        Thread.sleep(500)
        assertNull(CaptureStore(app).result())
        assertEquals(before, app.repository.dao.transactions().map { it.id }.toSet())
    }
    @Test fun authorizedSingleFrameReturnsTargetAndReleasesService() = captureTarget()
    @Test fun rotationKeepsSingleSessionAndReleasesService() = captureTarget(rotate = true)
    @Test fun fullScreenCaptureReturnsOtherApp() = captureTarget(fullScreen = true)
    private fun captureTarget(rotate: Boolean = false, fullScreen: Boolean = false) = runBlocking {
        app.ensureInitialized()
        val before = app.repository.dao.transactions().map { it.id }.toSet()
        CaptureStore(app).clear()
        startConsent()
        // Exercise Android 14+ explicit app sharing; choose only the fictional debug task.
        val spinner = device.wait(Until.findObject(By.res("com.android.systemui", "screen_share_mode_spinner")), 3000)
        if (spinner != null) {
            spinner.click()
            val single = device.wait(Until.findObject(By.textContains(if (fullScreen) "Entire screen" else "A single app")), 3000)
                ?: device.findObject(By.textContains(if (fullScreen) "整个屏幕" else "单个应用"))
            requireNotNull(single) { "Unrecognized capture mode UI" }.click()
        }
        device.wait(Until.findObject(By.res("android", "button1")), 5000)!!.click()
        if (!fullScreen) {
            val target = device.wait(Until.findObject(By.descContains("虚构账单测试")), 5000)
                ?: device.findObject(By.text("虚构账单测试"))
            requireNotNull(target) { "Fictional target missing from system task picker" }.click()
        }
        assertTrue("Target activity must be visible after consent", device.wait(Until.hasObject(By.textContains("虚构截图测试")), 5000))
        if (rotate) device.setOrientationLeft()
        val deadline = System.currentTimeMillis() + 20000
        while (CaptureStore(app).result() == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
        val uri = requireNotNull(CaptureStore(app).result()) { CaptureStore(app).message().orEmpty() }
        if (rotate) { device.setOrientationNatural(); device.unfreezeRotation() }
        val image = ReceiptImageReader(app).read(uri)
        val text = try { ChineseOcrEngine().use { it.recognize(image).joinToString(" ") { line -> line.text } } } finally { image.recycle() }
        assertTrue(text, text.contains("18.50"))
        val services = device.executeShellCommand("dumpsys activity services cn.edu.buaa.ledger")
        assertFalse(services, services.contains("capture.CaptureService"))
        assertEquals(before, app.repository.dao.transactions().map { it.id }.toSet())
        CaptureStore(app).clear()
    }
}
