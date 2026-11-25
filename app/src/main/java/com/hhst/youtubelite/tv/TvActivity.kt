package com.hhst.youtubelite.tv

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
    private val prefs: ExtensionManager by inject()
    private lateinit var settings: ExtensionTvSettings
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
            }
        }
        setContent {
            val signedInAs by account.collectAsStateWithLifecycle()
            TvTheme {
                TvApp(receiver = receiver, engine = engine, browse = browse, player = player, account = signedInAs,
                    settings = settings, version = Updater.installedVersion(this), chat = chat,
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
}
