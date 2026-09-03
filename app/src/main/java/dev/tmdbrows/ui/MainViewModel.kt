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
import dev.tmdbrows.tmdb.TmdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val prefs = Prefs(ctx)
    private val db = AppDatabase.get(ctx)

    val configs: StateFlow<List<ListConfig>> =
        db.configs().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val apiKey = MutableStateFlow(prefs.tmdbKey)
    val keyStatus = MutableStateFlow(if (prefs.tmdbKey.isBlank()) "No key saved" else "Key saved")
    val targets = MutableStateFlow<List<TargetApp>>(emptyList())
    val defaultTarget = MutableStateFlow(prefs.defaultTargetPackage)
    val syncMessage = MutableStateFlow(prefs.lastSyncMessage)
    val busy = MutableStateFlow(false)

    /** Channel id that needs the system "enable channel" prompt; consumed by the Activity. */
    val channelToApprove = MutableStateFlow<Long?>(null)

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

    /** Adds a list, creates its channel immediately, then kicks off a sync for it. */
    fun addList(input: String, nameOverride: String, targetPackage: String, onError: (String) -> Unit) = viewModelScope.launch {
        val listId = TmdbClient.parseListId(input)
        if (listId == null) { onError("Enter a TMDB list URL or numeric list ID"); return@launch }
        if (prefs.tmdbKey.isBlank()) { onError("Save a TMDB API key first"); return@launch }
        busy.value = true
        try {
            val name = nameOverride.ifBlank {
                withContext(Dispatchers.IO) { TmdbClient(prefs.tmdbKey).fetchList(listId).name }
            }
            val cfg = ListConfig(
                tmdbListId = listId, displayName = name,
                targetPackage = targetPackage, sortOrder = db.configs().nextSortOrder()
            )
            val id = db.configs().insert(cfg)
            val channelId = withContext(Dispatchers.IO) { ChannelPublisher.ensureChannel(ctx, cfg.copy(id = id)) }
            db.configs().update(cfg.copy(id = id, channelId = channelId))
            channelToApprove.value = channelId
            SyncScheduler.syncNow(ctx, id)
        } catch (e: Exception) {
            onError(e.message ?: "Failed to add list")
        } finally { busy.value = false }
    }

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
            kotlinx.coroutines.delay(4000)
            syncMessage.value = prefs.lastSyncMessage
        }
    }

    fun approvalHandled() { channelToApprove.value = null }
}
