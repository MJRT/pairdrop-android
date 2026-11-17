package com.fmsys.snapdrop.utils;

import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public final class LanDiscoveryCache {
    private static final String PREF_KEY = "lan_discovery_cache_v1";

    private LanDiscoveryCache() {
        // utility class
    }

    public static String get(final SharedPreferences preferences, final String networkKey) {
        if (networkKey == null) {
            return null;
        }
        try {
            final JSONObject cache = new JSONObject(preferences.getString(PREF_KEY, "{}"));
            return cache.optString(networkKey, null);
        } catch (JSONException e) {
            Log.w("LanDiscoveryCache", "Failed to read discovery cache", e);
            return null;
        }
    }

    public static void put(final SharedPreferences preferences, final String networkKey, final String url) {
        if (networkKey == null || url == null) {
            return;
        }
        try {
            final JSONObject cache = new JSONObject(preferences.getString(PREF_KEY, "{}"));
            cache.put(networkKey, url);
            preferences.edit().putString(PREF_KEY, cache.toString()).apply();
        } catch (JSONException e) {
            Log.w("LanDiscoveryCache", "Failed to update discovery cache", e);
        }
    }

    public static void remove(final SharedPreferences preferences, final String networkKey) {
        if (networkKey == null) {
            return;
        }
        try {
            final JSONObject cache = new JSONObject(preferences.getString(PREF_KEY, "{}"));
            cache.remove(networkKey);
            preferences.edit().putString(PREF_KEY, cache.toString()).apply();
        } catch (JSONException e) {
            Log.w("LanDiscoveryCache", "Failed to update discovery cache", e);
        }
    }
}
