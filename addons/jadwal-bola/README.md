# Jadwal Bola

Upcoming fixtures for the top European leagues — Premier League, La Liga, Serie A, Bundesliga,
Ligue 1, the Champions League and the Europa League — as a Clean Your TV module card.
Kick-off times are shown in WIB (Asia/Jakarta). Matches from the current round that have
already been played stay on the list with their score until they age out of the window.

Data comes from [TheSportsDB](https://www.thesportsdb.com) free test API (no account or key of
your own). The free key returns one next event per league, so the app reads that event's round
and season and then fetches the full round and the one after it, keeps the next 18 days and
sorts everything by kick-off.

Build and install (JDK 17, Android SDK 34), like the other add-ons:

```bash
cd addons/jadwal-bola && ./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Then open Clean Your TV once: it asks about the new card, answer **Show**, and the
**Jadwal Bola** tile sits on the home screen. The schedule itself opens when the tile is clicked.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Fetch the fixtures from TheSportsDB. Nothing is sent anywhere else; there is no account, no ads and no telemetry. |
| `ACCESS_NETWORK_STATE` | Let Android report whether the TV is online before a fetch. |

No background service: the app only uses the network while it is open, and refreshes on every open.

Tested on: Polytron Android TV (the owner's set, installed and opened over ADB).
