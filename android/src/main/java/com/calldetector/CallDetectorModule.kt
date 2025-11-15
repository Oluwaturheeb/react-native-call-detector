package com.calldetector

import android.app.Activity
import android.app.Application
import android.content.*
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.core.DeviceEventManagerModule
import org.json.JSONObject

@ReactModule(name = CallDetectorModule.NAME)
class CallDetectorModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), Application.ActivityLifecycleCallbacks {

    companion object { const val NAME = "CallDetector" }

    override fun getName() = NAME

    @ReactMethod
    fun startListener() {
        val intent = Intent(reactApplicationContext, CallStateService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            reactApplicationContext.startForegroundService(intent)
        } else {
            reactApplicationContext.startService(intent)
        }
    }

    @ReactMethod
    fun stopListener() {
        val intent = Intent(reactApplicationContext, CallStateService::class.java)
        reactApplicationContext.stopService(intent)
    }

    private val callReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                val stateString = intent?.getStringExtra("state") ?: return
                val jsonStr = intent.getStringExtra("callData")
                val callDataMap = Arguments.createMap()
                if (!jsonStr.isNullOrEmpty()) {
                    val json = JSONObject(jsonStr)
                    json.keys().forEach { key -> callDataMap.putString(key, json.optString(key)) }
                }

                val event = Arguments.createMap()
                event.putString("state", stateString)
                event.putMap("call", callDataMap)

                reactApplicationContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit("CallStateUpdate", event)

            } catch (e: Exception) { Log.e("CallDetectorModule", "BroadcastReceiver error", e) }
        }
    }

    override fun initialize() {
    super.initialize()
    val filter = IntentFilter("CALL_STATE_UPDATE")
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        reactApplicationContext.applicationContext.registerReceiver(callReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        reactApplicationContext.applicationContext.registerReceiver(callReceiver, filter)
    }
}

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        try { reactApplicationContext.unregisterReceiver(callReceiver) } catch (_: Exception) {}
    }

    // Lifecycle stubs
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
