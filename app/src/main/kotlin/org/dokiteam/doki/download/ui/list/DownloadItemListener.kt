package org.dokiteam.doki.download.ui.list

import org.dokiteam.doki.core.ui.list.OnListItemClickListener

interface DownloadItemListener : OnListItemClickListener<DownloadItemModel> {

	fun onCancelClick(item: DownloadItemModel)

	fun onPauseClick(item: DownloadItemModel)

	fun onResumeClick(item: DownloadItemModel)

	fun onSkipClick(item: DownloadItemModel)

	fun onSkipAllClick(item: DownloadItemModel)

	fun onExpandClick(item: DownloadItemModel)
}
