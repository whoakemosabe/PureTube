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
import com.hhst.youtubelite.player.engine.PlaybackApi
import com.hhst.youtubelite.tv.browse.RepositoryTvBrowseSource
import com.hhst.youtubelite.tv.browse.TvBrowseModels
import com.hhst.youtubelite.tvlink.AndroidTvLinkStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

/**
 * Puretube on a TV: the Google TV launcher opens this instead of the phone Home. It browses
 * and plays on its own, and plays what a paired phone sends. It listens
 * only while it is on screen; Android TV won't let an app jump to the front by itself.
 */
class TvActivity : ComponentActivity() {
    private val engine: PlaybackApi by inject()
    private lateinit var receiver: TvReceiver
    private lateinit var browse: TvBrowseModels
    private val account = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as App).startMediaRuntime()
        receiver = TvReceiver(this, engine, AndroidTvLinkStore(this, "tv"))
        browse = TvBrowseModels(lifecycleScope, RepositoryTvBrowseSource(get<ContentRepositories>()))
        lifecycleScope.launch {
            receiver.snapshot.collect { snapshot ->
                if (snapshot.isPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        lifecycleScope.launch {
            MediaSession.changes.collect { identity ->
                browse.identityChanged(identity.generation)
                account.value = if (identity.signedIn) MediaSession.selectedAccount()?.let { it.name ?: it.email } ?: "your account" else null
            }
        }
        setContent {
            val signedInAs by account.collectAsStateWithLifecycle()
            TvTheme {
                TvApp(receiver = receiver, engine = engine, browse = browse, account = signedInAs,
                    onSignOut = MediaSession::signOut, onExit = ::finish)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        receiver.start()
    }

    override fun onStop() {
        // Leaving the app on a TV means someone else wants the screen: pause, and stop listening.
        engine.pause()
        receiver.stop()
        super.onStop()
    }
}
