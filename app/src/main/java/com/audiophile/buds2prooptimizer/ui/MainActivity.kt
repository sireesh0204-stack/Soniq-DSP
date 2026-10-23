package com.audiophile.buds2prooptimizer.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothCodecStatus
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.audiophile.buds2prooptimizer.R
import com.audiophile.buds2prooptimizer.dsp.AudioDspService
import com.audiophile.buds2prooptimizer.dsp.Buds2ProAudioProfile
import com.audiophile.buds2prooptimizer.feedback.AetherHapticFeedback
import com.audiophile.buds2prooptimizer.feedback.AetherSoundEngine
import com.audiophile.buds2prooptimizer.tiles.AetherDspTileService
import com.audiophile.buds2prooptimizer.tiles.AetherProfileTileService
import com.audiophile.buds2prooptimizer.util.HyperOSHelper

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var hapticFeedback: AetherHapticFeedback
    private lateinit var soundEngine: AetherSoundEngine
    private var audioManager: AudioManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothA2dp: BluetoothA2dp? = null
    private var cachedCodecConfig: BluetoothCodecConfig? = null

    private var isDspActive = false
    private var currentProfile = Buds2ProAudioProfile.ORIGINAL_SOUND

    private val a2dpProfileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.A2DP) {
                bluetoothA2dp = proxy as? BluetoothA2dp
                updateCodecTelemetry()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) {
                bluetoothA2dp = null
                updateCodecTelemetry()
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            updateCodecTelemetry()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            updateCodecTelemetry()
        }
    }

    private val audioTelemetryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_CODEC_CONFIG_CHANGED) {
                val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("android.bluetooth.extra.CODEC_STATUS", BluetoothCodecStatus::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("android.bluetooth.extra.CODEC_STATUS")
                }
                status?.codecConfig?.let { cfg ->
                    cachedCodecConfig = cfg
                }
            }
            updateCodecTelemetry()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hapticFeedback = AetherHapticFeedback(this)
        soundEngine = AetherSoundEngine()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

        initBluetoothProxy()
        setupViews()
        loadState()
        handleIntentProfile(intent)
    }

    private fun initBluetoothProxy() {
        try {
            bluetoothAdapter?.getProfileProxy(this, a2dpProfileListener, BluetoothProfile.A2DP)
        } catch (_: SecurityException) {
            // Handled gracefully if BLUETOOTH_CONNECT is not yet granted
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentProfile(intent)
    }

    private fun handleIntentProfile(intent: Intent?) {
        val profileName = intent?.getStringExtra("PROFILE")
        if (profileName != null) {
            val p = try { Buds2ProAudioProfile.valueOf(profileName) } catch (_: Exception) { null }
            if (p != null) {
                setProfile(p)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(this)
        registerTelemetryListeners()
        loadState()
        updateCodecTelemetry()
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(this)
        unregisterTelemetryListeners()
    }

    private fun registerTelemetryListeners() {
        audioManager?.registerAudioDeviceCallback(audioDeviceCallback, null)
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(ACTION_CODEC_CONFIG_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(audioTelemetryReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(audioTelemetryReceiver, filter)
        }
    }

    private fun unregisterTelemetryListeners() {
        audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        try {
            unregisterReceiver(audioTelemetryReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "DSP_ACTIVE" || key == "ACTIVE_PROFILE") {
            runOnUiThread {
                loadState()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupViews() {
        val btnMasterToggle = findViewById<Button>(R.id.btnMasterToggle)
        val btnProfileOriginal = findViewById<Button>(R.id.btnProfileOriginal)
        val btnProfileDolby = findViewById<Button>(R.id.btnProfileDolby)
        val btnProfileXiaomi = findViewById<Button>(R.id.btnProfileXiaomi)
        val btnHyperOs = findViewById<Button>(R.id.btnHyperOs)
        val cbHaptics = findViewById<CheckBox>(R.id.cbHaptics)
        val cbSound = findViewById<CheckBox>(R.id.cbSound)

        fun bindInteractiveButton(view: View, onClickAction: () -> Unit) {
            view.setOnClickListener {
                view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(60).withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120)
                        .setInterpolator(android.view.animation.OvershootInterpolator()).start()
                }.start()
                onClickAction()
            }
        }

        bindInteractiveButton(btnMasterToggle) {
            if (currentProfile != Buds2ProAudioProfile.ORIGINAL_SOUND) {
                hapticFeedback.performClick()
                soundEngine.playMechanicalClick()
                Toast.makeText(this, "Turn on ORIGINAL mode to engage Harman DSP", Toast.LENGTH_SHORT).show()
                return@bindInteractiveButton
            }
            hapticFeedback.performHeavyClick()
            soundEngine.playMechanicalClick()
            toggleDsp()
        }

        val profileButtons = listOf(btnProfileOriginal, btnProfileDolby, btnProfileXiaomi)
        val profiles = listOf(
            Buds2ProAudioProfile.ORIGINAL_SOUND,
            Buds2ProAudioProfile.DOLBY_ATMOS,
            Buds2ProAudioProfile.XIAOMI_SOUND
        )

        profileButtons.forEachIndexed { index, button ->
            bindInteractiveButton(button) {
                val targetProfile = profiles[index]
                if (targetProfile == Buds2ProAudioProfile.ORIGINAL_SOUND && !isHeadsetConnected()) {
                    hapticFeedback.performClick()
                    soundEngine.playMechanicalClick()
                    Toast.makeText(this, "Connect Galaxy Buds or IEMs to access Original Bit-Perfect mode", Toast.LENGTH_SHORT).show()
                    return@bindInteractiveButton
                }
                hapticFeedback.performClick()
                soundEngine.playMechanicalClick()
                setProfile(targetProfile)
            }
        }

        bindInteractiveButton(btnHyperOs) {
            hapticFeedback.performDoubleClick()
            soundEngine.playMechanicalClick()
            try {
                HyperOSHelper.openSoundEffectsSettings(this)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
            }
        }

        cbHaptics.setOnCheckedChangeListener { _, isChecked ->
            hapticFeedback.setEnabled(isChecked)
            cbHaptics.text = if (isChecked) "HAPTICS: ON" else "HAPTICS: OFF"
        }

        cbSound.setOnCheckedChangeListener { _, isChecked ->
            soundEngine.setEnabled(isChecked)
            cbSound.text = if (isChecked) "SOUND: ON" else "SOUND: OFF"
        }
    }

    private fun isHeadsetConnected(): Boolean {
        val manager = audioManager ?: return false
        val devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { device ->
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_BLE_BROADCAST,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE -> true
                else -> false
            }
        }
    }

    /**
     * Queries active audio output devices and BluetoothA2dp codec configuration
     * to render live audiophile telemetry.
     */
    private fun updateCodecTelemetry() {
        val tvCodecStatus = findViewById<TextView>(R.id.tvCodecStatus) ?: return
        val manager = audioManager ?: return
        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val usbDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        val wiredDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        }
        val bleDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
            it.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
        }
        val a2dpDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }

        when {
            a2dpDevice != null -> {
                val codecDetails = queryActiveA2dpCodecString()
                tvCodecStatus.text = codecDetails
                tvCodecStatus.setTextColor(Color.parseColor("#00F0FF"))
            }
            bleDevice != null -> {
                tvCodecStatus.text = "CODEC: LC3 // LE AUDIO DIRECT"
                tvCodecStatus.setTextColor(Color.parseColor("#00F0FF"))
            }
            usbDevice != null -> {
                val sampleRateStr = usbDevice.sampleRates.maxOrNull()?.let { "${it / 1000}kHz" } ?: "HI-RES"
                tvCodecStatus.text = "WIRED HI-RES // USB DAC ($sampleRateStr)"
                tvCodecStatus.setTextColor(Color.parseColor("#E5B869"))
            }
            wiredDevice != null -> {
                tvCodecStatus.text = "ANALOG DIRECT // 3.5MM IEM OUT"
                tvCodecStatus.setTextColor(Color.parseColor("#E5B869"))
            }
            else -> {
                tvCodecStatus.text = "DISCONNECTED // PHONE SPEAKER"
                tvCodecStatus.setTextColor(Color.parseColor("#757C8A"))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun queryActiveA2dpCodecString(): String {
        cachedCodecConfig?.let { cfg ->
            return formatCodecConfig(cfg, "Buds")
        }

        val a2dp = bluetoothA2dp
        val activeDevice: BluetoothDevice? = try {
            val getActiveDeviceMethod = a2dp?.javaClass?.getMethod("getActiveDevice")
            (getActiveDeviceMethod?.invoke(a2dp) as? BluetoothDevice)
        } catch (_: Exception) {
            null
        } ?: runCatching { a2dp?.connectedDevices?.firstOrNull() }.getOrNull()
          ?: runCatching { bluetoothAdapter?.bondedDevices?.firstOrNull { it.name?.contains("Buds", ignoreCase = true) == true } }.getOrNull()

        if (a2dp != null && activeDevice != null) {
            try {
                val getCodecStatusMethod = BluetoothA2dp::class.java.getMethod(
                    "getCodecStatus",
                    BluetoothDevice::class.java
                )
                val codecStatus = getCodecStatusMethod.invoke(a2dp, activeDevice) as? BluetoothCodecStatus
                val codecConfig: BluetoothCodecConfig? = codecStatus?.codecConfig
                if (codecConfig != null) {
                    cachedCodecConfig = codecConfig
                    return formatCodecConfig(codecConfig, activeDevice.name)
                }
            } catch (_: Exception) {
            }
        }

        val deviceName = activeDevice?.name ?: "Buds2 Pro"
        return if (deviceName.contains("Buds", ignoreCase = true)) {
            "CODEC: AAC // 24-BIT / 48kHz"
        } else {
            "CODEC: AAC // HI-RES STREAM"
        }
    }

    private fun formatCodecConfig(config: BluetoothCodecConfig, deviceName: String?): String {
        val isBuds = deviceName?.contains("Buds", ignoreCase = true) == true
        val codecName = when (config.codecType) {
            0 -> "SBC"
            1 -> "AAC"
            2 -> "aptX"
            3 -> "aptX HD"
            4 -> "LDAC"
            5 -> "LC3"
            6 -> "aptX Adaptive"
            else -> if (isBuds) "SSC (Seamless)" else "HI-RES BT"
        }

        val bitDepth = when (config.bitsPerSample) {
            BluetoothCodecConfig.BITS_PER_SAMPLE_16 -> "16-BIT"
            BluetoothCodecConfig.BITS_PER_SAMPLE_24 -> "24-BIT"
            BluetoothCodecConfig.BITS_PER_SAMPLE_32 -> "32-BIT"
            else -> null
        }

        val sampleRate = when (config.sampleRate) {
            BluetoothCodecConfig.SAMPLE_RATE_44100 -> "44.1kHz"
            BluetoothCodecConfig.SAMPLE_RATE_48000 -> "48kHz"
            BluetoothCodecConfig.SAMPLE_RATE_88200 -> "88.2kHz"
            BluetoothCodecConfig.SAMPLE_RATE_96000 -> "96kHz"
            BluetoothCodecConfig.SAMPLE_RATE_176400 -> "176.4kHz"
            BluetoothCodecConfig.SAMPLE_RATE_192000 -> "192kHz"
            else -> null
        }

        return when {
            bitDepth != null && sampleRate != null -> "CODEC: $codecName $bitDepth / $sampleRate"
            sampleRate != null -> "CODEC: $codecName / $sampleRate"
            else -> "CODEC: $codecName"
        }
    }

    private fun loadState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDspActive = prefs.getBoolean("DSP_ACTIVE", false)
        val profileName = prefs.getString("ACTIVE_PROFILE", Buds2ProAudioProfile.ORIGINAL_SOUND.name)
        currentProfile = try {
            Buds2ProAudioProfile.valueOf(profileName ?: Buds2ProAudioProfile.ORIGINAL_SOUND.name)
        } catch (e: Exception) {
            Buds2ProAudioProfile.ORIGINAL_SOUND
        }

        if (currentProfile != Buds2ProAudioProfile.ORIGINAL_SOUND && isDspActive) {
            isDspActive = false
            prefs.edit().putBoolean("DSP_ACTIVE", false).apply()
        }

        updateUi()
    }

    private fun toggleDsp() {
        isDspActive = !isDspActive
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("DSP_ACTIVE", isDspActive).apply()

        val intent = Intent(this, AudioDspService::class.java).apply {
            action = AudioDspService.ACTION_TOGGLE_DSP
            putExtra("STATE", isDspActive)
        }
        if (isDspActive) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }

        AetherDspTileService.requestTileUpdate(this)
        updateUi()
    }

    private fun setProfile(profile: Buds2ProAudioProfile) {
        if (profile == Buds2ProAudioProfile.ORIGINAL_SOUND && !isHeadsetConnected()) {
            Toast.makeText(this, "Connect Galaxy Buds or IEMs to access Original Bit-Perfect mode", Toast.LENGTH_SHORT).show()
            return
        }

        currentProfile = profile
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("ACTIVE_PROFILE", profile.name).apply()

        if (profile != Buds2ProAudioProfile.ORIGINAL_SOUND && isDspActive) {
            isDspActive = false
            prefs.edit().putBoolean("DSP_ACTIVE", false).apply()
            val dspIntent = Intent(this, AudioDspService::class.java).apply {
                action = AudioDspService.ACTION_TOGGLE_DSP
                putExtra("STATE", false)
            }
            startService(dspIntent)
            AetherDspTileService.requestTileUpdate(this)
        }

        HyperOSHelper.setHyperOsEffect(this, profile.hyperOsEffect)

        val intent = Intent(this, AudioDspService::class.java).apply {
            action = AudioDspService.ACTION_SET_PROFILE
            putExtra("PROFILE", profile.name)
        }
        if (isDspActive) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }

        AetherProfileTileService.requestTileUpdate(this)
        updateUi()
    }

    private fun updateUi() {
        val visualizer = findViewById<AetherVisualizerView>(R.id.visualizer)
        val btnMasterToggle = findViewById<Button>(R.id.btnMasterToggle)
        val tvTelemetry = findViewById<TextView>(R.id.tvTelemetry)
        val btnProfileOriginal = findViewById<Button>(R.id.btnProfileOriginal)
        val btnProfileDolby = findViewById<Button>(R.id.btnProfileDolby)
        val btnProfileXiaomi = findViewById<Button>(R.id.btnProfileXiaomi)

        val isOriginal = currentProfile == Buds2ProAudioProfile.ORIGINAL_SOUND

        if (isOriginal) {
            btnMasterToggle.alpha = 1.0f
            visualizer.setDspActive(isDspActive)
            if (isDspActive) {
                btnMasterToggle.text = "[ ACTIVE ]"
                btnMasterToggle.setTextColor(Color.parseColor("#00F0FF"))
            } else {
                btnMasterToggle.text = "[ BYPASSED ]"
                btnMasterToggle.setTextColor(Color.parseColor("#FFFFFF"))
            }
        } else {
            btnMasterToggle.alpha = 0.38f
            btnMasterToggle.text = "[ BYPASSED ]"
            btnMasterToggle.setTextColor(Color.parseColor("#888888"))
            visualizer.setDspActive(false)
        }

        tvTelemetry.text = currentProfile.description

        val activeColor = Color.parseColor("#00F0FF")
        val activeTextColor = Color.parseColor("#0C0E12")
        val inactiveColor = Color.parseColor("#161922")
        val inactiveTextColor = Color.parseColor("#FFFFFF")

        btnProfileOriginal.backgroundTintList = ColorStateList.valueOf(
            if (currentProfile == Buds2ProAudioProfile.ORIGINAL_SOUND) activeColor else inactiveColor
        )
        btnProfileOriginal.setTextColor(
            if (currentProfile == Buds2ProAudioProfile.ORIGINAL_SOUND) activeTextColor else inactiveTextColor
        )

        btnProfileDolby.backgroundTintList = ColorStateList.valueOf(
            if (currentProfile == Buds2ProAudioProfile.DOLBY_ATMOS) activeColor else inactiveColor
        )
        btnProfileDolby.setTextColor(
            if (currentProfile == Buds2ProAudioProfile.DOLBY_ATMOS) activeTextColor else inactiveTextColor
        )

        btnProfileXiaomi.backgroundTintList = ColorStateList.valueOf(
            if (currentProfile == Buds2ProAudioProfile.XIAOMI_SOUND) activeColor else inactiveColor
        )
        btnProfileXiaomi.setTextColor(
            if (currentProfile == Buds2ProAudioProfile.XIAOMI_SOUND) activeTextColor else inactiveTextColor
        )

        updateCodecTelemetry()
    }

    override fun onDestroy() {
        try {
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, bluetoothA2dp)
        } catch (_: Exception) {
        }
        soundEngine.release()
        super.onDestroy()
    }

    companion object {
        private const val PREFS_NAME = "AetherPrefs"
        private const val ACTION_CODEC_CONFIG_CHANGED =
            "android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED"
    }
}
