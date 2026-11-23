package com.anamorsyai.alpai.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * A no-op-besides-existing foreground service: its only job is to hold this process at
 * foreground priority so Android is far less likely to kill it while a long build or command
 * is running inside an Alpine session — exactly the case a plain background app process gets
 * reclaimed first. `proot` and the shells it runs are child processes of this same app
 * process, so keeping any one component of the process foreground (this service) protects
 * all of them equally; there's no need to move session ownership into the service itself.
 *
 * Also holds a partial wake lock plus a Wi-Fi lock (both always on in AlpAI v1 — there is no
 * Settings toggle for them yet), so long-running work keeps running — and keeps its LAN
 * connectivity — with the screen off. A foreground service alone only prevents the process
 * being killed; it does NOT stop the CPU suspending or Wi-Fi power-saving.
 *
 * Trimmed from AlpDroid's version: the service talks to the app only through
 * [TerminalSessionRegistry] (via [TerminalSessionRegistryProvider] on the Application) —
 * no plugin jobs, no background servers, no SettingsStore, no direct Application subclass.
 */
class TerminalKeepAliveService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registry(): TerminalSessionRegistry? =
        (application as? TerminalSessionRegistryProvider)?.terminalRegistry

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val registry = registry()
        if (intent == null && (registry?.tabCount ?: 0) == 0) {
            // A null intent means the whole process was killed and this service just got
            // restarted fresh by START_STICKY — proot and every shell were child processes of
            // that same dead process, so there is nothing left to actually keep alive. Without
            // this, the service just re-armed itself (and a real partial wake lock) forever,
            // showing "session running" indefinitely for sessions that no longer exist.
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_EXIT_ALL) {
            // Destroying each session's underlying process here is enough on its own — every
            // tab's reader thread will see the pty hit EOF right after and run the same
            // tab.onExit teardown a normal typed "exit" already goes through, instead of this
            // duplicating that bookkeeping itself and risking getting it out of sync.
            registry?.destroyAllTabs()
            // Normally the reader threads report EOF and the tabs disappear. A wedged session
            // never does — "Exit" must still end it from the user's side, so after a short
            // grace period any tab still listed is torn down outright.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                registry?.removeAllTabsNow()
            }, 2500)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        // Android 12+ can refuse a foreground start made from the background (and 14+ is
        // pickier still). Never crash the service over it: without the foreground priority the
        // process is just as killable as before, so bail out cleanly and let the app retry
        // from its next foreground moment (the manager re-runs updateKeepAliveService on
        // every tab change).
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground refused; will retry when the app is next foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        acquireWifiLock()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        releaseWifiLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlpAI:terminal").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        runCatching {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "AlpAI:wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseWifiLock() {
        runCatching { wifiLock?.let { if (it.isHeld) it.release() } }
        wifiLock = null
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Background sessions", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Keeps Alpine shell sessions and long-running commands alive in the background"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            PendingIntent.getActivity(
                this, 0, launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val exitAll = PendingIntent.getService(
            this, 0, Intent(this, TerminalKeepAliveService::class.java).setAction(ACTION_EXIT_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val tabCount = registry()?.tabCount ?: 0
        val title = if (tabCount == 1) "1 session running" else "$tabCount sessions running"
        // NotificationCompat.Builder rather than the plain platform Notification.Builder(this,
        // channelId) two-arg constructor, which doesn't exist before API 26 (a NoSuchMethodError
        // waiting to happen on the API 24/25 devices this app's own minSdk claims to support) —
        // Compat's single implementation handles the pre-O fallback (no channel concept at all)
        // transparently instead.
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Tap to return to your terminal")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Exit", exitAll)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "AlpAI/KeepAlive"
        private const val CHANNEL_ID = "alpai_keepalive"
        private const val NOTIFICATION_ID = 1
        const val ACTION_EXIT_ALL = "com.anamorsyai.alpai.EXIT_ALL_SESSIONS"
    }
}
