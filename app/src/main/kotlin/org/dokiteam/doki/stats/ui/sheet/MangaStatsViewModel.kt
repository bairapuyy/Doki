package org.dokiteam.doki.stats.ui.sheet

import androidx.collection.MutableIntList
import androidx.collection.emptyIntList
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.dokiteam.doki.core.model.parcelable.ParcelableManga
import org.dokiteam.doki.core.nav.AppRouter
import org.dokiteam.doki.core.ui.BaseViewModel
import org.dokiteam.doki.core.ui.model.DateTimeAgo
import org.dokiteam.doki.core.util.ext.calculateTimeAgo
import org.dokiteam.doki.core.util.ext.require
import org.dokiteam.doki.stats.data.StatsRepository
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class MangaStatsViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val repository: StatsRepository,
) : BaseViewModel() {

	val manga = savedStateHandle.require<ParcelableManga>(AppRouter.KEY_MANGA).manga

	val stats = MutableStateFlow(emptyIntList())
	val startDate = MutableStateFlow<DateTimeAgo?>(null)
	val totalPagesRead = MutableStateFlow(0)

	init {
		launchLoadingJob(Dispatchers.Default) {
			val timeline = repository.getMangaTimeline(manga.id)
			if (timeline.isEmpty()) {
				startDate.value = null
				stats.value = emptyIntList()
			} else {
				val startDay = TimeUnit.MILLISECONDS.toDays(timeline.firstKey())
				val endDay = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
				val res = MutableIntList((endDay - startDay).toInt() + 1)
				for (day in startDay..endDay) {
					val from = TimeUnit.DAYS.toMillis(day)
					val to = TimeUnit.DAYS.toMillis(day + 1)
					res.add(timeline.subMap(from, true, to, false).values.sum())
				}
				stats.value = res
				startDate.value = calculateTimeAgo(Instant.ofEpochMilli(timeline.firstKey()))
			}
		}
		launchLoadingJob(Dispatchers.Default) {
			totalPagesRead.value = repository.getTotalPagesRead(manga.id)
		}
	}
}
