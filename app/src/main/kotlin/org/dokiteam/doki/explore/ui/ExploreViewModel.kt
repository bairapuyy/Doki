package org.dokiteam.doki.explore.ui

import androidx.collection.LongSet
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.dokiteam.doki.R
import org.dokiteam.doki.core.model.MangaSourceInfo
import org.dokiteam.doki.core.os.AppShortcutManager
import org.dokiteam.doki.core.prefs.AppSettings
import org.dokiteam.doki.core.prefs.observeAsFlow
import org.dokiteam.doki.core.prefs.observeAsStateFlow
import org.dokiteam.doki.core.ui.BaseViewModel
import org.dokiteam.doki.core.ui.util.ReversibleAction
import org.dokiteam.doki.core.util.ext.MutableEventFlow
import org.dokiteam.doki.core.util.ext.call
import org.dokiteam.doki.core.util.ext.combine
import org.dokiteam.doki.explore.data.MangaSourcesRepository
import org.dokiteam.doki.explore.domain.ExploreRepository
import org.dokiteam.doki.explore.ui.model.ExploreButtons
import org.dokiteam.doki.explore.ui.model.MangaSourceItem
import org.dokiteam.doki.explore.ui.model.RecommendationsItem
import org.dokiteam.doki.list.ui.model.EmptyHint
import org.dokiteam.doki.list.ui.model.ListHeader
import org.dokiteam.doki.list.ui.model.ListModel
import org.dokiteam.doki.list.ui.model.LoadingState
import org.dokiteam.doki.list.ui.model.MangaCompactListModel
import org.dokiteam.doki.parsers.model.Manga
import org.dokiteam.doki.parsers.model.MangaSource
import org.dokiteam.doki.parsers.util.runCatchingCancellable
import org.dokiteam.doki.suggestions.domain.SuggestionRepository
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
	private val settings: AppSettings,
	private val suggestionRepository: SuggestionRepository,
	private val exploreRepository: ExploreRepository,
	private val sourcesRepository: MangaSourcesRepository,
	private val shortcutManager: AppShortcutManager,
) : BaseViewModel() {

	val isGrid = settings.observeAsStateFlow(
		key = AppSettings.KEY_SOURCES_GRID,
		scope = viewModelScope + Dispatchers.IO,
		valueProducer = { isSourcesGridMode },
	)

	val isAllSourcesEnabled = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_SOURCES_ENABLED_ALL,
		valueProducer = { isAllSourcesEnabled },
	)

	private val isSuggestionsEnabled = settings.observeAsFlow(
		key = AppSettings.KEY_SUGGESTIONS,
		valueProducer = { isSuggestionsEnabled },
	)

	val onOpenManga = MutableEventFlow<Manga>()
	val onActionDone = MutableEventFlow<ReversibleAction>()
	val onShowSuggestionsTip = MutableEventFlow<Unit>()
	private val isRandomLoading = MutableStateFlow(false)

	val content: StateFlow<List<ListModel>> = isLoading.flatMapLatest { loading ->
		if (loading) {
			flowOf(getLoadingStateList())
		} else {
			createContentFlow()
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, getLoadingStateList())

	init {
		launchJob(Dispatchers.Default) {
			if (!settings.isSuggestionsEnabled && settings.isTipEnabled(TIP_SUGGESTIONS)) {
				onShowSuggestionsTip.call(Unit)
			}
		}
	}

	fun openRandom() {
		if (isRandomLoading.value) {
			return
		}
		launchJob(Dispatchers.Default) {
			isRandomLoading.value = true
			try {
				val manga = exploreRepository.findRandomManga(tagsLimit = 8)
				onOpenManga.call(manga)
			} finally {
				isRandomLoading.value = false
			}
		}
	}

	fun disableSources(sources: Collection<MangaSource>) {
		launchJob(Dispatchers.Default) {
			val rollback = sourcesRepository.setSourcesEnabled(sources, isEnabled = false)
			val message = if (sources.size == 1) R.string.source_disabled else R.string.sources_disabled
			onActionDone.call(ReversibleAction(message, rollback))
		}
	}

	fun requestPinShortcut(source: MangaSource) {
		launchLoadingJob(Dispatchers.Default) {
			shortcutManager.requestPinShortcut(source)
		}
	}

	fun setSourcesPinned(sources: Collection<MangaSource>, isPinned: Boolean) {
		launchJob(Dispatchers.Default) {
			sourcesRepository.setIsPinned(sources, isPinned)
			val message = if (sources.size == 1) {
				if (isPinned) R.string.source_pinned else R.string.source_unpinned
			} else {
				if (isPinned) R.string.sources_pinned else R.string.sources_unpinned
			}
			onActionDone.call(ReversibleAction(message, null))
		}
	}

	fun respondSuggestionTip(isAccepted: Boolean) {
		settings.isSuggestionsEnabled = isAccepted
		settings.closeTip(TIP_SUGGESTIONS)
	}

	fun sourcesSnapshot(ids: LongSet): List<MangaSourceInfo> {
		return content.value.mapNotNull {
			(it as? MangaSourceItem)?.takeIf { x -> x.id in ids }?.source
		}
	}

	private fun createContentFlow() = combine(
		sourcesRepository.observeEnabledSources(),
		getSuggestionFlow(),
		isGrid,
		isRandomLoading,
		isAllSourcesEnabled,
		sourcesRepository.observeHasNewSourcesForBadge(),
	) { content, suggestions, grid, randomLoading, allSourcesEnabled, newSources ->
		buildList(content, suggestions, grid, randomLoading, allSourcesEnabled, newSources)
	}.withErrorHandling()

	private fun buildList(
		sources: List<MangaSourceInfo>,
		recommendation: List<Manga>,
		isGrid: Boolean,
		randomLoading: Boolean,
		allSourcesEnabled: Boolean,
		hasNewSources: Boolean,
	): List<ListModel> {
		val result = ArrayList<ListModel>(sources.size + 3)
		result += ExploreButtons(randomLoading)
		if (recommendation.isNotEmpty()) {
			result += ListHeader(R.string.suggestions, R.string.more, R.id.nav_suggestions)
			result += RecommendationsItem(recommendation.toRecommendationList())
		}
		if (sources.isNotEmpty()) {
			result += ListHeader(
				textRes = R.string.remote_sources,
				buttonTextRes = if (allSourcesEnabled) R.string.manage else R.string.catalog,
				badge = if (!allSourcesEnabled && hasNewSources) "" else null,
			)
			sources.mapTo(result) { MangaSourceItem(it, isGrid) }
		} else {
			result += EmptyHint(
				icon = R.drawable.ic_empty_common,
				textPrimary = R.string.no_manga_sources,
				textSecondary = R.string.no_manga_sources_text,
				actionStringRes = R.string.catalog,
			)
		}
		return result
	}

	private fun getLoadingStateList() = listOf(
		ExploreButtons(isRandomLoading.value),
		LoadingState,
	)

	private fun getSuggestionFlow() = isSuggestionsEnabled.mapLatest { isEnabled ->
		if (isEnabled) {
			runCatchingCancellable {
				suggestionRepository.getRandomList(SUGGESTIONS_COUNT)
			}.getOrDefault(emptyList())
		} else {
			emptyList()
		}
	}

	private fun List<Manga>.toRecommendationList() = map { manga ->
		MangaCompactListModel(
			id = manga.id,
			title = manga.title,
			subtitle = manga.tags.joinToString { it.title },
			coverUrl = manga.coverUrl,
			manga = manga,
			counter = 0,
		)
	}

	companion object {

		private const val TIP_SUGGESTIONS = "suggestions"
		private const val SUGGESTIONS_COUNT = 8
	}
}
