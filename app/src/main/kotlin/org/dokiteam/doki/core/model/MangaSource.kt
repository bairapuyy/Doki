package org.dokiteam.doki.core.model

import android.content.Context
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.text.inSpans
import org.dokiteam.doki.R
import org.dokiteam.doki.core.parser.external.ExternalMangaSource
import org.dokiteam.doki.core.util.ext.getDisplayName
import org.dokiteam.doki.core.util.ext.toLocale
import org.dokiteam.doki.core.util.ext.toLocaleOrNull
import org.dokiteam.doki.parsers.model.ContentType
import org.dokiteam.doki.parsers.model.MangaParserSource
import org.dokiteam.doki.parsers.model.MangaSource
import org.dokiteam.doki.parsers.util.splitTwoParts
import java.util.Locale

data object LocalMangaSource : MangaSource {
	override val name = "LOCAL"
}

data object UnknownMangaSource : MangaSource {
	override val name = "UNKNOWN"
}

fun MangaSource(name: String?): MangaSource {
	when (name ?: return UnknownMangaSource) {
		UnknownMangaSource.name -> return UnknownMangaSource

		LocalMangaSource.name -> return LocalMangaSource
	}
	if (name.startsWith("content:")) {
		val parts = name.substringAfter(':').splitTwoParts('/') ?: return UnknownMangaSource
		return ExternalMangaSource(packageName = parts.first, authority = parts.second)
	}
	MangaParserSource.entries.forEach {
		if (it.name == name) return it
	}
	return UnknownMangaSource
}

fun Collection<String>.toMangaSources() = map(::MangaSource)

fun MangaSource.isNsfw(): Boolean = when (this) {
	is MangaSourceInfo -> mangaSource.isNsfw()
	is MangaParserSource -> contentType == ContentType.HENTAI
	else -> false
}

@get:StringRes
val ContentType.titleResId
	get() = when (this) {
		ContentType.MANGA -> R.string.content_type_manga
		ContentType.HENTAI -> R.string.content_type_hentai
		ContentType.COMICS -> R.string.content_type_comics
		ContentType.OTHER -> R.string.content_type_other
		ContentType.MANHWA -> R.string.content_type_manhwa
		ContentType.MANHUA -> R.string.content_type_manhua
		ContentType.NOVEL -> R.string.content_type_novel
		ContentType.ONE_SHOT -> R.string.content_type_one_shot
		ContentType.DOUJINSHI -> R.string.content_type_doujinshi
		ContentType.IMAGE_SET -> R.string.content_type_image_set
		ContentType.ARTIST_CG -> R.string.content_type_artist_cg
		ContentType.GAME_CG -> R.string.content_type_game_cg
	}

tailrec fun MangaSource.unwrap(): MangaSource = if (this is MangaSourceInfo) {
	mangaSource.unwrap()
} else {
	this
}

fun MangaSource.getLocale(): Locale? = (unwrap() as? MangaParserSource)?.locale?.toLocaleOrNull()

fun MangaSource.getSummary(context: Context): String? = when (val source = unwrap()) {
	is MangaParserSource -> {
		val type = context.getString(source.contentType.titleResId)
		val locale = source.locale.toLocale().getDisplayName(context)
		context.getString(R.string.source_summary_pattern, type, locale)
	}

	is ExternalMangaSource -> context.getString(R.string.external_source)

	else -> null
}

fun MangaSource.getTitle(context: Context): String = when (val source = unwrap()) {
	is MangaParserSource -> source.title
	LocalMangaSource -> context.getString(R.string.local_storage)
	is ExternalMangaSource -> source.resolveName(context)
	else -> context.getString(R.string.unknown)
}

fun SpannableStringBuilder.appendIcon(textView: TextView, @DrawableRes resId: Int): SpannableStringBuilder {
	val icon = ContextCompat.getDrawable(textView.context, resId) ?: return this
	icon.setTintList(textView.textColors)
	val size = textView.lineHeight
	icon.setBounds(0, 0, size, size)
	val alignment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
		ImageSpan.ALIGN_CENTER
	} else {
		ImageSpan.ALIGN_BOTTOM
	}
	return inSpans(ImageSpan(icon, alignment)) { append(' ') }
}
