package org.dokiteam.doki.settings.search

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.XmlRes
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.get
import dagger.Reusable
import org.dokiteam.doki.R
import org.dokiteam.doki.backups.ui.periodical.PeriodicalBackupSettingsFragment
import org.dokiteam.doki.core.LocalizedAppContext
import org.dokiteam.doki.settings.AppearanceSettingsFragment
import org.dokiteam.doki.settings.DownloadsSettingsFragment
import org.dokiteam.doki.settings.NetworkSettingsFragment
import org.dokiteam.doki.settings.ProxySettingsFragment
import org.dokiteam.doki.settings.ReaderSettingsFragment
import org.dokiteam.doki.settings.ServicesSettingsFragment
import org.dokiteam.doki.settings.SuggestionsSettingsFragment
import org.dokiteam.doki.settings.about.AboutSettingsFragment
import org.dokiteam.doki.settings.sources.SourcesSettingsFragment
import org.dokiteam.doki.settings.tracker.TrackerSettingsFragment
import org.dokiteam.doki.settings.userdata.UserDataSettingsFragment
import org.dokiteam.doki.settings.userdata.storage.StorageManageSettingsFragment
import javax.inject.Inject

@Reusable
@SuppressLint("RestrictedApi")
class SettingsSearchHelper @Inject constructor(
	@LocalizedAppContext private val context: Context,
) {

	fun inflatePreferences(): List<SettingsItem> {
		val preferenceManager = PreferenceManager(context)
		val result = ArrayList<SettingsItem>()
		preferenceManager.inflateTo(result, R.xml.pref_appearance, emptyList(), AppearanceSettingsFragment::class.java)
		preferenceManager.inflateTo(result, R.xml.pref_sources, emptyList(), SourcesSettingsFragment::class.java)
		preferenceManager.inflateTo(result, R.xml.pref_reader, emptyList(), ReaderSettingsFragment::class.java)
		preferenceManager.inflateTo(result, R.xml.pref_network, emptyList(), NetworkSettingsFragment::class.java)
		preferenceManager.inflateTo(result, R.xml.pref_user_data, emptyList(), UserDataSettingsFragment::class.java)
		preferenceManager.inflateTo(
			result,
			R.xml.pref_storage,
			listOf(context.getString(R.string.data_and_privacy)),
			StorageManageSettingsFragment::class.java,
		)
		preferenceManager.inflateTo(result, R.xml.pref_downloads, emptyList(), DownloadsSettingsFragment::class.java)
		preferenceManager.inflateTo(result, R.xml.pref_tracker, emptyList(), TrackerSettingsFragment::class.java)
		preferenceManager.inflateTo(result, R.xml.pref_services, emptyList(), ServicesSettingsFragment::class.java)
		preferenceManager.inflateTo(result, R.xml.pref_about, emptyList(), AboutSettingsFragment::class.java)
		preferenceManager.inflateTo(
			result,
			R.xml.pref_backup_periodic,
			listOf(context.getString(R.string.data_and_privacy)),
			PeriodicalBackupSettingsFragment::class.java,
		)
		preferenceManager.inflateTo(
			result,
			R.xml.pref_proxy,
			listOf(context.getString(R.string.proxy)),
			ProxySettingsFragment::class.java,
		)
		preferenceManager.inflateTo(
			result,
			R.xml.pref_suggestions,
			listOf(context.getString(R.string.suggestions)),
			SuggestionsSettingsFragment::class.java,
		)
		preferenceManager.inflateTo(
			result,
			R.xml.pref_sources,
			listOf(context.getString(R.string.remote_sources)),
			SourcesSettingsFragment::class.java,
		)
		return result
	}

	private fun PreferenceManager.inflateTo(
		result: MutableList<SettingsItem>,
		@XmlRes resId: Int,
		breadcrumbs: List<String>,
		fragmentClass: Class<out PreferenceFragmentCompat>
	) {
		val screen = inflateFromResource(context, resId, null)
		val screenTitle = screen.title?.toString()
		screen.inflateTo(
			result = result,
			breadcrumbs = if (screenTitle.isNullOrEmpty()) breadcrumbs else breadcrumbs + screenTitle,
			fragmentClass = fragmentClass,
		)
	}

	private fun PreferenceScreen.inflateTo(
		result: MutableList<SettingsItem>,
		breadcrumbs: List<String>,
		fragmentClass: Class<out PreferenceFragmentCompat>
	): Unit = repeat(preferenceCount) { i ->
		val pref = this[i]
		if (pref is PreferenceScreen) {
			val screenTitle = pref.title?.toString()
			pref.inflateTo(
				result = result,
				breadcrumbs = if (screenTitle.isNullOrEmpty()) breadcrumbs else breadcrumbs + screenTitle,
				fragmentClass = fragmentClass,
			)
		} else {
			result.add(
				SettingsItem(
					key = pref.key ?: return@repeat,
					title = pref.title ?: return@repeat,
					breadcrumbs = breadcrumbs,
					fragmentClass = fragmentClass,
				),
			)
		}
	}
}
