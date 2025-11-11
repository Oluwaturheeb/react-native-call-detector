package com.calldetector

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import com.facebook.react.bridge.*
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.core.DeviceEventManagerModule
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver

@ReactModule(name = CallDetectorModule.NAME)
class CallDetectorModule(reactContext: ReactApplicationContext) :
        ReactContextBaseJavaModule(reactContext), Application.ActivityLifecycleCallbacks {

  companion object {
    const val NAME = "CallDetectorModule"
  }

  private var telephonyManager: TelephonyManager? = null
  private var activity: Activity? = null
  private var wasAppInOffHook = false
  private var wasAppInRinging = false

  private val callStateListener =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : TelephonyCallback(), TelephonyCallback.CallStateListener {
              override fun onCallStateChanged(state: Int) {
                handleCallState(state, null)
              }
            }
          } else null

  override fun getName() = NAME

  @ReactMethod
  fun startListener() {
    val context = reactApplicationContext
    val intent = Intent(context, CallStateService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startForegroundService(intent)
    } else {
      context.startService(intent)
    }
  }

  @ReactMethod
  fun stopListener() {
    val context = reactApplicationContext
    val intent = Intent(context, CallStateService::class.java)
    context.stopService(intent)
  }

  private fun handleCallState(state: Int, phoneNumber: String?) {
    val emitter =
            reactApplicationContext.getJSModule(
                    DeviceEventManagerModule.RCTDeviceEventEmitter::class.java
            )
    when (state) {
      TelephonyManager.CALL_STATE_IDLE -> {
        when {
          wasAppInOffHook ->
                  emitter.emit(
                          "CallStateUpdate",
                          mapOf("state" to "Disconnected", "number" to phoneNumber)
                  )
          wasAppInRinging ->
                  emitter.emit(
                          "CallStateUpdate",
                          mapOf("state" to "Missed", "number" to phoneNumber)
                  )
        }
        wasAppInOffHook = false
        wasAppInRinging = false
      }
      TelephonyManager.CALL_STATE_OFFHOOK -> {
        wasAppInOffHook = true
        emitter.emit("CallStateUpdate", mapOf("state" to "Offhook", "number" to phoneNumber))
      }
      TelephonyManager.CALL_STATE_RINGING -> {
        wasAppInRinging = true
        emitter.emit("CallStateUpdate", mapOf("state" to "Incoming", "number" to phoneNumber))
      }
    }
  }

  // Activity lifecycle callbacks
  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
  override fun onActivityStarted(activity: Activity) {}
  override fun onActivityResumed(activity: Activity) {}
  override fun onActivityPaused(activity: Activity) {}
  override fun onActivityStopped(activity: Activity) {}
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
  override fun onActivityDestroyed(activity: Activity) {}

  override fun getConstants(): Map<String, Any> =
          mapOf(
                  "Incoming" to "Incoming",
                  "Offhook" to "Offhook",
                  "Disconnected" to "Disconnected",
                  "Missed" to "Missed"
          )

  private val callReceiver =
          object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
              val state = intent?.getIntExtra("state", TelephonyManager.CALL_STATE_IDLE) ?: return
              handleCallState(state, null)
            }
          }

  override fun initialize() {
    super.initialize()
    val filter = IntentFilter("CALL_STATE_UPDATE")
    reactApplicationContext.registerReceiver(callReceiver, filter)
  }

  override fun onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy()
    reactApplicationContext.unregisterReceiver(callReceiver)
  }
}
