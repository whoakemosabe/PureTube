package com.hhst.youtubelite.home

import androidx.lifecycle.SavedStateHandle
import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.home.domain.HomeAction
import com.hhst.youtubelite.home.domain.HomeNavigationRequest
import com.hhst.youtubelite.home.domain.HomePage
import com.hhst.youtubelite.home.domain.HomeRepository
import com.hhst.youtubelite.home.presentation.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** LiTube #380: a channel picture on a home card must open something, even before the feed names the channel. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeChannelPictureTest {
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }

    private fun model(card: FeedItem.Video): HomeViewModel {
        val repo = object : HomeRepository {
            override suspend fun loadHome(filter: String, continuation: String?) = HomePage(listOf(card))
        }
        return HomeViewModel(repo, SavedStateHandle(), loadOnStart = true)
    }

    @Test fun aKnownChannelOpensIt() = runTest {
        val vm = model(FeedItem.Video("video", "Title", "Author", "", "", "", "UCabc"))
        advanceUntilIdle()
        vm.onAction(HomeAction.OpenChannelOf("video"))
        advanceUntilIdle()
        assertEquals(HomeNavigationRequest.Channel("UCabc"), vm.navigationRequests.first())
    }

    @Test fun anUnknownChannelOpensTheVideoInsteadOfABlankChannel() = runTest {
        val vm = model(FeedItem.Video("video", "Title", "Author", "", "", "", ""))
        advanceUntilIdle()
        vm.onAction(HomeAction.OpenChannelOf("video"))
        advanceUntilIdle()
        assertEquals(HomeNavigationRequest.Watch("video"), vm.navigationRequests.first())
    }
}
