package id.feezet.jadwalbola;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Jadwal Bola: upcoming fixtures for the top European leagues, as a Clean Your TV module card.
 * Data: TheSportsDB free test API (no account). The free key returns one "next" event per league,
 * so the app reads that event's round + season and then fetches the full round (and the next one).
 * Kick-offs arrive in UTC and are shown in Asia/Jakarta (WIB).
 */
public class MainActivity extends Activity {

    // League display name -> TheSportsDB league id.
    private static final String[][] LEAGUES = {
        {"Premier League", "4328"},
        {"La Liga", "4335"},
        {"Serie A", "4332"},
        {"Bundesliga", "4331"},
        {"Ligue 1", "4334"},
        {"Liga Champions", "4480"},
        {"Liga Europa", "4481"},
    };
    private static final String API = "https://www.thesportsdb.com/api/v1/json/123/";

    private TextView status;
    private FixtureAdapter adapter;

    private static final class Fixture {
        long utcMillis;
        String league;
        String title;   // "Arsenal vs Everton"
        String score;   // "2 - 1" when the match has been played, else null
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(9, 14, 10));
        int pad = dp(48);
        root.setPadding(pad, dp(28), pad, dp(20));

        TextView title = new TextView(this);
        title.setText("Jadwal Bola — Liga Top Eropa");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setText("Memuat jadwal…");
        status.setTextColor(Color.rgb(170, 190, 175));
        status.setTextSize(16);
        status.setPadding(0, dp(6), 0, dp(14));
        root.addView(status);

        ListView list = new ListView(this);
        list.setDivider(null);
        adapter = new FixtureAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        load();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void load() {
        new Thread(() -> {
            List<Fixture> all = new ArrayList<>();
            String error = null;
            for (String[] league : LEAGUES) {
                try {
                    all.addAll(fetchLeague(league[0], league[1]));
                } catch (Exception e) {
                    if (error == null) error = e.getMessage();
                }
            }
            // Window: from 12 hours ago (today's finished matches stay, with scores) to 18 days ahead.
            long now = System.currentTimeMillis();
            long from = now - 12L * 3600 * 1000;
            long to = now + 18L * 24 * 3600 * 1000;
            Map<String, Fixture> byKey = new LinkedHashMap<>();
            for (Fixture f : all) {
                if (f.utcMillis >= from && f.utcMillis <= to) {
                    byKey.put(f.league + "|" + f.title + "|" + f.utcMillis, f);
                }
            }
            List<Fixture> fixtures = new ArrayList<>(byKey.values());
            Collections.sort(fixtures, Comparator.comparingLong(f -> f.utcMillis));
            String err = error;
            runOnUiThread(() -> {
                adapter.setFixtures(fixtures);
                if (fixtures.isEmpty()) {
                    status.setText(err != null
                        ? "Gagal memuat jadwal. Cek koneksi internet TV, lalu buka lagi aplikasi ini."
                        : "Tidak ada pertandingan dalam 18 hari ke depan.");
                } else {
                    status.setText(fixtures.size() + " pertandingan • jam dalam WIB • diperbarui "
                        + new SimpleDateFormat("HH:mm", new Locale("id", "ID")).format(new Date()));
                }
            });
        }).start();
    }

    private List<Fixture> fetchLeague(String name, String leagueId) throws Exception {
        List<Fixture> out = new ArrayList<>();
        JSONObject next = getJson(API + "eventsnextleague.php?id=" + leagueId);
        JSONArray events = next.optJSONArray("events");
        if (events == null || events.length() == 0) return out;
        JSONObject first = events.getJSONObject(0);
        addFixture(out, name, first);
        String round = first.optString("intRound", "");
        String season = first.optString("strSeason", "");
        if (round.isEmpty() || season.isEmpty()) return out;
        // The full round around the next match, plus the round after it when the number parses.
        fetchRound(out, name, leagueId, round, season);
        try {
            int r = Integer.parseInt(round.trim());
            fetchRound(out, name, leagueId, String.valueOf(r + 1), season);
        } catch (NumberFormatException ignored) {
            // Cup rounds are not always plain numbers; the single next event is already in.
        }
        return out;
    }

    private void fetchRound(List<Fixture> out, String name, String leagueId, String round, String season) {
        try {
            JSONObject d = getJson(API + "eventsround.php?id=" + leagueId + "&r=" + round + "&s=" + season);
            JSONArray events = d.optJSONArray("events");
            if (events == null) return;
            for (int i = 0; i < events.length(); i++) addFixture(out, name, events.getJSONObject(i));
        } catch (Exception ignored) {
            // One missing round must not sink the whole schedule.
        }
    }

    private void addFixture(List<Fixture> out, String league, JSONObject e) {
        try {
            String ts = e.optString("strTimestamp", "");
            if (ts.isEmpty()) {
                ts = e.optString("dateEvent", "") + "T" + e.optString("strTime", "00:00:00");
            }
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date when = in.parse(ts);
            if (when == null) return;
            Fixture f = new Fixture();
            f.utcMillis = when.getTime();
            f.league = league;
            f.title = e.optString("strHomeTeam", "?") + " vs " + e.optString("strAwayTeam", "?");
            if (!e.isNull("intHomeScore") && !e.isNull("intAwayScore")) {
                f.score = e.optString("intHomeScore") + " - " + e.optString("intAwayScore");
            }
            out.add(f);
        } catch (Exception ignored) {
            // Skip a malformed event, keep the rest.
        }
    }

    private JSONObject getJson(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", "JadwalBola/1.0 (Android TV)");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        } finally {
            c.disconnect();
        }
        return new JSONObject(sb.toString());
    }

    private final class FixtureAdapter extends BaseAdapter {
        private List<Fixture> items = new ArrayList<>();
        private final SimpleDateFormat dayFmt =
            new SimpleDateFormat("EEE, d MMM", new Locale("id", "ID"));
        private final SimpleDateFormat timeFmt =
            new SimpleDateFormat("HH:mm", new Locale("id", "ID"));

        FixtureAdapter() {
            TimeZone wib = TimeZone.getTimeZone("Asia/Jakarta");
            dayFmt.setTimeZone(wib);
            timeFmt.setTimeZone(wib);
        }

        void setFixtures(List<Fixture> fixtures) {
            items = fixtures;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout row;
            TextView main;
            TextView sub;
            if (convert instanceof LinearLayout) {
                row = (LinearLayout) convert;
                main = (TextView) row.getChildAt(0);
                sub = (TextView) row.getChildAt(1);
            } else {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(18), dp(12), dp(18), dp(12));
                main = new TextView(MainActivity.this);
                main.setTextColor(Color.WHITE);
                main.setTextSize(21);
                main.setTypeface(Typeface.DEFAULT_BOLD);
                sub = new TextView(MainActivity.this);
                sub.setTextColor(Color.rgb(170, 190, 175));
                sub.setTextSize(15);
                row.addView(main);
                row.addView(sub);
                row.setGravity(Gravity.CENTER_VERTICAL);
            }
            Fixture f = items.get(i);
            Date when = new Date(f.utcMillis);
            main.setText(f.score != null ? f.title + "  •  " + f.score : f.title);
            sub.setText(f.league + " • " + dayFmt.format(when) + " • " + timeFmt.format(when) + " WIB"
                + (f.score != null ? " • selesai" : ""));
            return row;
        }
    }
}
