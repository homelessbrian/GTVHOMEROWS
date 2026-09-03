# TMDB Rows

Builds rows on the Android TV / Google TV home screen from TMDB, in three flavours:

- **Filtered rows** — define your own criteria in the app (genres in/out, year range, minimum
  rating with a vote floor, runtime, streaming service, original language, sort order) and the
  row stays current on its own via TMDB's Discover API. A live "≈ N titles match" counter
  updates as you adjust filters so you're not flying blind.
- **Trending / popular** — TMDB's ready-made charts: trending this week or today, popular,
  top rated, in theatres, upcoming, airing today, on the air.
- **TMDB lists** — a specific list by URL or ID, exactly as before.

Selecting a tile opens the title in Stremio or Nuvio — configurable globally and per row.

## Build the APK (no local tools needed)

1. Create a new GitHub repository (public or private).
2. Upload this whole folder (drag-and-drop in the browser works — make sure `.github/workflows/build.yml` is included).
3. Open the **Actions** tab. The "Build APK" workflow runs automatically on push (~4 min).
4. When it finishes, scroll to **Artifacts** and download `TmdbRows-debug-apk`. Unzip it to get `app-debug.apk`.

## Install on the TV

Any of:
- **Downloader** app on the TV → paste a link to the APK (e.g. upload it to a GitHub release, Google Drive direct link, etc.)
- `adb install app-debug.apk` from a computer on the same network (enable Developer options → USB/Network debugging on the TV)
- Send Files to TV / a file-manager app from a USB stick

## First run

1. Open **TMDB Rows** from the launcher.
2. Paste your TMDB API key (themoviedb.org → Settings → API; either the v3 key or the v4 read token works) and press **Save & test**.
3. Pick the default app to open titles in.
4. Add a row:
   - **Build a filter** → set your criteria, watch the match count, name it, create.
   - **Trending / popular** → pick a chart and content type.
   - **From a TMDB list** → paste a list URL like `https://www.themoviedb.org/list/8231164` or just the ID.
   Filtered rows can be edited later ("Edit filters") or copied ("Duplicate") to make variants.
5. Android TV will prompt you to enable the new channel — accept.
6. The row fills in within a few seconds; it refreshes every 6 hours (or use **Sync now**).

If a row doesn't show up, check the launcher's channel/row settings and enable it there
(in Projectivy: Settings → Home → Manage channels; in Monet: Home screen → Customize rows).

## How it works

- `tmdb/DiscoverSpec.kt` — the user's criteria, and the translation into TMDB `/discover` query parameters.
- `tmdb/TmdbClient.kt` — lists (v3 with v4 fallback), `/discover`, the trending/popular endpoints, genre and watch-provider reference data, and IMDb ids. Posters come straight from `image.tmdb.org`.
- `channels/ChannelPublisher.kt` — publishes one Android TV **channel** per list and one **PreviewProgram** per title via `androidx.tvprovider`.
- `sync/SyncWorker.kt` — WorkManager job that refreshes everything and diffs against the current tiles.
- `launch/LaunchActivity.kt` — invisible trampoline that a tile opens; looks up the row's target app and forwards a deep link:
  - Stremio: `stremio:///detail/movie/tt…/tt…` or `stremio:///detail/series/tt…`
  - Nuvio: `nuvio://tmdb/movie/<id>` or `nuvio://tmdb/series/<id>`
- `ui/` — Compose settings screen plus the D-pad-friendly filter builder (chips for multi-select, ◀/▶ steppers instead of text entry for numbers).

The API key is stored with `EncryptedSharedPreferences` and never leaves the device except in requests to TMDB.
