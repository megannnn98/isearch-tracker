package dev.isearch.tracker

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Minimal UI: pick the fob, see connection status, toggle "find phone", test the sounds. */
@SuppressLint("MissingPermission", "UseSwitchCompatOrMaterialCode")
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var fobText: TextView
    private val scanner by lazy { BleScanner(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var pendingAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        status = TextView(this).apply { textSize = 20f }
        fobText = TextView(this)
        root.addView(status)
        root.addView(fobText)
        root.addView(button("Подключить брелок") { withPermissions { pickFob() } })
        root.addView(Switch(this).apply {
            text = "Поиск телефона по двойному нажатию"
            isChecked = prefs.findPhoneEnabled
            setOnCheckedChangeListener { _, checked -> prefs.findPhoneEnabled = checked }
        })
        root.addView(button("Проверить сигнал на телефоне (вкл/выкл)") { sendToService(TrackerService.ACTION_TOGGLE_TEST_ALARM) })
        root.addView(button("Остановить сигнал на телефоне") { sendToService(TrackerService.ACTION_STOP_ALARM) })
        root.addView(button("Сигнал на брелоке (вкл/выкл)") { sendToService(TrackerService.ACTION_TOGGLE_FOB_ALERT) })
        root.addView(button("Отключить брелок") { forgetFob() })
        root.addView(button("Настройки батареи") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        setContentView(ScrollView(this).apply { addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) })
    }

    override fun onResume() {
        super.onResume()
        TrackerStatus.listener = ::render
        render()
        if (prefs.serviceEnabled && prefs.mac != null && Permissions.hasBluetooth(this)) {
            TrackerService.start(this)
        }
    }

    override fun onPause() {
        TrackerStatus.listener = null
        scanner.stop()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val action = pendingAction
        pendingAction = null
        if (Permissions.hasBluetooth(this)) {
            action?.invoke()
        } else if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            toast("Нужны разрешения Bluetooth")
        }
    }

    private fun render() {
        status.text = TrackerService.describe(TrackerStatus.state) +
            if (TrackerStatus.alarmPlaying) " · сигнал звучит" else ""
        fobText.text = prefs.mac?.let { "Брелок: $it" } ?: "Брелок не выбран"
    }

    private fun withPermissions(action: () -> Unit) {
        if (Permissions.hasBluetooth(this)) {
            action()
            return
        }
        pendingAction = action
        requestPermissions(Permissions.all(), REQUEST_PERMISSIONS)
    }

    private fun pickFob() {
        val found = LinkedHashMap<String, String>()
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Нажмите кнопку на брелоке")
            .setAdapter(adapter) { _, which ->
                scanner.stop()
                selectFob(found.keys.elementAt(which))
            }
            .setNegativeButton("Отмена", null)
            .setOnDismissListener { scanner.stop() }
            .create()
        val started = scanner.start { device: BluetoothDevice, name: String?, rssi: Int ->
            runOnUiThread {
                val label = "${name ?: "без имени"}  ${device.address}  $rssi dBm"
                if (found.put(device.address, label) == null) {
                    adapter.add(label)
                } else {
                    adapter.clear()
                    adapter.addAll(found.values)
                }
            }
        }
        if (!started) {
            toast("Не удалось начать поиск. Включите Bluetooth.")
            return
        }
        dialog.show()
        handler.postDelayed({ scanner.stop() }, SCAN_TIMEOUT_MS)
    }

    private fun selectFob(mac: String) {
        prefs.mac = mac
        prefs.serviceEnabled = true
        TrackerService.start(this)
        render()
    }

    private fun forgetFob() {
        prefs.serviceEnabled = false
        prefs.mac = null
        stopService(Intent(this, TrackerService::class.java))
        render()
    }

    private fun sendToService(action: String) {
        if (prefs.mac == null) {
            toast("Сначала подключите брелок")
            return
        }
        withPermissions { TrackerService.start(this, action) }
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private companion object {
        const val REQUEST_PERMISSIONS = 1
        const val SCAN_TIMEOUT_MS = 30_000L
    }
}
