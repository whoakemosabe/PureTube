package com.hhst.youtubelite.tv

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hhst.youtubelite.App
import com.hhst.youtubelite.data.media.ContentRepositories
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.data.media.PreferencesAuthStore
import com.hhst.youtubelite.account.data.MediaSignInService
import okhttp3.OkHttpClient
import com.hhst.youtubelite.player.engine.PlaybackApi
import com.hhst.youtubelite.tv.browse.RepositoryTvBrowseSource
import com.hhst.youtubelite.tv.browse.TvBrowseModels
import com.hhst.youtubelite.tvlink.AndroidTvLinkStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.getKoin
import com.hhst.youtubelite.data.media.PlaybackWatchReporting
import org.koin.android.ext.android.inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hhst.youtubelite.downloader.ui.DownloadUi
import com.hhst.youtubelite.extension.ExtensionManager
import com.hhst.youtubelite.home.domain.FeedVisibility
import com.hhst.youtubelite.tv.browse.ExtensionTvSettings
import com.hhst.youtubelite.tv.browse.LIST_KEYS
import com.hhst.youtubelite.update.UpdateWatch
import com.hhst.youtubelite.update.Updater
import com.hhst.youtubelite.watch.chat.ChatWebSessionStore
import com.hhst.youtubelite.watch.chat.LiveChatViewModel
import com.hhst.youtubelite.watch.chat.MediaLiveChatGateway
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.hhst.youtubelite.core.JsonCache

/**
 * Puretube on a TV: the Google TV launcher opens this instead of the phone Home. It browses
 * and plays on its own, and plays what a paired phone sends. It listens
 * only while it is on screen; Android TV won't let an app jump to the front by itself.
 */
class TvActivity : ComponentActivity() {
    private val engine: PlaybackApi by inject()
    private lateinit var receiver: TvReceiver
    private lateinit var browse: TvBrowseModels
    private lateinit var player: TvPlayerSession
    private val account = MutableStateFlow<String?>(null)
    private val accountPhoto = MutableStateFlow<String?>(null)
    private val prefs: ExtensionManager by inject()
    private lateinit var settings: ExtensionTvSettings
    private val captions: TvCaptionStyleStore by lazy { CachedCaptionStyle(get<JsonCache>()) }
    private val chat: LiveChatViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LiveChatViewModel(
                MediaLiveChatGateway(get<ContentRepositories>().client, ChatWebSessionStore(PreferencesAuthStore(this@TvActivity))),
                signedIn = MediaSession.signedIn(),
            ) as T
        })[LiveChatViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as App).startMediaRuntime()
        com.hhst.youtubelite.player.datasource.CodecCapabilities.preferHardware = true
        receiver = TvReceiver(this, engine, AndroidTvLinkStore(this, "tv"))
        player = TvPlayerSession(lifecycleScope, engine, RepositoryTvVideoOps(get<ContentRepositories>()), getKoin().getOrNull<PlaybackWatchReporting>())
        browse = TvBrowseModels(lifecycleScope, RepositoryTvBrowseSource(get<ContentRepositories>()) { FeedVisibility.fromPreferences(prefs) })
        settings = ExtensionTvSettings(prefs)
        lifecycleScope.launch {
            // Hide Shorts and Hide watched change what the lists hold: read them again.
            settings.values.map { values -> LIST_KEYS.associateWith { values[it] } }.distinctUntilChanged().drop(1)
                .collect { browse.reloadLists() }
        }
        lifecycleScope.launch {
            receiver.snapshot.collect { snapshot ->
                if (snapshot.isPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        lifecycleScope.launch {
            MediaSession.changes.collect { identity ->
                browse.identityChanged(identity.generation)
                chat.updateSession(identity.signedIn, identity.generation)
                account.value = if (identity.signedIn) MediaSession.selectedAccount()?.let { it.name ?: it.email } ?: "your account" else null
                accountPhoto.value = if (identity.signedIn) MediaSession.selectedAccount()?.photo else null
            }
        }
        TvHomeRows.videoOf(intent)?.let { receiver.playLocal(it, "", null) }
        setContent {
            val signedInAs by account.collectAsStateWithLifecycle()
            val photo by accountPhoto.collectAsStateWithLifecycle()
            TvTheme {
                TvApp(receiver = receiver, engine = engine, browse = browse, player = player, account = signedInAs, accountPhoto = photo,
                    settings = settings, version = Updater.installedVersion(this), chat = chat, captions = captions,
                    onSignOut = MediaSession::signOut,
                    openSignIn = { TvSignInSession(MediaSignInService(get<OkHttpClient>(), PreferencesAuthStore(this))) },
                    onOpenDownloads = { DownloadUi.openManager(this) },
                    onDownload = { id, title, author, artwork -> DownloadUi.showSingleConfirm(this, id, title, author, artwork) },
                    onExit = ::finish)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        receiver.start()
        player.start()
        // The TV has no Home screen of the phone's, so it asks about updates here.
        UpdateWatch.onHomeShown(this)
        refreshHomeRows()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        TvHomeRows.videoOf(intent)?.let { receiver.playLocal(it, "", null) }
    }

    private var homeRowsAt = 0L

    /**
     * Puts fresh Home picks and half-watched videos on the Google TV home screen. It reuses
     * the lists the TV's own Home reads (no second fetch) and runs at most every half hour.
     */
    private fun refreshHomeRows() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (homeRowsAt != 0L && now - homeRowsAt < HOME_ROWS_EVERY_MS) return
        homeRowsAt = now
        lifecycleScope.launch {
            val home = settled(browse.list(com.hhst.youtubelite.tv.browse.TvSection.Home))
            val watching = if (browse.signedIn) settled(browse.list(com.hhst.youtubelite.tv.browse.TvSection.History))
                .filter(TvHomeRows::inProgress) else emptyList()
            if (home.isNotEmpty() || watching.isNotEmpty()) TvHomeRows.publish(this@TvActivity, home, watching)
        }
    }

    /** The list's first page once it is in, or nothing if it fails or takes too long. */
    private suspend fun settled(model: com.hhst.youtubelite.tv.browse.TvListModel): List<com.hhst.youtubelite.tv.browse.TvCard> {
        model.loadFirst()
        return kotlinx.coroutines.withTimeoutOrNull(HOME_ROWS_WAIT_MS) {
            model.state.first { !it.loading && (it.cards.isNotEmpty() || it.failed || it.done) }
        }?.cards.orEmpty()
    }

    override fun onDestroy() {
        settings.close()
        super.onDestroy()
    }

    override fun onStop() {
        // Leaving the app on a TV means someone else wants the screen: pause, and stop listening.
        engine.pause()
        receiver.stop()
        player.stop()
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // In the background, give the thumbnails back, so Android keeps the app around and
        // the next open is warm instead of a cold start.
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) com.hhst.youtubelite.core.YoutubeThumbnail.clearMemory()
    }
}

private const val HOME_ROWS_EVERY_MS = 30 * 60_000L
private const val HOME_ROWS_WAIT_MS = 20_000L
