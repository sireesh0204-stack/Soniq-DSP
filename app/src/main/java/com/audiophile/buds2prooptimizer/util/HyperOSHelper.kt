package com.audiophile.buds2prooptimizer.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.provider.Settings
import android.util.Log
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object HyperOSHelper {

    private const val TAG = "HyperOSHelper"

    // Xiaomi HyperOS framework JAR paths for AudioEffectCenter
    private const val MIUI_FRAMEWORK_SYSTEM_EXT_PATH = "/system_ext/framework/miui-framework.jar"
    private const val MIUI_FRAMEWORK_SYSTEM_PATH = "/system/framework/miui-framework.jar"
    private const val CLASS_AUDIO_EFFECT_CENTER = "android.media.audiofx.AudioEffectCenter"

    // Settings.Global / Settings.System keys used by com.miui.misound & HeadsetSettingsFW
    private const val KEY_EFFECT_IMPLEMENTER = "effect_implementer"
    private const val KEY_LAST_SELECT_EFFECT_NONE = "last_select_effect_none"
    private const val KEY_DOLBY_EFFECT_SWITCH = "dolby_effect_switch"
    private const val KEY_MISOUND_STATE = "misound_state"

    // Broadcast Actions
    private const val ACTION_AUDIO_EFFECT_REFRESH = "miui.intent.action.ACTION_AUDIO_EFFECT_REFRESH"
    private const val ACTION_SYSTEM_UI_DOLBY_EFFECT_SWITCH = "miui.intent.action.ACTION_SYSTEM_UI_DOLBY_EFFECT_SWITCH"
    private const val ACTION_3D_SURROUND_STATE_CHANGED = "com.miui.misound.ACTION_3D_SURROUND_STATE_CHANGED"

    // Standard & Vendor UUIDs
    private val EFFECT_TYPE_NULL: UUID = UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210")
    private val EFFECT_TYPE_DS: UUID = UUID.fromString("46d279d9-9be7-453d-9d7c-ef937f675587")

    // Known Dolby DAP & Xiaomi MiSound implementation UUIDs across MTK / QCOM HyperOS builds
    private val KNOWN_DOLBY_UUIDS = setOf(
        UUID.fromString("46d279aa-86e7-11e2-9b9f-0002a5d5c51b"), // Dolby DAP Primary (MTK/HyperOS)
        UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa"), // Dolby Atmos DAX3
        UUID.fromString("6ab06da4-c516-4611-8166-452799218539")  // Dolby Game / secondary effect
    )

    private val KNOWN_MISOUND_UUIDS = setOf(
        UUID.fromString("5b8e36a5-144a-4c38-b1d7-0002a5d5c51b"), // Xiaomi MiSound Primary
        UUID.fromString("2795f2c0-5059-11e2-bcfd-0800200c9a66")  // Dirac / MiSound Legacy
    )

    // Proprietary MiSound / HeadsetSettingsFW / Dolby AudioEffect parameter IDs
    private const val DOLBY_PARAM_DAP_ON = 0
    private const val MISOUND_PARAM_ENABLE = 5
    private const val MISOUND_PARAM_EFFECT_SWITCH = 25

    // Priority 10000 ensures untrusted_app preempts com.miui.misound (priority 0) on Session 0
    private const val SESSION_ZERO_TAKEOVER_PRIORITY = 10000

    // Hold active AudioEffect references when overriding Session 0 so AudioFlinger
    // does not immediately revert control to lower-priority clients upon GC/release.
    private val retainedEffects = mutableListOf<AudioEffect>()

    // Cached Class reference for AudioEffectCenter
    @Volatile
    private var cachedAudioEffectCenterClass: Class<*>? = null

    /**
     * Sets the active sound effect implementer in Xiaomi HyperOS by:
     * 1. Driving android.media.audiofx.AudioEffectCenter.getInstance(context).setEffectActive(...)
     * 2. Synchronizing Settings.Global & Settings.System state tables
     * 3. Performing Session 0 high-priority AudioEffect takeover
     * 4. Updating vendor AudioManager HAL parameters and notifying com.miui.misound
     *
     * Valid effect strings:
     * - "none"    -> Original Sound / Bit-Perfect (HyperOS sound effects disabled)
     * - "dolby"   -> Dolby Atmos mode
     * - "misound" -> Xiaomi Sound mode
     */
    fun setHyperOsEffect(context: Context, effect: String): Boolean {
        val normalizedEffect = when (effect.lowercase()) {
            "dolby" -> "dolby"
            "misound" -> "misound"
            else -> "none"
        }

        var centerSuccess = false
        var settingsSuccess = false
        var audioFxSuccess = false

        try {
            // 1. Release retained Session 0 locks first so new transitions apply cleanly
            if (normalizedEffect != "none") {
                releaseRetainedEffects()
            }

            // 2. Synchronize Settings.Global and Settings.System state tables first
            // so AudioEffectCenter / com.miui.misound observers read consistent state
            settingsSuccess = updateSystemSettings(context, normalizedEffect)
            Log.d(TAG, "updateSystemSettings($normalizedEffect) -> success=$settingsSuccess")

            // 3. Drive HyperOS AudioEffectCenter without passing invalid "none" effect identifier
            centerSuccess = invokeAudioEffectCenter(context, normalizedEffect)
            Log.d(TAG, "invokeAudioEffectCenter($normalizedEffect) -> success=$centerSuccess")

            // Re-assert Settings.Global / Settings.System after AudioEffectCenter in case
            // deactivating dolby/misound caused framework callbacks to clobber implementer keys
            if (normalizedEffect == "none") {
                updateSystemSettings(context, "none")
            }

            // 4. Directly manipulate AudioEffect Session 0 (Dolby DAP & MiSound engines)
            audioFxSuccess = applySessionZeroAudioEffects(normalizedEffect)
            Log.d(TAG, "Direct Session 0 AudioEffect control success: $audioFxSuccess")

            // 5. Apply vendor AudioManager parameters as secondary hint
            applyVendorHalParameters(context, normalizedEffect)

            // 6. Trigger UI & service state re-evaluation in com.miui.misound
            triggerMiSoundStateRefresh(context, normalizedEffect)

        } catch (e: Exception) {
            Log.w(TAG, "Unable to update HyperOS audio effect: ${e.message}")
        }
        return centerSuccess || settingsSuccess || audioFxSuccess
    }

    /**
     * Loads `android.media.audiofx.AudioEffectCenter` from boot classpath or via PathClassLoader
     * fallback from `/system_ext/framework/miui-framework.jar`, obtains the singleton instance via
     * `getInstance(Context)`, and calls the INSTANCE method `setEffectActive(String, boolean)`.
     */
    private fun invokeAudioEffectCenter(context: Context, effect: String): Boolean {
        return try {
            val clazz = loadAudioEffectCenterClass(context) ?: return false

            val getInstanceMethod = clazz.getDeclaredMethod("getInstance", Context::class.java).apply {
                isAccessible = true
            }
            val centerInstance = getInstanceMethod.invoke(null, context.applicationContext ?: context)
                ?: return false

            val setEffectActiveMethod = clazz.getDeclaredMethod(
                "setEffectActive",
                String::class.java,
                Boolean::class.javaPrimitiveType
            ).apply {
                isAccessible = true
            }

            when (effect) {
                "dolby" -> {
                    runCatching { setEffectActiveMethod.invoke(centerInstance, "misound", false) }
                    setEffectActiveMethod.invoke(centerInstance, "dolby", true)
                }
                "misound" -> {
                    runCatching { setEffectActiveMethod.invoke(centerInstance, "dolby", false) }
                    setEffectActiveMethod.invoke(centerInstance, "misound", true)
                }
                else -> {
                    // "none" (Original Sound): explicitly deactivate both dolby and misound.
                    // Never invoke setEffectActive("none", ...) as "none" is not a registered
                    // AudioEffectCenter effect and triggers a SecurityException / IllegalArgumentException.
                    val dolbyDeactivated = runCatching {
                        setEffectActiveMethod.invoke(centerInstance, "dolby", false)
                        true
                    }.getOrDefault(false)

                    val miSoundDeactivated = runCatching {
                        setEffectActiveMethod.invoke(centerInstance, "misound", false)
                        true
                    }.getOrDefault(false)

                    return dolbyDeactivated || miSoundDeactivated
                }
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "AudioEffectCenter invocation failed for '$effect': ${t.message}", t)
            false
        }
    }

    /**
     * Resolves `android.media.audiofx.AudioEffectCenter` from the default classloader or falls back
     * to loading `/system_ext/framework/miui-framework.jar` via PathClassLoader.
     */
    private fun loadAudioEffectCenterClass(context: Context): Class<*>? {
        cachedAudioEffectCenterClass?.let { return it }

        synchronized(this) {
            cachedAudioEffectCenterClass?.let { return it }

            // Attempt 1: Standard Context / Boot ClassLoader
            runCatching {
                val cls = Class.forName(CLASS_AUDIO_EFFECT_CENTER, false, context.classLoader)
                cachedAudioEffectCenterClass = cls
                return cls
            }

            runCatching {
                val cls = Class.forName(CLASS_AUDIO_EFFECT_CENTER)
                cachedAudioEffectCenterClass = cls
                return cls
            }

            // Attempt 2: PathClassLoader fallback for /system_ext/framework/miui-framework.jar
            val jarPaths = listOf(MIUI_FRAMEWORK_SYSTEM_EXT_PATH, MIUI_FRAMEWORK_SYSTEM_PATH)
            for (jarPath in jarPaths) {
                val jarFile = File(jarPath)
                if (jarFile.exists() && jarFile.canRead()) {
                    runCatching {
                        val parentLoader = context.classLoader ?: ClassLoader.getSystemClassLoader()
                        val pathClassLoader = PathClassLoader(jarFile.absolutePath, parentLoader)
                        val cls = Class.forName(CLASS_AUDIO_EFFECT_CENTER, true, pathClassLoader)
                        cachedAudioEffectCenterClass = cls
                        Log.d(TAG, "Loaded $CLASS_AUDIO_EFFECT_CENTER via PathClassLoader from $jarPath")
                        return cls
                    }.onFailure { e ->
                        Log.w(TAG, "PathClassLoader failed for $jarPath: ${e.message}")
                    }
                }
            }

            Log.w(TAG, "Unable to locate $CLASS_AUDIO_EFFECT_CENTER in boot classpath or miui-framework.jar")
            return null
        }
    }

    /**
     * Step 2: Synchronizes Settings.Global and Settings.System keys and notifies ContentObservers.
     */
    private fun updateSystemSettings(context: Context, effect: String): Boolean {
        val resolver = context.contentResolver
        val normalizedEffect = when (effect.lowercase()) {
            "dolby" -> "dolby"
            "misound" -> "misound"
            else -> "none"
        }
        val isNone = if (normalizedEffect == "none") 1 else 0
        val dolbyInt = if (normalizedEffect == "dolby") 1 else 0
        val miSoundInt = if (normalizedEffect == "misound") 1 else 0

        // 1. Update Settings.Global keys
        val putGlobalImpl = runCatching {
            Settings.Global.putString(resolver, KEY_EFFECT_IMPLEMENTER, normalizedEffect)
        }.getOrDefault(false)
        val putGlobalNone = runCatching {
            Settings.Global.putInt(resolver, KEY_LAST_SELECT_EFFECT_NONE, isNone)
        }.getOrDefault(false)
        runCatching { Settings.Global.putInt(resolver, KEY_DOLBY_EFFECT_SWITCH, dolbyInt) }
        runCatching { Settings.Global.putInt(resolver, KEY_MISOUND_STATE, miSoundInt) }

        // 2. Update Settings.System keys (used by HyperOS speaker / headset routing profiles)
        val putSystemImpl = runCatching {
            Settings.System.putString(resolver, KEY_EFFECT_IMPLEMENTER, normalizedEffect)
        }.getOrDefault(false)
        val putSystemNone = runCatching {
            Settings.System.putInt(resolver, KEY_LAST_SELECT_EFFECT_NONE, isNone)
        }.getOrDefault(false)
        runCatching { Settings.System.putInt(resolver, KEY_DOLBY_EFFECT_SWITCH, dolbyInt) }
        runCatching { Settings.System.putInt(resolver, KEY_MISOUND_STATE, miSoundInt) }

        // 3. Notify ContentObservers across both Global and System tables
        runCatching {
            resolver.notifyChange(Settings.Global.getUriFor(KEY_EFFECT_IMPLEMENTER), null)
            resolver.notifyChange(Settings.Global.getUriFor(KEY_LAST_SELECT_EFFECT_NONE), null)
            resolver.notifyChange(Settings.Global.getUriFor(KEY_DOLBY_EFFECT_SWITCH), null)
            resolver.notifyChange(Settings.Global.getUriFor(KEY_MISOUND_STATE), null)
            resolver.notifyChange(Settings.System.getUriFor(KEY_EFFECT_IMPLEMENTER), null)
            resolver.notifyChange(Settings.System.getUriFor(KEY_LAST_SELECT_EFFECT_NONE), null)
            resolver.notifyChange(Settings.System.getUriFor(KEY_DOLBY_EFFECT_SWITCH), null)
            resolver.notifyChange(Settings.System.getUriFor(KEY_MISOUND_STATE), null)
        }

        // 4. Broadcast state synchronization directly to com.miui.misound
        runCatching {
            val dolbyIntent = Intent(ACTION_SYSTEM_UI_DOLBY_EFFECT_SWITCH).apply {
                setPackage("com.miui.misound")
                putExtra("flag", dolbyInt == 1)
                putExtra("state", dolbyInt)
            }
            context.sendBroadcast(dolbyIntent)

            val refreshIntent = Intent(ACTION_AUDIO_EFFECT_REFRESH).apply {
                setPackage("com.miui.misound")
                putExtra(KEY_EFFECT_IMPLEMENTER, normalizedEffect)
                putExtra(KEY_LAST_SELECT_EFFECT_NONE, isNone)
            }
            context.sendBroadcast(refreshIntent)
        }

        Log.d(
            TAG,
            "updateSystemSettings($normalizedEffect) -> " +
                "$KEY_EFFECT_IMPLEMENTER=$normalizedEffect (global=$putGlobalImpl, system=$putSystemImpl), " +
                "$KEY_LAST_SELECT_EFFECT_NONE=$isNone (global=$putGlobalNone, system=$putSystemNone), " +
                "$KEY_DOLBY_EFFECT_SWITCH=$dolbyInt, $KEY_MISOUND_STATE=$miSoundInt"
        )
        return (putGlobalImpl && putGlobalNone) || (putSystemImpl && putSystemNone)
    }

    /**
     * Step 3: Instantiates AudioEffect on Global Audio Session 0 with priority 10000
     * to preempt com.miui.misound, sends the exact binary parameter payloads,
     * and toggles the hardware/DSP effect enabled state directly in AudioFlinger.
     */
    @Synchronized
    private fun applySessionZeroAudioEffects(effect: String): Boolean {
        val enableDolby = (effect == "dolby")
        val enableMiSound = (effect == "misound")
        val modeVal = when (effect) {
            "none" -> 0
            "misound" -> 1
            "dolby" -> 2
            else -> 0
        }
        var anyApplied = false

        // Release previously held overrides before re-acquiring
        releaseRetainedEffects()

        val constructor = getHiddenAudioEffectConstructor() ?: return false
        val setParamMethod = getSetParameterMethod()

        // Discover active Dolby and MiSound UUIDs from the device's registered effects table
        val (dolbyUuids, miSoundUuids) = discoverDeviceEffectUuids()

        // 3a. Control Dolby DAP on Session 0 (12-byte value payload per Lq/a;->m)
        for (uuid in dolbyUuids) {
            val fx = instantiateEffect(constructor, EFFECT_TYPE_NULL, uuid, SESSION_ZERO_TAKEOVER_PRIORITY, 0)
                ?: instantiateEffect(constructor, EFFECT_TYPE_DS, uuid, SESSION_ZERO_TAKEOVER_PRIORITY, 0)
            if (fx != null) {
                try {
                    setParamMethod?.let { method ->
                        invokeDolbyDapParam(fx, method, DOLBY_PARAM_DAP_ON, if (enableDolby) 1 else 0)
                        invokeIntParam(fx, method, MISOUND_PARAM_EFFECT_SWITCH, if (enableDolby) 1 else 0)
                    }
                    val status = fx.setEnabled(enableDolby)
                    Log.d(
                        TAG,
                        "Dolby AudioEffect [$uuid] setEnabled($enableDolby) -> status=$status, hasControl=${fx.hasControl()}"
                    )
                    anyApplied = true

                    // Maintain retained reference when disabling in "none" mode so untrusted_app
                    // keeps priority 10000 control over Session 0
                    if (!enableDolby && fx.hasControl()) {
                        retainedEffects.add(fx)
                    } else {
                        fx.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error controlling Dolby UUID $uuid: ${e.message}")
                    runCatching { fx.release() }
                }
            }
        }

        // 3b. Control Xiaomi MiSound / Dirac on Session 0 (4-byte LE param 5 & param 25 payloads)
        for (uuid in miSoundUuids) {
            val fx = instantiateEffect(constructor, EFFECT_TYPE_NULL, uuid, SESSION_ZERO_TAKEOVER_PRIORITY, 0)
            if (fx != null) {
                try {
                    setParamMethod?.let { method ->
                        invokeIntParam(fx, method, MISOUND_PARAM_ENABLE, if (enableMiSound) 1 else 0)
                        invokeIntParam(fx, method, MISOUND_PARAM_EFFECT_SWITCH, modeVal)
                    }
                    val status = fx.setEnabled(enableMiSound)
                    Log.d(
                        TAG,
                        "MiSound AudioEffect [$uuid] setEnabled($enableMiSound) -> status=$status, hasControl=${fx.hasControl()}"
                    )
                    anyApplied = true

                    if (!enableMiSound && fx.hasControl()) {
                        retainedEffects.add(fx)
                    } else {
                        fx.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error controlling MiSound UUID $uuid: ${e.message}")
                    runCatching { fx.release() }
                }
            }
        }

        return anyApplied
    }

    /**
     * Queries AudioEffect.queryEffects() to dynamically catch platform-specific Dolby/MiSound
     * UUIDs on MediaTek & Qualcomm devices, merging them with known static UUIDs.
     */
    private fun discoverDeviceEffectUuids(): Pair<Set<UUID>, Set<UUID>> {
        val dolbySet = LinkedHashSet<UUID>(KNOWN_DOLBY_UUIDS)
        val miSoundSet = LinkedHashSet<UUID>(KNOWN_MISOUND_UUIDS)

        runCatching {
            val descriptors = AudioEffect.queryEffects() ?: emptyArray()
            for (desc in descriptors) {
                val name = (desc.name ?: "").lowercase()
                val impl = (desc.implementor ?: "").lowercase()
                val uuid = desc.uuid ?: continue

                if (name.contains("dolby") || name.contains("dap") || name.contains("dax") || impl.contains("dolby")) {
                    dolbySet.add(uuid)
                    Log.d(TAG, "Discovered Dolby Effect Descriptor: name=${desc.name}, uuid=$uuid, type=${desc.type}")
                } else if (name.contains("misound") || name.contains("xiaomi") || name.contains("dirac") || impl.contains("xiaomi")) {
                    miSoundSet.add(uuid)
                    Log.d(TAG, "Discovered MiSound Effect Descriptor: name=${desc.name}, uuid=$uuid, type=${desc.type}")
                }
            }
        }
        return Pair(dolbySet, miSoundSet)
    }

    private fun getHiddenAudioEffectConstructor(): Constructor<AudioEffect>? {
        return try {
            AudioEffect::class.java.getConstructor(
                UUID::class.java,
                UUID::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).apply { isAccessible = true }
        } catch (e: Exception) {
            Log.w(TAG, "Could not reflect AudioEffect(UUID, UUID, int, int): ${e.message}")
            null
        }
    }

    private fun instantiateEffect(
        constructor: Constructor<AudioEffect>,
        type: UUID,
        uuid: UUID,
        priority: Int,
        session: Int
    ): AudioEffect? {
        return try {
            constructor.newInstance(type, uuid, priority, session)
        } catch (_: Exception) {
            null
        }
    }

    private fun getSetParameterMethod(): Method? {
        return try {
            AudioEffect::class.java.getMethod(
                "setParameter",
                ByteArray::class.java,
                ByteArray::class.java
            ).apply { isAccessible = true }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Sends a 4-byte Little-Endian parameter ID and 4-byte Little-Endian integer value:
     * Used for MiSound param 5 (enable: 1 or 0) and param 25 (mode: none=0, misound=1, dolby=2).
     */
    private fun invokeIntParam(fx: AudioEffect, method: Method, paramId: Int, value: Int) {
        runCatching {
            val paramBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(paramId).array()
            val valueBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
            method.invoke(fx, paramBytes, valueBytes)
        }
    }

    /**
     * Replicates Xiaomi MiSound's proprietary DolbyAudioEffect.setParameter payload (Lq/a;->m):
     * - param: 4-byte Little-Endian ByteBuffer containing 0 (DOLBY_PARAM_DAP_ON)
     * - value: 12-byte Little-Endian ByteBuffer containing:
     *     int 0 (parameter ID)
     *     int 1 (count of parameters)
     *     int (1 if enableDolby else 0)
     */
    private fun invokeDolbyDapParam(fx: AudioEffect, method: Method, paramId: Int, value: Int) {
        runCatching {
            val paramBytes = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(paramId)
                .array()
            val valueBytes = ByteBuffer.allocate(12)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(paramId)
                .putInt(1)
                .putInt(value)
                .array()
            method.invoke(fx, paramBytes, valueBytes)
        }
    }

    @Synchronized
    fun releaseRetainedEffects() {
        val iterator = retainedEffects.iterator()
        while (iterator.hasNext()) {
            val fx = iterator.next()
            runCatching { fx.release() }
            iterator.remove()
        }
    }

    /**
     * Step 4: Applies AudioManager HAL parameters as fallback for secondary routing layers.
     */
    private fun applyVendorHalParameters(context: Context, effect: String) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val halParams = when (effect) {
                "none" -> "dolby_enable=false;misound_enable=false;spatial_audio_enable=false;ds_state=off"
                "dolby" -> "dolby_enable=true;misound_enable=false;ds_state=on"
                "misound" -> "dolby_enable=false;misound_enable=true;ds_state=off"
                else -> "dolby_enable=false;misound_enable=false;spatial_audio_enable=false;ds_state=off"
            }
            audioManager?.setParameters(halParams)
            Log.d(TAG, "Applied AudioManager HAL parameters: $halParams")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set AudioManager HAL parameters: ${e.message}")
        }
    }

    /**
     * Step 5: Dispatches UI notification and audio session control broadcasts to com.miui.misound.
     */
    private fun triggerMiSoundStateRefresh(context: Context, effect: String) {
        val enableDolby = (effect == "dolby")

        // 5a. Unprivileged UI Dolby switch broadcast observed by HeadsetSettingsActivity
        runCatching {
            val dolbySwitchIntent = Intent(ACTION_SYSTEM_UI_DOLBY_EFFECT_SWITCH).apply {
                setPackage("com.miui.misound")
                putExtra("flag", enableDolby)
                putExtra("state", if (enableDolby) 1 else 0)
            }
            context.sendBroadcast(dolbySwitchIntent)
            Log.d(TAG, "Dispatched $ACTION_SYSTEM_UI_DOLBY_EFFECT_SWITCH to com.miui.misound")
        }

        // 5b. 3D Surround state change broadcast
        runCatching {
            val surroundIntent = Intent(ACTION_3D_SURROUND_STATE_CHANGED).apply {
                setPackage("com.miui.misound")
            }
            context.sendBroadcast(surroundIntent)
            Log.d(TAG, "Dispatched $ACTION_3D_SURROUND_STATE_CHANGED to com.miui.misound")
        }

        // 5c. Standard Android AudioEffect session 0 control broadcasts targeted at com.miui.misound
        runCatching {
            val action = if (effect == "none") {
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
            } else {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
            }
            val sessionIntent = Intent(action).apply {
                setPackage("com.miui.misound")
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            context.sendBroadcast(sessionIntent)
            Log.d(TAG, "Dispatched $action for session 0 to com.miui.misound")
        }

        // 5d. Safe protected broadcast (guarded against SecurityException on Android 16 / API 36)
        runCatching {
            val refreshIntent = Intent(ACTION_AUDIO_EFFECT_REFRESH).apply {
                setPackage("com.miui.misound")
            }
            context.sendBroadcast(refreshIntent)
            Log.d(TAG, "Dispatched $ACTION_AUDIO_EFFECT_REFRESH broadcast.")
        }
    }

    /**
     * Reads current HyperOS sound effect implementer ("dolby", "misound", or "none").
     */
    fun getHyperOsEffect(context: Context): String {
        return try {
            // Query AudioEffectCenter first if available
            val clazz = loadAudioEffectCenterClass(context)
            if (clazz != null) {
                val getInstanceMethod = clazz.getDeclaredMethod("getInstance", Context::class.java).apply {
                    isAccessible = true
                }
                val centerInstance = getInstanceMethod.invoke(null, context.applicationContext ?: context)
                if (centerInstance != null) {
                    val isEffectActiveMethod = runCatching {
                        clazz.getDeclaredMethod("isEffectActive", String::class.java).apply { isAccessible = true }
                    }.getOrNull()

                    if (isEffectActiveMethod != null) {
                        val dolbyActive = (isEffectActiveMethod.invoke(centerInstance, "dolby") as? Boolean) == true
                        if (dolbyActive) return "dolby"
                        val miSoundActive = (isEffectActiveMethod.invoke(centerInstance, "misound") as? Boolean) == true
                        if (miSoundActive) return "misound"
                    }
                }
            }

            val isNone = Settings.Global.getInt(context.contentResolver, KEY_LAST_SELECT_EFFECT_NONE, 0) == 1
            val implementer = Settings.Global.getString(context.contentResolver, KEY_EFFECT_IMPLEMENTER) ?: "none"
            if (isNone || implementer.isEmpty()) "none" else implementer
        } catch (_: Exception) {
            "none"
        }
    }

    /**
     * Attempts to launch HyperOS / MIUI specific Sound Effects panel.
     */
    fun openSoundEffectsSettings(context: Context) {
        val intents = listOf(
            Intent("miui.intent.action.HEADSET_SETTINGS"),
            Intent().setComponent(ComponentName("com.miui.misound", "com.miui.misound.HeadsetSettingsActivity")),
            Intent("miui.intent.action.DOLBY_GEQ_SETTING"),
            Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
            },
            Intent(Settings.ACTION_SOUND_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // Try next fallback
            }
        }
    }
}
