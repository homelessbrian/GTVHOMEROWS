package dev.tmdbrows.launch

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import dev.tmdbrows.data.Prefs
import dev.tmdbrows.db.AppDatabase
import dev.tmdbrows.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Invisible trampoline. Launcher tiles point at
 *   tmdbrows://open?config=<id>&type=<movie|series>&tmdb=<id>
 * and this forwards to whichever app the user chose for that row.
 */
class LaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        val configId = data?.getQueryParameter("config")?.toLongOrNull()
        val type = data?.getQueryParameter("type")
        val tmdbId = data?.getQueryParameter("tmdb")?.toLongOrNull()

        if (data?.host == "settings" || configId == null || type == null || tmdbId == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish(); return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val ctx = this@LaunchActivity
            val (item, pkg) = withContext(Dispatchers.IO) {
                val db = AppDatabase.get(ctx)
                val cfg = db.configs().get(configId)
                val item = db.items().find(configId, tmdbId, type)
                val pkg = cfg?.targetPackage?.ifBlank { null } ?: Prefs(ctx).defaultTargetPackage
                item to pkg
            }
            if (item == null) { fail("Item not found — try syncing again"); return@launch }

            val target = Targets.find(ctx, pkg) ?: Targets.installed(ctx).firstOrNull()
            if (target == null) { fail("No supported app installed (Stremio or Nuvio)"); return@launch }

            val launch = Targets.intentFor(target, item)
            if (launch == null) { fail("Can't open this item in ${target.label}"); return@launch }
            try {
                startActivity(launch)
            } catch (e: ActivityNotFoundException) {
                fail("${target.label} couldn't open this link")
            }
            finish()
        }
    }

    private fun fail(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }
}
