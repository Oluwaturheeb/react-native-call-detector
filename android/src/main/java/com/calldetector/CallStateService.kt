package com.calldetector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo // ADDED Import for ServiceInfo
import android.database.Cursor
import android.os.Build
import android.os.IBinder
import android.provider.CallLog
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.facebook.react.ReactApplication
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor // ADDED Import for Executor
import org.json.JSONObject
import android.database.ContentObserver
import com.facebook.react.bridge.WritableMap

class CallStateService : Service() {

  private var telephonyManager: TelephonyManager? = null
  private var lastState: Int = TelephonyManager.CALL_STATE_IDLE
  private var currentNumber: String? = null
  private lateinit var mainExecutor: Executor // ADDED Executor field
private lateinit var callLogObserver: ContentObserver
  // Use nullable properties for the listeners as we did before
  private var newApiCallListener: TelephonyCallback? = null
  @Suppress("DEPRECATION") private var legacyListener: PhoneStateListener? = null

  override fun onCreate() {
    super.onCreate()
    telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
    mainExecutor = ContextCompat.getMainExecutor(this) // Initialize executor

    // Initialize the listeners here in onCreate, not as properties
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      newApiCallListener =
              object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                  // API 31+ only gives state here, number must be null
                  handleCallStateChange(state, null)
                }
              }
    } else {
      @Suppress("DEPRECATION")
      legacyListener =
              object : PhoneStateListener() {
                @Deprecated("Required for < API 31")
                override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                  handleCallStateChange(state, incomingNumber)
                }
              }
    }

    callLogObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            val lastCall = fetchLastCallData(null)
            val type = lastCall["type"]

            when (type) {
                "INCOMING" -> sendEvent("Disconnected", lastCall)
                "OUTGOING" -> sendEvent("Disconnected", lastCall)
                "MISSED"   -> sendEvent("Missed", lastCall)
                "REJECTED" -> sendEvent("Rejected", lastCall)
            }
        }
    }

    contentResolver.registerContentObserver(
        CallLog.Calls.CONTENT_URI,
        true,
        callLogObserver
    )
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForegroundNotification()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      // FIX 1 & 3: Use mainExecutor and newApiCallListener (which is a TelephonyCallback instance)
      telephonyManager?.registerTelephonyCallback(mainExecutor, newApiCallListener!!)
    } else {
      // Use the correct legacy listener
      telephonyManager?.listen(legacyListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    return START_STICKY
  }

  private fun startForegroundNotification() {
    val appName = applicationInfo.loadLabel(packageManager).toString()
    val channelId = "call_detector_channel"

    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(channelId, appName, NotificationManager.IMPORTANCE_LOW)
      notificationManager.createNotificationChannel(channel)
    }

    val notification: Notification =
            NotificationCompat.Builder(this, channelId)
                    .setContentTitle(appName)
                    .setContentText(appName)
                    .setSmallIcon(applicationInfo.icon)
                    .setOngoing(true)
                    .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
    } else {
      startForeground(1, notification)
    }
  }

  private fun handleCallStateChange(state: Int, number: String?) {
    when (state) {
        TelephonyManager.CALL_STATE_RINGING -> {
            currentNumber = number
            sendEvent("Incoming", createCallMap(number, "INCOMING"))
        }
        TelephonyManager.CALL_STATE_OFFHOOK -> {
            sendEvent("Offhook", createCallMap(number, "OUTGOING"))
        }
        TelephonyManager.CALL_STATE_IDLE -> {
            android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(
                    {
                        if (lastState == TelephonyManager.CALL_STATE_OFFHOOK) {
                            val lastCall = fetchLastCallData(currentNumber)
                            sendEvent("Disconnected", lastCall)
                        } else if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                            val lastCall = fetchLastCallData(currentNumber)
                            val callType = lastCall["type"] as? String
                            when (callType) {
                                "MISSED" -> sendEvent("Missed", lastCall)
                                "REJECTED" -> sendEvent("Rejected", lastCall)
                                else -> sendEvent("Disconnected", lastCall)
                            }
                        }
                    },
                    600
                )
        }
    }
    lastState = state
}

  private fun createCallMap(number: String?, type: String): Map<String, Any?> {
    val call = HashMap<String, Any?>()
    call["number"] = number ?: ""
    call["type"] = type
    call["name"] = ""
    call["date"] = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    return call
}

  private fun sendEvent(state: String, data: Map<String, Any?>) {
    val reactApp = application as? ReactApplication
    val reactContext = reactApp?.reactNativeHost?.reactInstanceManager?.currentReactContext

    reactContext?.runOnUiQueueThread {
      if (reactContext.hasActiveReactInstance()) {
        val params = Arguments.createMap()
        params.putString("state", state)
        val callMap = Arguments.createMap()
        data.forEach { (key, value) ->
          when (value) {
            is String -> callMap.putString(key, value)
            is Int -> callMap.putInt(key, value)
            is Double -> callMap.putDouble(key, value)
          }
        }
        params.putMap("call", callMap)
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("CallStateUpdate", params)
      }
    }

    // 🔥 Add this part
    try {
      val broadcastIntent = Intent("CALL_STATE_UPDATE")
      broadcastIntent.putExtra("state", state)
      broadcastIntent.putExtra("callData", JSONObject(data).toString())
      sendBroadcast(broadcastIntent)
    } catch (e: Exception) {
      Log.e("CallStateService", "Broadcast error: ${e.message}")
    }
  }

  private fun fetchLastCallData(number: String?): Map<String, Any?> {
    val resolver: ContentResolver = contentResolver
    val cursor: Cursor? =
        resolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            ),
            null,
            null,
            CallLog.Calls.DATE + " DESC"
        )

    val result = HashMap<String, Any?>()

    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val numberIndex = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeIndex = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIndex = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationIndex = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)

            val name = it.getString(nameIndex)
            val phoneNumber = it.getString(numberIndex)
            val typeCode = it.getInt(typeIndex)
            val date = it.getLong(dateIndex)
            val duration = it.getInt(durationIndex)

            val type =
                when (typeCode) {
                    CallLog.Calls.INCOMING_TYPE -> if (duration == 0) "MISSED" else "INCOMING"
                    CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                    CallLog.Calls.MISSED_TYPE -> "MISSED"
                    CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                    else -> "UNKNOWN"
                }

            result["name"] = name ?: ""
            result["number"] = phoneNumber ?: number ?: ""
            result["type"] = type
            result["date"] =
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(date))
            result["duration"] = duration.toString()
        }
    }

    return result
}

  override fun onDestroy() {
    super.onDestroy()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      // Use the correct newApiCallListener property
      telephonyManager?.unregisterTelephonyCallback(newApiCallListener!!)
    } else {
      // Use the correct legacyListener property
      telephonyManager?.listen(legacyListener, PhoneStateListener.LISTEN_NONE)
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
