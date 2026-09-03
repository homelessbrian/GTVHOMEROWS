package dev.tmdbrows.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tmdbrows.channels.ChannelPublisher
import dev.tmdbrows.data.Prefs
import dev.tmdbrows.db.AppDatabase
import dev.tmdbrows.db.ListConfig
import dev.tmdbrows.launch.TargetApp
import dev.tmdbrows.launch.Targets
import dev.tmdbrows.sync.SyncScheduler
import dev.tmdbrows.tmdb.DiscoverSpec
import dev.tmdbrows.tmdb.Genre
import dev.tmdbrows.tmdb.MediaKind
import dev.tmdbrows.tmdb.Preset
import dev.tmdbrows.tmdb.Provider
import dev.tmdbrows.tmdb.SourceKind
import dev.tmdbrows.tmdb.TmdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val prefs = Prefs(ctx)
    private val db = AppDatabase.get(ctx)
    private fun client() = TmdbClient(prefs.tmdbKey)

    val configs: StateFlow<List<ListConfig>> =
        db.configs().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val apiKey = MutableStateFlow(prefs.tmdbKey)
    val keyStatus = MutableStateFlow(if (prefs.tmdbKey.isBlank()) "No key saved" else "Key saved")
    val targets = MutableStateFlow<List<TargetApp>>(emptyList())
    val defaultTarget = MutableStateFlow(prefs.defaultTargetPackage)
    val syncMessage = MutableStateFlow(prefs.lastSyncMessage)
    val busy = MutableStateFlow(false)
    val toast = MutableStateFlow<String?>(null)
    val channelToApprove = MutableStateFlow<Long?>(null)

    // ---- Filter builder state ------------------------------------------------

    /** Non-null while the builder screen is open. */
    val builderSpec = MutableStateFlow<DiscoverSpec?>(null)
    val builderName = MutableStateFlow("")
    val builderTarget = MutableStateFlow("")
    /** Row being edited, or null when creating a new one. */
    val builderEditing = MutableStateFlow<ListConfig?>(null)
    val genres = MutableStateFlow<List<Genre>>(emptyList())
    val providers = MutableStateFlow<List<Provider>>(emptyList())
    val matchCount = MutableStateFlow<String>("")
    private var countJob: Job? = null

    val genreNames: Map<Int, String> get() = genres.value.associate { it.id to it.name }

    fun refresh() {
        targets.value = Targets.installed(ctx)
        if (defaultTarget.value.isBlank() && targets.value.isNotEmpty()) {
            setDefaultTarget(targets.value.first().packageName)
        }
        syncMessage.value = prefs.lastSyncMessage
    }

    fun saveAndTestKey(key: String) = viewModelScope.launch {
        busy.value = true
        keyStatus.value = "Testing…"
        try {
            TmdbClient(key).validate()
            prefs.tmdbKey = key
            apiKey.value = key
            keyStatus.value = "Key works ✓"
        } catch (e: Exception) {
            keyStatus.value = "Failed: ${e.message}"
        } finally { busy.value = false }
    }

    fun setDefaultTarget(pkg: String) {
        prefs.defaultTargetPackage = pkg
        defaultTarget.value = pkg
    }

    // ---- Adding rows ---------------------------------------------------------

    fun addListRow(input: String, nameOverride: String, targetPackage: String) = viewModelScope.launch {
        val listId = TmdbClient.parseListId(input)
        if (listId == null) { toast.value = "Enter a TMDB list URL or numeric list ID"; return@launch }
        if (!requireKey()) return@launch
        busy.value = true
        try {
            val name = nameOverride.ifBlank { client().fetchList(listId).name }
            publish(ListConfig(
                kind = SourceKind.LIST.name, tmdbListId = listId,
                displayName = name, targetPackage = targetPackage,
                sortOrder = db.configs().nextSortOrder()
            ))
        } catch (e: Exception) {
            toast.value = e.message ?: "Failed to add list"
        } finally { busy.value = false }
    }

    fun addPresetRow(preset: Preset, kind: MediaKind, nameOverride: String, targetPackage: String, maxItems: Int) =
        viewModelScope.launch {
            if (!requireKey()) return@launch
            busy.value = true
            try {
                publish(ListConfig(
                    kind = SourceKind.PRESET.name, presetId = preset.id,
                    presetMediaKind = kind.api, presetMaxItems = maxItems,
                    displayName = nameOverride.ifBlank { "${preset.label} · ${kind.label}" },
                    targetPackage = targetPackage, sortOrder = db.configs().nextSortOrder()
                ))
            } catch (e: Exception) {
                toast.value = e.message ?: "Failed to add row"
            } finally { busy.value = false }
        }

    private suspend fun publish(cfg: ListConfig) {
        val id = db.configs().insert(cfg)
        val withId = cfg.copy(id = id)
        val channelId = withContext(Dispatchers.IO) { ChannelPublisher.ensureChannel(ctx, withId) }
        db.configs().update(withId.copy(channelId = channelId))
        channelToApprove.value = channelId
        SyncScheduler.syncNow(ctx, id)
    }

    private fun requireKey(): Boolean {
        if (prefs.tmdbKey.isBlank()) { toast.value = "Save a TMDB API key first"; return false }
        return true
    }

    // ---- Builder -------------------------------------------------------------

    fun openBuilder(existing: ListConfig? = null) {
        if (!requireKey()) return
        builderEditing.value = existing
        val spec = if (existing != null) DiscoverSpec.fromJson(existing.discoverJson)
        else DiscoverSpec(watchRegion = Locale.getDefault().country.ifBlank { "US" })
        builderSpec.value = spec
        builderName.value = existing?.displayName ?: ""
        builderTarget.value = existing?.targetPackage ?: ""
        loadReferenceData(spec.mediaKind)
        requestCount()
    }

    fun closeBuilder() {
        builderSpec.value = null
        builderEditing.value = null
        matchCount.value = ""
    }

    fun updateSpec(transform: (DiscoverSpec) -> DiscoverSpec) {
        val current = builderSpec.value ?: return
        val next = transform(current)
        builderSpec.value = next
        if (next.mediaKind != current.mediaKind) {
            // Genre and provider IDs differ between movies and TV, so start those over.
            builderSpec.value = next.copy(genresInclude = emptyList(), genresExclude = emptyList(), providers = emptyList())
            loadReferenceData(next.mediaKind)
        }
        requestCount()
    }

    private fun loadReferenceData(kind: MediaKind) = viewModelScope.launch {
        genres.value = runCatching { client().genres(kind) }.getOrDefault(emptyList())
        val region = builderSpec.value?.watchRegion ?: "US"
        providers.value = runCatching { client().watchProviders(kind, region).take(24) }.getOrDefault(emptyList())
    }

    /** Debounced live count so rapid D-pad presses don't hammer the API. */
    private fun requestCount() {
        countJob?.cancel()
        val spec = builderSpec.value ?: return
        matchCount.value = "counting…"
        countJob = viewModelScope.launch {
            delay(500)
            val n = runCatching { client().countMatches(spec) }.getOrNull()
            matchCount.value = when {
                n == null -> "couldn't count"
                n == 0 -> "no titles match — loosen a filter"
                n == 1 -> "1 title matches"
                else -> "≈ ${"%,d".format(n)} titles match"
            }
        }
    }

    fun saveBuilder() = viewModelScope.launch {
        val spec = builderSpec.value ?: return@launch
        val name = builderName.value.ifBlank { spec.summary(genreNames).take(40) }
        val editing = builderEditing.value
        busy.value = true
        try {
            if (editing != null) {
                val updated = editing.copy(
                    displayName = name, discoverJson = spec.toJson(), targetPackage = builderTarget.value
                )
                db.configs().update(updated)
                // Criteria changed, so the cached items are stale — rebuild this row.
                withContext(Dispatchers.IO) { db.items().deleteForConfig(updated.id) }
                SyncScheduler.syncNow(ctx, updated.id)
            } else {
                publish(ListConfig(
                    kind = SourceKind.DISCOVER.name, discoverJson = spec.toJson(),
                    displayName = name, targetPackage = builderTarget.value,
                    sortOrder = db.configs().nextSortOrder()
                ))
            }
            closeBuilder()
        } catch (e: Exception) {
            toast.value = e.message ?: "Failed to save"
        } finally { busy.value = false }
    }

    fun duplicateRow(cfg: ListConfig) = viewModelScope.launch {
        publish(cfg.copy(id = 0, channelId = null, displayName = cfg.displayName + " (copy)",
            sortOrder = db.configs().nextSortOrder()))
    }

    // ---- Row management ------------------------------------------------------

    fun setListTarget(cfg: ListConfig, pkg: String) = viewModelScope.launch {
        db.configs().update(cfg.copy(targetPackage = pkg))
    }

    fun removeList(cfg: ListConfig) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            cfg.channelId?.let { ChannelPublisher.deleteChannel(ctx, it) }
            db.items().deleteForConfig(cfg.id)
        }
        db.configs().delete(cfg)
    }

    fun syncAll() {
        SyncScheduler.syncNow(ctx)
        syncMessage.value = "Sync started…"
        viewModelScope.launch {
            delay(4000)
            syncMessage.value = prefs.lastSyncMessage
        }
    }

    fun approvalHandled() { channelToApprove.value = null }
    fun toastShown() { toast.value = null }

    /** Genre names for the settings list; loaded lazily so summaries read nicely. */
    fun ensureGenresLoaded() {
        if (genres.value.isEmpty() && prefs.tmdbKey.isNotBlank()) loadReferenceData(MediaKind.MOVIE)
    }
}
