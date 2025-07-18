package org.dokiteam.doki.details.ui.pager.pages

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.dokiteam.doki.core.util.ext.getItem
import org.dokiteam.doki.list.ui.MangaSelectionDecoration

class PagesSelectionDecoration(context: Context) : MangaSelectionDecoration(context) {

	override fun getItemId(parent: RecyclerView, child: View): Long {
		val holder = parent.getChildViewHolder(child) ?: return RecyclerView.NO_ID
		val item = holder.getItem(PageThumbnail::class.java) ?: return RecyclerView.NO_ID
		return item.page.id
	}
}
