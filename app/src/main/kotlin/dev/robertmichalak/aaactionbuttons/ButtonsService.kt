package dev.robertmichalak.aaactionbuttons

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Flat button list for Android Auto via [MediaBrowserServiceCompat].
 *
 * Prefer **custom browse actions** when AA supports them: tap the action on a row to run
 * without opening Now Playing (and without browsable-folder prefetch loops).
 *
 * Fallback when unsupported: [FLAG_PLAYABLE] + [onPlayFromMediaId] (may stay on player UI).
 */
class ButtonsService : MediaBrowserServiceCompat() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)

    private lateinit var store: ButtonStore
    private lateinit var actionRunner: ActionRunner
    private lateinit var session: MediaSessionCompat

    private val busyButtons = ConcurrentHashMap<String, String>()
    private var lastMediaId: String? = null
    private var lastRunAtElapsedMs = 0L
    private var customActionsSupported = false

    private val buttonsChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            notifyChildrenChanged(ROOT_ID)
        }
    }

    override fun onCreate() {
        super.onCreate()
        DebugLog.init(this)
        DebugLog.i("ButtonsService created")
        store = ButtonStore(this)
        actionRunner = ActionRunner(this)

        session = MediaSessionCompat(this, TAG).apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setCallback(SessionCallback())
            isActive = true
        }
        sessionToken = session.sessionToken
        // No "current track" → AA is more likely to open the browse/button list, not Now Playing.
        clearIdleSession()

        val filter = IntentFilter(ButtonStore.ACTION_BUTTONS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(buttonsChangedReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(buttonsChangedReceiver, filter)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(buttonsChangedReceiver)
        serviceJob.cancel()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot? {
        if (!isAllowedCaller(clientPackageName)) {
            Log.w(TAG, "Rejected media browser from unknown package")
            return null
        }

        val actionLimit = readCustomActionLimit(rootHints)
        // Always advertise custom actions. Only treat them as the primary UX when limit > 0.
        customActionsSupported = actionLimit > 0
        val hintKeys = rootHints?.keySet()?.sorted()?.joinToString(",") ?: "(null)"
        DebugLog.i(
            "onGetRoot v${BuildConfig.VERSION_NAME} from=$clientPackageName " +
                "customActionLimit=$actionLimit supported=$customActionsSupported hints=$hintKeys",
        )
        AaDiagnostics.save(
            context = this,
            clientPackage = clientPackageName,
            actionLimit = actionLimit,
            supported = customActionsSupported,
            hintKeys = hintKeys,
        )
        DebugLog.persist(this)

        val extras = Bundle().apply {
            // Always publish the Run action definition so capable hosts can use it.
            putParcelableArrayList(
                MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ROOT_LIST,
                arrayListOf(buildRunActionBundle()),
            )
            putInt(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            )
            putInt(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            )
        }
        return BrowserRoot(ROOT_ID, extras)
    }

    private fun readCustomActionLimit(rootHints: Bundle?): Int {
        if (rootHints == null) return 0
        val key = MediaConstants.BROWSER_ROOT_HINTS_KEY_CUSTOM_BROWSER_ACTION_LIMIT
        if (!rootHints.containsKey(key)) return 0
        // Hosts may put Int or Long; getInt on a Long can wrongly yield 0.
        runCatching {
            val asInt = rootHints.getInt(key, Int.MIN_VALUE)
            if (asInt != Int.MIN_VALUE) return asInt
        }
        runCatching {
            val asLong = rootHints.getLong(key, Long.MIN_VALUE)
            if (asLong != Long.MIN_VALUE) return asLong.toInt()
        }
        return 0
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        if (parentId != ROOT_ID) {
            // Browsable fallback page if the row (not the action) was tapped.
            result.sendResult(mutableListOf(buildHintItem()))
            return
        }
        result.sendResult(buildRootItems())
    }

    override fun onLoadItem(
        itemId: String?,
        result: Result<MediaBrowserCompat.MediaItem>,
    ) {
        if (itemId.isNullOrBlank()) {
            result.sendResult(null)
            return
        }
        val button = store.find(itemId)
        if (button == null) {
            result.sendResult(null)
            return
        }
        result.sendResult(buildButtonItem(button))
    }

    override fun onCustomAction(action: String, extras: Bundle?, result: Result<Bundle>) {
        if (action != ACTION_RUN) {
            DebugLog.w("Unknown custom action=$action")
            result.sendError(null)
            return
        }
        val mediaId = extras?.getString(MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_MEDIA_ITEM_ID)
        DebugLog.i("onCustomAction run mediaId=$mediaId")
        if (mediaId.isNullOrBlank()) {
            result.sendError(
                Bundle().apply {
                    putString(
                        MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_RESULT_MESSAGE,
                        getString(R.string.aa_custom_action_missing_id),
                    )
                },
            )
            return
        }

        result.detach()
        serviceScope.launch {
            val outcomeBundle = Bundle()
            val started = startButtonAction(mediaId, source = "customAction")
            if (!started) {
                outcomeBundle.putString(
                    MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_RESULT_MESSAGE,
                    getString(R.string.aa_custom_action_busy),
                )
                outcomeBundle.putString(
                    MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_RESULT_REFRESH_ITEM,
                    mediaId,
                )
                result.sendResult(outcomeBundle)
                return@launch
            }

            outcomeBundle.putString(
                MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_RESULT_MESSAGE,
                getString(R.string.aa_custom_action_started),
            )
            outcomeBundle.putString(
                MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_RESULT_REFRESH_ITEM,
                mediaId,
            )
            result.sendProgressUpdate(outcomeBundle)

            val button = store.find(mediaId)
            val runOutcome = if (button != null) {
                runCatching { actionRunner.run(button) }
            } else {
                kotlin.Result.failure(IllegalStateException("Unknown button"))
            }

            busyButtons.remove(mediaId)
            notifyChildrenChanged(ROOT_ID)
            clearIdleSession()
            DebugLog.persist(this@ButtonsService)

            val done = Bundle()
            done.putString(
                MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_RESULT_REFRESH_ITEM,
                mediaId,
            )
            if (runOutcome.isSuccess) {
                DebugLog.i("Action completed OK (customAction)")
                done.putString(
                    MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_RESULT_MESSAGE,
                    getString(R.string.aa_custom_action_done, button?.label.orEmpty()),
                )
                result.sendResult(done)
            } else {
                val error = runOutcome.exceptionOrNull()
                DebugLog.e("Action failed (customAction)", error)
                done.putString(
                    MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_RESULT_MESSAGE,
                    getString(R.string.aa_custom_action_failed),
                )
                result.sendError(done)
            }
        }
    }

    private fun buildRunActionBundle(): Bundle {
        val iconUri = Uri.Builder()
            .scheme("android.resource")
            .authority(packageName)
            .appendPath("drawable")
            .appendPath("ic_run")
            .build()
            .toString()
        return Bundle().apply {
            putString(MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID, ACTION_RUN)
            putString(
                MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_LABEL,
                getString(R.string.aa_custom_action_run),
            )
            putString(MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ICON_URI, iconUri)
        }
    }

    private fun buildRootItems(): MutableList<MediaBrowserCompat.MediaItem> {
        return store.load().map { buildButtonItem(it) }.toMutableList()
    }

    private fun buildButtonItem(button: dev.robertmichalak.aaactionbuttons.model.ButtonConfig): MediaBrowserCompat.MediaItem {
        val busy = busyButtons.containsKey(button.id)
        val title = if (busy) {
            getString(R.string.aa_button_running, button.label)
        } else {
            button.label
        }
        val extras = localContentExtras()
        if (customActionsSupported) {
            extras.putStringArrayList(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST,
                arrayListOf(ACTION_RUN),
            )
            extras.putInt(
                MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
            )
        }
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(button.id)
            .setTitle(title)
            .setExtras(extras)
            .build()

        val flags = if (customActionsSupported) {
            // Browsable row + custom action. Prefetch of children is safe (hint only, no Tasker).
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        } else {
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        }
        return MediaBrowserCompat.MediaItem(description, flags)
    }

    private fun buildHintItem(): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(HINT_MEDIA_ID)
            .setTitle(getString(R.string.aa_use_run_action_title))
            .setSubtitle(getString(R.string.aa_use_run_action_subtitle))
            .setExtras(localContentExtras())
            .build()
        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE,
        )
    }

    private fun localContentExtras(): Bundle {
        return Bundle().apply {
            putLong(
                MediaDescriptionCompat.EXTRA_DOWNLOAD_STATUS,
                MediaDescriptionCompat.STATUS_DOWNLOADED,
            )
        }
    }

    private fun isAllowedCaller(packageName: String): Boolean {
        return CallerAllowlist.allows(
            callerPackage = packageName,
            selfPackage = applicationContext.packageName,
            debugBuild = BuildConfig.DEBUG,
        )
    }

    /**
     * @return true if this call claimed the busy slot and should run the action.
     */
    private fun startButtonAction(mediaId: String, source: String): Boolean {
        val button = store.find(mediaId) ?: return false
        val now = SystemClock.elapsedRealtime()
        if (mediaId == lastMediaId && now - lastRunAtElapsedMs < DOUBLE_FIRE_MS) {
            DebugLog.i("Ignoring duplicate from $source for id=$mediaId")
            return false
        }
        if (busyButtons.putIfAbsent(button.id, button.label) != null) {
            DebugLog.i("Ignoring $source; button already busy id=$mediaId")
            return false
        }
        lastMediaId = mediaId
        lastRunAtElapsedMs = now
        // Keep metadata empty so we don't pin Now Playing; busy state is only in the browse list.
        clearIdleSession()
        notifyChildrenChanged(ROOT_ID)
        DebugLog.i("Start from $source id=${button.id} label='${button.label}'")
        return true
    }

    private fun runButtonFromPlay(mediaId: String?) {
        if (mediaId.isNullOrBlank() || mediaId == HINT_MEDIA_ID) {
            clearIdleSession()
            return
        }
        if (customActionsSupported) {
            // Playable fallback path disabled while custom actions are available.
            DebugLog.i("Ignoring play; use custom Run action mediaId=$mediaId")
            clearIdleSession()
            return
        }
        if (!startButtonAction(mediaId, source = "play")) {
            clearIdleSession()
            return
        }
        val button = store.find(mediaId) ?: return
        serviceScope.launch {
            try {
                val outcome = runCatching { actionRunner.run(button) }
                outcome.onFailure { error -> DebugLog.e("Action failed", error) }
                outcome.onSuccess { DebugLog.i("Action completed OK") }
            } finally {
                busyButtons.remove(button.id)
                clearIdleSession()
                notifyChildrenChanged(ROOT_ID)
                DebugLog.persist(this@ButtonsService)
            }
        }
    }

    /**
     * Idle session with no current item. [STATE_STOPPED] + a title makes AA treat us as a player
     * and often open Now Playing; [STATE_NONE] + empty metadata favors the browse list.
     */
    private fun clearIdleSession() {
        session.setMetadata(MediaMetadataCompat.Builder().build())
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)
                .setState(PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)
                .build(),
        )
    }

    // Button list, not a searchable media catalog.
    @SuppressLint("MissingOnPlayFromSearch")
    private inner class SessionCallback : MediaSessionCompat.Callback() {
        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            runButtonFromPlay(mediaId)
        }

        override fun onPlay() {
            DebugLog.i("onPlay ignored (no re-trigger)")
            clearIdleSession()
        }

        override fun onPause() {
            clearIdleSession()
        }

        override fun onStop() {
            clearIdleSession()
        }
    }

    companion object {
        private const val TAG = "AaActionButtons"
        private const val DOUBLE_FIRE_MS = 750L
        private const val ACTION_RUN = "dev.robertmichalak.aaactionbuttons.action.RUN"
        private const val HINT_MEDIA_ID = "__hint_use_run__"
        const val ROOT_ID = "root"
    }
}
