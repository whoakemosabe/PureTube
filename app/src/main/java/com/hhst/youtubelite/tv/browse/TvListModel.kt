package com.hhst.youtubelite.tv.browse

import com.hhst.youtubelite.watch.domain.SignInRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvListState(
    val cards: List<TvCard> = emptyList(),
    val loading: Boolean = false,
    val appending: Boolean = false,
    /** The first page failed; nothing to show but a retry. */
    val failed: Boolean = false,
    /** A later page failed; the rows already shown stay. */
    val appendFailed: Boolean = false,
    val needsAccount: Boolean = false,
    val done: Boolean = false,
)

/**
 * One paged list on a TV screen. Follows the phone's paging rules: loaded rows stay when
 * a later page fails, a cursor is spent only after its page arrived, a repeated cursor
 * ends the list, and a reload drops any answer to an older request.
 */
internal class TvListModel(
    private val scope: CoroutineScope,
    private val load: suspend (cursor: String?) -> TvPage,
) {
    private val _state = MutableStateFlow(TvListState())
    val state: StateFlow<TvListState> = _state.asStateFlow()
    private var next: String? = null
    private val seenCursors = mutableSetOf<String>()
    private var generation = 0L
    private var job: Job? = null

    fun loadFirst() {
        if (_state.value.loading) return
        if (_state.value.cards.isNotEmpty() && !_state.value.failed) return
        reload()
    }

    fun reload() {
        job?.cancel()
        val request = ++generation
        next = null
        seenCursors.clear()
        _state.value = TvListState(loading = true)
        job = scope.launch {
            val result = runCatching { load(null) }
            if (request != generation) return@launch
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            result.fold(
                onSuccess = { page ->
                    next = page.next
                    page.next?.let(seenCursors::add)
                    _state.value = TvListState(cards = page.cards.distinctBy(TvCard::key), done = page.next == null)
                },
                onFailure = { error ->
                    _state.value = TvListState(failed = true, needsAccount = error is SignInRequiredException)
                },
            )
        }
    }

    /** Called as focus nears the end of the list. */
    fun loadMore() {
        val cursor = next ?: return
        val current = _state.value
        if (current.loading || current.appending || current.done) return
        val request = generation
        _state.update { it.copy(appending = true, appendFailed = false) }
        job = scope.launch {
            val result = runCatching { load(cursor) }
            if (request != generation) return@launch
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            result.fold(
                onSuccess = { page ->
                    val following = page.next
                    // A cursor that comes back again would loop forever: the list ends here.
                    val repeated = following != null && !seenCursors.add(following)
                    next = following.takeUnless { repeated }
                    _state.update { state ->
                        val known = state.cards.map(TvCard::key).toSet()
                        state.copy(cards = state.cards + page.cards.filter { it.key !in known }.distinctBy(TvCard::key),
                            appending = false, done = next == null)
                    }
                },
                onFailure = { _state.update { it.copy(appending = false, appendFailed = true) } },
            )
        }
    }
}
