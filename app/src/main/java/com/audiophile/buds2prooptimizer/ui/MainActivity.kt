package com.audiophile.buds2prooptimizer.ui

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
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.transition.ChangeBounds
import androidx.transition.Fade
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
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

    private lateinit var connectedConstraintSet: ConstraintSet
    private lateinit var disconnectedConstraintSet: ConstraintSet
    private var lastConnectedState: Boolean? = null

    @Volatile
    private var isTrackPlaying = false
    @Volatile
    private var activeTrackSampleRate = 0
    @Volatile
    private var activeTrackBitDepth = "24-BIT"

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

    private val audioPlaybackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            super.onPlaybackConfigChanged(configs)
            evaluatePlaybackConfigs(configs)
            runOnUiThread {
                updateCodecTelemetry()
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            runOnUiThread {
                loadState()
                updateCodecTelemetry()
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            runOnUiThread {
                if (!isHeadsetConnected() && isDspActive) {
                    forceDisableDsp()
                }
                loadState()
                updateCodecTelemetry()
            }
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
            if (!isHeadsetConnected() && isDspActive) {
                forceDisableDsp()
            }
            updateCodecTelemetry()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HyperOSHelper.installFrameworkCrashProtector()
        setContentView(R.layout.activity_main)

        hapticFeedback = AetherHapticFeedback(this)
        soundEngine = AetherSoundEngine()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

        initConstraintSets()
        initBluetoothProxy()
        audioManager?.let { manager ->
            manager.registerAudioPlaybackCallback(audioPlaybackCallback, null)
            evaluatePlaybackConfigs(manager.activePlaybackConfigurations)
        }

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
        audioManager?.let { evaluatePlaybackConfigs(it.activePlaybackConfigurations) }
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

    private fun initConstraintSets() {
        val rootLayout = findViewById<ConstraintLayout>(R.id.rootLayout) ?: return
        connectedConstraintSet = ConstraintSet().apply {
            clone(rootLayout)
        }
        val density = resources.displayMetrics.density
        disconnectedConstraintSet = ConstraintSet().apply {
            clone(connectedConstraintSet)
            setVisibility(R.id.visualizer, ConstraintSet.GONE)
            setVisibility(R.id.btnMasterToggle, ConstraintSet.GONE)

            clear(R.id.btnHyperOs, ConstraintSet.BOTTOM)
            connect(
                R.id.btnHyperOs,
                ConstraintSet.TOP,
                R.id.tvCodecStatus,
                ConstraintSet.BOTTOM,
                (48 * density).toInt()
            )

            clear(R.id.llToggles, ConstraintSet.BOTTOM)
            connect(
                R.id.llToggles,
                ConstraintSet.TOP,
                R.id.btnHyperOs,
                ConstraintSet.BOTTOM,
                (24 * density).toInt()
            )
        }
    }

    private fun applyLayoutTransition(connected: Boolean) {
        if (!::connectedConstraintSet.isInitialized || !::disconnectedConstraintSet.isInitialized) return
        if (lastConnectedState == connected) return
        val isInitial = (lastConnectedState == null)
        lastConnectedState = connected

        val rootLayout = findViewById<ConstraintLayout>(R.id.rootLayout) ?: return
        if (!isInitial) {
            val transition = TransitionSet().apply {
                ordering = TransitionSet.ORDERING_TOGETHER
                addTransition(ChangeBounds().apply {
                    duration = 540L
                    interpolator = PathInterpolator(0.16f, 1f, 0.3f, 1f)
                })
                addTransition(Fade().apply {
                    duration = 400L
                    interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                })
            }
            TransitionManager.beginDelayedTransition(rootLayout, transition)
        }
        val targetSet = if (connected) connectedConstraintSet else disconnectedConstraintSet
        targetSet.applyTo(rootLayout)
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
            var isPressedDown = false

            view.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressedDown = true
                        v.animate()
                            .scaleX(0.955f)
                            .scaleY(0.955f)
                            .setDuration(120L)
                            .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
                            .start()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val isInside = event.x >= 0 && event.x <= v.width &&
                                       event.y >= 0 && event.y <= v.height
                        if (!isInside && isPressedDown) {
                            isPressedDown = false
                            v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(240L)
                                .setInterpolator(PathInterpolator(0.16f, 1f, 0.3f, 1f))
                                .start()
                        } else if (isInside && !isPressedDown) {
                            isPressedDown = true
                            v.animate()
                                .scaleX(0.955f)
                                .scaleY(0.955f)
                                .setDuration(120L)
                                .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
                                .start()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val wasPressed = isPressedDown
                        isPressedDown = false
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(260L)
                            .setInterpolator(PathInterpolator(0.16f, 1f, 0.3f, 1f))
                            .start()

                        val isInside = event.x >= 0 && event.x <= v.width &&
                                       event.y >= 0 && event.y <= v.height
                        if (wasPressed && isInside) {
                            v.post {
                                onClickAction()
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        isPressedDown = false
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(220L)
                            .setInterpolator(PathInterpolator(0.16f, 1f, 0.3f, 1f))
                            .start()
                        true
                    }
                    else -> false
                }
            }
        }

        bindInteractiveButton(btnMasterToggle) {
            if (!isHeadsetConnected()) {
                hapticFeedback.performClick()
                soundEngine.playMechanicalClick()
                Toast.makeText(this, "Connect earbuds or DAC to engage Harman DSP", Toast.LENGTH_SHORT).show()
                return@bindInteractiveButton
            }
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
                    hapticFeedback.performError()
                    soundEngine.playMechanicalClick()
                    Toast.makeText(this, "Connect earbuds or IEMs to select Original Sound", Toast.LENGTH_SHORT).show()
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

    private fun evaluatePlaybackConfigs(configs: List<AudioPlaybackConfiguration>) {
        val activeMediaConfig = configs.firstOrNull { config ->
            val isMedia = config.audioAttributes?.usage == AudioAttributes.USAGE_MEDIA
            val active = runCatching {
                val isActiveMethod = AudioPlaybackConfiguration::class.java.getMethod("isActive")
                isActiveMethod.invoke(config) as? Boolean ?: false
            }.getOrDefault(audioManager?.isMusicActive == true)
            isMedia && active
        } ?: configs.firstOrNull { config ->
            config.audioAttributes?.usage == AudioAttributes.USAGE_MEDIA && (audioManager?.isMusicActive == true)
        }

        if (activeMediaConfig != null) {
            isTrackPlaying = true
            val format: AudioFormat? = runCatching {
                val getFormatMethod = AudioPlaybackConfiguration::class.java.getMethod("getFormat")
                getFormatMethod.invoke(activeMediaConfig) as? AudioFormat
            }.getOrNull()

            activeTrackSampleRate = format?.sampleRate?.takeIf { it > 0 } ?: 48000
            activeTrackBitDepth = when (format?.encoding) {
                AudioFormat.ENCODING_PCM_16BIT -> "16-BIT"
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-BIT"
                AudioFormat.ENCODING_PCM_32BIT,
                AudioFormat.ENCODING_PCM_FLOAT -> "32-BIT / HI-RES"
                else -> "24-BIT"
            }
        } else {
            isTrackPlaying = audioManager?.isMusicActive == true
            if (!isTrackPlaying) {
                activeTrackSampleRate = 0
            } else if (activeTrackSampleRate == 0) {
                activeTrackSampleRate = 48000
                activeTrackBitDepth = "24-BIT"
            }
        }
    }

    private fun formatSampleRateKHz(sampleRateHz: Int): String {
        return com.audiophile.buds2prooptimizer.util.AudioTelemetryUtils.formatSampleRateKHz(sampleRateHz)
    }

    /**
     * Queries active audio output devices, live track playback telemetry, and BluetoothA2dp
     * codec configuration to render live audiophile telemetry.
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
                val (codecName, deviceName) = queryActiveA2dpCodecAndDevice(a2dpDevice)
                if (isTrackPlaying) {
                    val sampleRateStr = formatSampleRateKHz(activeTrackSampleRate)
                    tvCodecStatus.text = "PLAYING: $activeTrackBitDepth / $sampleRateStr // $codecName"
                } else {
                    tvCodecStatus.text = "CODEC: $codecName // $deviceName (READY)"
                }
                tvCodecStatus.setTextColor(Color.parseColor("#00F0FF"))
            }
            bleDevice != null -> {
                val deviceName = bleDevice.productName?.toString()?.takeIf { it.isNotBlank() } ?: "LE AUDIO"
                if (isTrackPlaying) {
                    val sampleRateStr = formatSampleRateKHz(activeTrackSampleRate)
                    tvCodecStatus.text = "PLAYING: $activeTrackBitDepth / $sampleRateStr // LC3"
                } else {
                    tvCodecStatus.text = "CODEC: LC3 // $deviceName (READY)"
                }
                tvCodecStatus.setTextColor(Color.parseColor("#00F0FF"))
            }
            usbDevice != null -> {
                val deviceName = usbDevice.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB DAC"
                if (isTrackPlaying) {
                    val sampleRateStr = formatSampleRateKHz(activeTrackSampleRate)
                    tvCodecStatus.text = "PLAYING: $activeTrackBitDepth / $sampleRateStr // USB DAC"
                } else {
                    tvCodecStatus.text = "CODEC: PCM HI-RES // $deviceName (READY)"
                }
                tvCodecStatus.setTextColor(Color.parseColor("#E5B869"))
            }
            wiredDevice != null -> {
                if (isTrackPlaying) {
                    val sampleRateStr = formatSampleRateKHz(activeTrackSampleRate)
                    tvCodecStatus.text = "PLAYING: $activeTrackBitDepth / $sampleRateStr // 3.5MM IEM"
                } else {
                    tvCodecStatus.text = "CODEC: ANALOG DIRECT // 3.5MM IEM (READY)"
                }
                tvCodecStatus.setTextColor(Color.parseColor("#E5B869"))
            }
            else -> {
                tvCodecStatus.text = "DISCONNECTED // PHONE SPEAKER"
                tvCodecStatus.setTextColor(Color.parseColor("#757C8A"))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun queryActiveA2dpCodecAndDevice(a2dpAudioDevice: AudioDeviceInfo): Pair<String, String> {
        val a2dp = bluetoothA2dp
        val activeDevice: BluetoothDevice? = try {
            val getActiveDeviceMethod = a2dp?.javaClass?.getMethod("getActiveDevice")
            (getActiveDeviceMethod?.invoke(a2dp) as? BluetoothDevice)
        } catch (_: Exception) {
            null
        } ?: runCatching { a2dp?.connectedDevices?.firstOrNull() }.getOrNull()
          ?: runCatching {
              bluetoothAdapter?.bondedDevices?.firstOrNull {
                  it.name?.contains("Buds", ignoreCase = true) == true
              }
          }.getOrNull()

        val resolvedDeviceName = runCatching { activeDevice?.name }.getOrNull()
            ?: a2dpAudioDevice.productName?.toString()?.takeIf { it.isNotBlank() }
            ?: "Buds2 Pro"

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
                    return Pair(resolveCodecName(codecConfig, resolvedDeviceName), resolvedDeviceName.uppercase())
                }
            } catch (_: Exception) {
            }
        }

        cachedCodecConfig?.let { cfg ->
            return Pair(resolveCodecName(cfg, resolvedDeviceName), resolvedDeviceName.uppercase())
        }

        return Pair("AAC", resolvedDeviceName.uppercase())
    }

    private fun resolveCodecName(config: BluetoothCodecConfig, deviceName: String?): String {
        return com.audiophile.buds2prooptimizer.util.AudioTelemetryUtils.resolveCodecName(config.codecType, deviceName)
    }

    private fun forceDisableDsp() {
        isDspActive = false
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("DSP_ACTIVE", false)
            .apply()
        val dspIntent = Intent(this, AudioDspService::class.java).apply {
            action = AudioDspService.ACTION_TOGGLE_DSP
            putExtra("STATE", false)
        }
        startService(dspIntent)
        AetherDspTileService.requestTileUpdate(this)
    }

    private fun loadState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDspActive = prefs.getBoolean("DSP_ACTIVE", false)
        val profileName = prefs.getString("ACTIVE_PROFILE", null)
        currentProfile = if (!profileName.isNullOrBlank()) {
            runCatching { Buds2ProAudioProfile.valueOf(profileName) }
                .getOrDefault(Buds2ProAudioProfile.ORIGINAL_SOUND)
        } else {
            Buds2ProAudioProfile.ORIGINAL_SOUND
        }

        if (!isHeadsetConnected()) {
            if (isDspActive) {
                forceDisableDsp()
            } else {
                isDspActive = false
            }
            if (currentProfile == Buds2ProAudioProfile.ORIGINAL_SOUND) {
                currentProfile = Buds2ProAudioProfile.DOLBY_ATMOS
            }
        } else if (currentProfile != Buds2ProAudioProfile.ORIGINAL_SOUND && isDspActive) {
            forceDisableDsp()
        }

        updateUi()
    }

    private fun toggleDsp() {
        if (!isHeadsetConnected()) {
            forceDisableDsp()
            updateUi()
            return
        }

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
        currentProfile = profile
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("ACTIVE_PROFILE", profile.name).apply()

        val connected = isHeadsetConnected()
        if (!connected || profile != Buds2ProAudioProfile.ORIGINAL_SOUND) {
            if (isDspActive) {
                forceDisableDsp()
            } else {
                isDspActive = false
            }
        }

        val targetHyperOsEffect = if (profile == Buds2ProAudioProfile.ORIGINAL_SOUND) {
            "none"
        } else {
            profile.hyperOsEffect
        }

        runCatching {
            HyperOSHelper.setHyperOsEffect(this, targetHyperOsEffect)
        }

        runCatching {
            val intent = Intent(this, AudioDspService::class.java).apply {
                action = AudioDspService.ACTION_SET_PROFILE
                putExtra("PROFILE", profile.name)
            }
            if (isDspActive && connected) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        }

        runCatching {
            AetherProfileTileService.requestTileUpdate(this)
        }
        updateUi()
    }

    private fun updateUi() {
        val visualizer = findViewById<AetherVisualizerView>(R.id.visualizer)
        val btnMasterToggle = findViewById<Button>(R.id.btnMasterToggle)
        val tvTelemetry = findViewById<TextView>(R.id.tvTelemetry)
        val btnProfileOriginal = findViewById<Button>(R.id.btnProfileOriginal)
        val btnProfileDolby = findViewById<Button>(R.id.btnProfileDolby)
        val btnProfileXiaomi = findViewById<Button>(R.id.btnProfileXiaomi)

        val connected = isHeadsetConnected()
        val isOriginal = currentProfile == Buds2ProAudioProfile.ORIGINAL_SOUND
        val effectiveDspActive = isDspActive && connected && isOriginal

        applyLayoutTransition(connected)

        if (isOriginal && connected) {
            btnMasterToggle.alpha = 1.0f
            visualizer.setDspActive(effectiveDspActive)
            if (effectiveDspActive) {
                btnMasterToggle.text = "[ ACTIVE ]"
                btnMasterToggle.setTextColor(Color.parseColor("#00F0FF"))
            } else {
                btnMasterToggle.text = "[ BYPASSED ]"
                btnMasterToggle.setTextColor(Color.parseColor("#FFFFFF"))
            }
        } else {
            btnMasterToggle.alpha = if (isOriginal) 0.65f else 0.38f
            btnMasterToggle.text = "[ BYPASSED ]"
            btnMasterToggle.setTextColor(Color.parseColor("#888888"))
            visualizer.setDspActive(false)
        }

        tvTelemetry.text = currentProfile.description

        val activeColor = Color.parseColor("#00F0FF")
        val activeTextColor = Color.parseColor("#0C0E12")
        val inactiveColor = Color.parseColor("#161922")
        val inactiveTextColor = Color.parseColor("#FFFFFF")

        if (!connected) {
            btnProfileOriginal.alpha = 0.35f
            btnProfileOriginal.backgroundTintList = ColorStateList.valueOf(inactiveColor)
            btnProfileOriginal.setTextColor(Color.parseColor("#555E6D"))
        } else {
            btnProfileOriginal.alpha = 1.0f
            btnProfileOriginal.backgroundTintList = ColorStateList.valueOf(
                if (currentProfile == Buds2ProAudioProfile.ORIGINAL_SOUND) activeColor else inactiveColor
            )
            btnProfileOriginal.setTextColor(
                if (currentProfile == Buds2ProAudioProfile.ORIGINAL_SOUND) activeTextColor else inactiveTextColor
            )
        }

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
            audioManager?.unregisterAudioPlaybackCallback(audioPlaybackCallback)
        } catch (_: Exception) {
        }
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
