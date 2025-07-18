package org.dokiteam.doki.remotelist.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import org.dokiteam.doki.R
import org.dokiteam.doki.core.model.MangaSource
import org.dokiteam.doki.core.model.distinctById
import org.dokiteam.doki.core.parser.MangaDataRepository
import org.dokiteam.doki.core.parser.MangaRepository
import org.dokiteam.doki.core.prefs.AppSettings
import org.dokiteam.doki.core.prefs.ListMode
import org.dokiteam.doki.core.util.ext.MutableEventFlow
import org.dokiteam.doki.core.util.ext.call
import org.dokiteam.doki.core.util.ext.getCauseUrl
import org.dokiteam.doki.core.util.ext.printStackTraceDebug
import org.dokiteam.doki.explore.data.MangaSourcesRepository
import org.dokiteam.doki.explore.domain.ExploreRepository
import org.dokiteam.doki.filter.ui.FilterCoordinator
import org.dokiteam.doki.list.domain.MangaListMapper
import org.dokiteam.doki.list.ui.MangaListViewModel
import org.dokiteam.doki.list.ui.model.ButtonFooter
import org.dokiteam.doki.list.ui.model.EmptyState
import org.dokiteam.doki.list.ui.model.ListModel
import org.dokiteam.doki.list.ui.model.LoadingFooter
import org.dokiteam.doki.list.ui.model.LoadingState
import org.dokiteam.doki.list.ui.model.toErrorFooter
import org.dokiteam.doki.list.ui.model.toErrorState
import org.dokiteam.doki.parsers.model.Manga
import org.dokiteam.doki.parsers.util.sizeOrZero
import javax.inject.Inject

private const val FILTER_MIN_INTERVAL = 250L

@HiltViewModel
open class RemoteListViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	mangaRepositoryFactory: MangaRepository.Factory,
	final override val filterCoordinator: FilterCoordinator,
	settings: AppSettings,
	protected val mangaListMapper: MangaListMapper,
	private val exploreRepository: ExploreRepository,
	sourcesRepository: MangaSourcesRepository,
	mangaDataRepository: MangaDataRepository
) : MangaListViewModel(settings, mangaDataRepository), FilterCoordinator.Owner {

	val source = MangaSource(savedStateHandle[RemoteListFragment.ARG_SOURCE])
	val isRandomLoading = MutableStateFlow(false)
	val onOpenManga = MutableEventFlow<Manga>()

	private val repository = mangaRepositoryFactory.create(source)
	private val mangaList = MutableStateFlow<List<Manga>?>(null)
	private val hasNextPage = MutableStateFlow(false)
	private val listError = MutableStateFlow<Throwable?>(null)
	private var loadingJob: Job? = null
	private var randomJob: Job? = null

	override val content = combine(
		mangaList.map { it?.skipNsfwIfNeeded() },
		observeListModeWithTriggers(),
		listError,
		hasNextPage,
	) { list, mode, error, hasNext ->
		buildList(list?.size?.plus(2) ?: 2) {
			when {
				list.isNullOrEmpty() && error != null -> add(
					error.toErrorState(
						canRetry = true,
						secondaryAction = if (error.getCauseUrl().isNullOrEmpty()) 0 else R.string.open_in_browser,
					),
				)

				list == null -> add(LoadingState)
				list.isEmpty() -> add(createEmptyState(canResetFilter = filterCoordinator.isFilterApplied))
				else -> {
					mapMangaList(this, list, mode)
					when {
						error != null -> add(error.toErrorFooter())
						hasNext -> add(LoadingFooter())
						else -> getFooter()?.let(::add)
					}
				}
			}
			onBuildList(this)
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Lazily, listOf(LoadingState))

	init {
		filterCoordinator.observe()
			.debounce(FILTER_MIN_INTERVAL)
			.onEach { filterState ->
				loadingJob?.cancelAndJoin()
				mangaList.value = null
				loadList(filterState, false)
			}.catch { error ->
				listError.value = error
			}.launchIn(viewModelScope)

		launchJob(Dispatchers.Default) {
			sourcesRepository.trackUsage(source)
		}
	}

	override fun onRefresh() {
		loadList(filterCoordinator.snapshot(), append = false)
	}

	override fun onRetry() {
		loadList(filterCoordinator.snapshot(), append = !mangaList.value.isNullOrEmpty())
	}

	fun loadNextPage() {
		if (hasNextPage.value && listError.value == null) {
			loadList(filterCoordinator.snapshot(), append = true)
		}
	}

	protected fun loadList(filterState: FilterCoordinator.Snapshot, append: Boolean): Job {
		loadingJob?.let {
			if (it.isActive) return it
		}
		return launchLoadingJob(Dispatchers.Default) {
			try {
				listError.value = null
				val list = repository.getList(
					offset = if (append) mangaList.value.sizeOrZero() else 0,
					order = filterState.sortOrder,
					filter = filterState.listFilter,
				)
				val prevList = mangaList.value.orEmpty()
				if (!append) {
					mangaList.value = list.distinctById()
				} else if (list.isNotEmpty()) {
					mangaList.value = (prevList + list).distinctById()
				}
				hasNextPage.value = if (append) {
					prevList != mangaList.value
				} else {
					list.size > prevList.size || hasNextPage.value
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				e.printStackTraceDebug()
				listError.value = e
				if (!mangaList.value.isNullOrEmpty()) {
					errorEvent.call(e)
				}
				hasNextPage.value = false
			}
		}.also { loadingJob = it }
	}

	protected open fun createEmptyState(canResetFilter: Boolean) = EmptyState(
		icon = R.drawable.ic_empty_common,
		textPrimary = R.string.nothing_found,
		textSecondary = 0,
		actionStringRes = if (canResetFilter) R.string.reset_filter else 0,
	)

	protected open suspend fun onBuildList(list: MutableList<ListModel>) = Unit

	protected open suspend fun mapMangaList(
		destination: MutableCollection<in ListModel>,
		manga: Collection<Manga>,
		mode: ListMode
	) = mangaListMapper.toListModelList(destination, manga, mode)

	protected open fun getFooter(): ButtonFooter? {
		val filter = filterCoordinator.snapshot().listFilter
		val hasQuery = !filter.query.isNullOrEmpty()
		val hasAuthor = !filter.author.isNullOrEmpty()
		val isOneTag = filter.tags.size == 1
		return if ((hasQuery xor isOneTag xor hasAuthor) && !(hasQuery && isOneTag && hasAuthor)) {
			ButtonFooter(R.string.global_search)
		} else {
			null
		}
	}

	fun openRandom() {
		if (randomJob?.isActive == true) {
			return
		}
		randomJob = launchLoadingJob(Dispatchers.Default) {
			isRandomLoading.value = true
			val manga = exploreRepository.findRandomManga(source, 16)
			onOpenManga.call(manga)
			isRandomLoading.value = false
		}
	}
}
