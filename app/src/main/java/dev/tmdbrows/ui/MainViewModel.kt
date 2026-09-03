package dev.tmdbrows.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tmdbrows.channels.ChannelPublisher
import dev.tmdbrows.data.Prefs
import dev.tmdbrows.db.AppDatabase
import dev.tmdbrows.db.CustomTarget
import dev.tmdbrows.db.ListConfig
import dev.tmdbrows.launch.TargetApp
import dev.tmdbrows.launch.Targets
import dev.tmdbrows.sync.SyncScheduler
import dev.tmdbrows.tmdb.ArtStyle
import dev.tmdbrows.tmdb.Artwork
import dev.tmdbrows.tmdb.CatalogEntry
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
    val customTargets: StateFlow<List<CustomTarget>> =
        db.customTargets().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
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
        targets.value = Targets.all(ctx, customTargets.value)
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

    /** Non-null while the ready-made rows browser is open. */
    val catalogOpen = MutableStateFlow(false)

    fun openCatalog() { if (requireKey()) catalogOpen.value = true }
    fun closeCatalog() { catalogOpen.value = false }

    fun addFromCatalog(entry: CatalogEntry) = viewModelScope.launch {
        if (!requireKey()) return@launch
        busy.value = true
        try {
            val base = ListConfig(
                displayName = entry.title, catalogId = entry.id, artStyle = entry.artStyle.id,
                targetPackage = "", sortOrder = db.configs().nextSortOrder()
            )
            val cfg = when {
                entry.preset != null -> base.copy(
                    kind = SourceKind.PRESET.name, presetId = entry.preset.id,
                    presetMediaKind = entry.presetKind.api, presetMaxItems = 40
                )
                entry.spec != null -> base.copy(
                    kind = SourceKind.DISCOVER.name, discoverJson = entry.spec.toJson()
                )
                else -> return@launch
            }
            publish(cfg)
        } catch (e: Exception) {
            toast.value = e.message ?: "Failed to add row"
        } finally { busy.value = false }
    }

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

    // ---- Artwork --------------------------------------------------------------

    val artPattern = MutableStateFlow(prefs.artPattern)
    val artEnabled = MutableStateFlow(prefs.artEnabled)
    val artCheckResult = MutableStateFlow("")

    fun setArtPattern(v: String) { artPattern.value = v; artCheckResult.value = "" }

    fun setArtEnabled(enabled: Boolean) {
        if (enabled && artPattern.value.isBlank()) {
            toast.value = "Paste an artwork URL first"; return
        }
        prefs.artEnabled = enabled
        artEnabled.value = enabled
        if (prefs.tmdbKey.isNotBlank()) SyncScheduler.syncNow(ctx)
    }

    fun saveArtPattern() = viewModelScope.launch {
        prefs.artPattern = artPattern.value
        if (artPattern.value.isBlank()) {
            prefs.artEnabled = false
            artEnabled.value = false
        }
        artCheckResult.value = "Saved"
        if (artEnabled.value) SyncScheduler.syncNow(ctx)
    }

    /** Fetches the pattern for a known title so the user finds out now, not after a sync. */
    fun checkArtPattern() = viewModelScope.launch {
        val pattern = artPattern.value
        if (pattern.isBlank()) { artCheckResult.value = "Paste a URL first"; return@launch }
        val sample = Targets.sampleItem()
        val url = Artwork.fillPattern(pattern, sample)
        if (url == null) { artCheckResult.value = "Pattern needs an id the sample doesn't have"; return@launch }
        artCheckResult.value = "Checking…"
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                }
                val code = conn.responseCode
                val type = conn.contentType ?: ""
                conn.disconnect()
                code to type
            }
        }
        artCheckResult.value = outcome.fold(
            onSuccess = { (code, type) ->
                when {
                    code in 200..299 && type.startsWith("image") -> "Works — returned an image"
                    code in 200..299 -> "Returned $type, not an image. Check the URL."
                    else -> "Provider returned HTTP $code"
                }
            },
            onFailure = { "Couldn't reach it: ${it.message}" }
        )
    }

    fun setRowArtStyle(cfg: ListConfig, style: ArtStyle) = viewModelScope.launch {
        db.configs().update(cfg.copy(artStyle = style.id))
        SyncScheduler.syncNow(ctx, cfg.id)
    }

    // ---- Custom targets -------------------------------------------------------

    /** Non-null while the custom-app editor is open; holds the row being edited or a blank one. */
    val targetEditor = MutableStateFlow<CustomTarget?>(null)
    val targetTestResult = MutableStateFlow("")
    val installedApps = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    fun openTargetEditor(existing: CustomTarget? = null) {
        installedApps.value = Targets.launchableApps(ctx)
        targetTestResult.value = ""
        targetEditor.value = existing ?: CustomTarget(packageName = "", label = "", template = "")
    }

    fun closeTargetEditor() {
        targetEditor.value = null
        targetTestResult.value = ""
    }

    fun updateTargetDraft(transform: (CustomTarget) -> CustomTarget) {
        targetEditor.value = targetEditor.value?.let(transform)
        targetTestResult.value = ""
    }

    /** Fires the template against a known title so the user sees straight away if it works. */
    fun testTarget() {
        val draft = targetEditor.value ?: return
        if (draft.packageName.isBlank()) { targetTestResult.value = "Pick an app first"; return }
        if (draft.template.isBlank()) { targetTestResult.value = "Enter a URI template first"; return }
        val sample = Targets.sampleItem()
        val uri = Targets.fillTemplate(draft.template, sample)
        if (uri == null) { targetTestResult.value = "Template needs an id the sample doesn't have"; return }
        val app = TargetApp(draft.packageName, draft.label, template = draft.template)
        val intent = Targets.intentFor(app, sample) ?: run {
            targetTestResult.value = "Couldn't build a link from that template"; return
        }
        try {
            ctx.startActivity(intent)
            targetTestResult.value = "Opened $uri — check whether the app landed on Inception"
        } catch (e: Exception) {
            targetTestResult.value = "${draft.label.ifBlank { draft.packageName }} rejected $uri"
        }
    }

    fun saveTarget() = viewModelScope.launch {
        val draft = targetEditor.value ?: return@launch
        if (draft.packageName.isBlank() || draft.template.isBlank()) {
            toast.value = "Pick an app and enter a template"; return@launch
        }
        val label = draft.label.ifBlank {
            installedApps.value.firstOrNull { it.first == draft.packageName }?.second ?: draft.packageName
        }
        db.customTargets().upsert(draft.copy(label = label))
        closeTargetEditor()
        refresh()
    }

    fun deleteTarget(t: CustomTarget) = viewModelScope.launch {
        db.customTargets().delete(t)
        closeTargetEditor()
        refresh()
    }

    // ---- Row previews ----------------------------------------------------------

    fun previewFor(configId: Long) = db.items().observePreview(configId, 7)
    fun countFor(configId: Long) = db.items().observeCount(configId)

    /** Which screen the nav rail is showing. */
    val destination = MutableStateFlow(Dest.ROWS)
    fun go(d: Dest) { destination.value = d }

    fun approvalHandled() { channelToApprove.value = null }
    fun toastShown() { toast.value = null }

    /** Genre names for the settings list; loaded lazily so summaries read nicely. */
    fun ensureGenresLoaded() {
        if (genres.value.isEmpty() && prefs.tmdbKey.isNotBlank()) loadReferenceData(MediaKind.MOVIE)
    }
}
