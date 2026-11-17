package com.fmsys.snapdrop.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ServerResolver {
    private static final int SAVED_SERVER_TIMEOUT_MILLIS = 1500;
    private static final int OFFICIAL_SERVER_TIMEOUT_MILLIS = 3000;

    private ServerResolver() {
        // utility class
    }

    public enum Source {
        DISCOVERY_CACHE,
        SAVED_SERVER,
        LAN_SCAN,
        OFFICIAL
    }

    public static final class Resolution {
        private final String url;
        private final Source source;

        Resolution(final String url, final Source source) {
            this.url = url;
            this.source = source;
        }

        public String getUrl() {
            return url;
        }

        public Source getSource() {
            return source;
        }
    }

    public static Resolution resolveStartup(
            final Context context,
            final SharedPreferences preferences,
            final String preferredServer,
            final Set<String> customServers,
            final String officialServer,
            final List<Integer> scanPorts) {
        final LanServerScanner.LanNetwork lan = LanServerScanner.getWifiLan(context);

        final LinkedHashSet<String> savedServers = new LinkedHashSet<>();
        if (preferredServer != null && !officialServer.equals(preferredServer)) {
            savedServers.add(preferredServer);
        }
        if (customServers != null) {
            for (String server : customServers) {
                if (!officialServer.equals(server)) {
                    savedServers.add(server);
                }
            }
        }

        for (String server : savedServers) {
            if (PairDropInstanceVerifier.isPairDropInstance(server, SAVED_SERVER_TIMEOUT_MILLIS)) {
                return new Resolution(server, Source.SAVED_SERVER);
            }
        }

        if (lan != null) {
            final String cachedServer = LanDiscoveryCache.get(preferences, lan.getNetworkKey());
            if (cachedServer != null && PairDropInstanceVerifier.isPairDropInstance(cachedServer, SAVED_SERVER_TIMEOUT_MILLIS)) {
                return new Resolution(cachedServer, Source.DISCOVERY_CACHE);
            }
            if (cachedServer != null) {
                LanDiscoveryCache.remove(preferences, lan.getNetworkKey());
            }

            final String discovered = LanServerScanner.scan(lan, scanPorts);
            if (discovered != null) {
                LanDiscoveryCache.put(preferences, lan.getNetworkKey(), discovered);
                return new Resolution(discovered, Source.LAN_SCAN);
            }
        }

        if (PairDropInstanceVerifier.isPairDropInstance(officialServer, OFFICIAL_SERVER_TIMEOUT_MILLIS)) {
            return new Resolution(officialServer, Source.OFFICIAL);
        }

        return null;
    }

    public static String scanLocal(
            final Context context,
            final SharedPreferences preferences,
            final List<Integer> scanPorts) {
        final LanServerScanner.LanNetwork lan = LanServerScanner.getWifiLan(context);
        if (lan == null) {
            return null;
        }
        final String discovered = LanServerScanner.scan(lan, scanPorts);
        if (discovered != null) {
            LanDiscoveryCache.put(preferences, lan.getNetworkKey(), discovered);
        }
        return discovered;
    }
}
