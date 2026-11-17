package com.fmsys.snapdrop.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class LanServerScanner {
    public static final String DEFAULT_PORTS = "80,3000";
    private static final int MAX_SCAN_PREFIX_LENGTH = 24;
    private static final int SCAN_WORKERS = 32;
    private static final int CONNECT_TIMEOUT_MILLIS = 250;
    private static final int HTTP_TIMEOUT_MILLIS = 1200;

    private LanServerScanner() {
        // utility class
    }

    public static final class LanNetwork {
        private final int localAddress;
        private final int scanNetworkAddress;
        private final int scanPrefixLength;
        private final String networkKey;

        LanNetwork(final int localAddress, final int scanNetworkAddress, final int scanPrefixLength, final String networkKey) {
            this.localAddress = localAddress;
            this.scanNetworkAddress = scanNetworkAddress;
            this.scanPrefixLength = scanPrefixLength;
            this.networkKey = networkKey;
        }

        public String getNetworkKey() {
            return networkKey;
        }

        List<String> candidateHosts() {
            final List<String> hosts = new ArrayList<>();
            final long mask = prefixMask(scanPrefixLength);
            final long network = Integer.toUnsignedLong(scanNetworkAddress);
            final long broadcast = network | (~mask & 0xffffffffL);
            for (long host = network + 1; host < broadcast; host++) {
                if ((int) host != localAddress) {
                    hosts.add(intToIpv4((int) host));
                }
            }
            return hosts;
        }
    }

    public static LanNetwork getWifiLan(final Context context) {
        final ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return null;
        }

        for (Network network : manager.getAllNetworks()) {
            final NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                continue;
            }

            final LinkProperties properties = manager.getLinkProperties(network);
            if (properties == null) {
                continue;
            }

            for (LinkAddress linkAddress : properties.getLinkAddresses()) {
                if (!(linkAddress.getAddress() instanceof Inet4Address)) {
                    continue;
                }

                final Inet4Address address = (Inet4Address) linkAddress.getAddress();
                if (address.isLoopbackAddress() || address.isLinkLocalAddress()) {
                    continue;
                }

                final int local = ipv4ToInt(address);
                final int actualPrefixLength = Math.max(0, Math.min(32, linkAddress.getPrefixLength()));
                final int scanPrefixLength = Math.max(actualPrefixLength, MAX_SCAN_PREFIX_LENGTH);
                final int actualNetwork = local & (int) prefixMask(actualPrefixLength);
                final int scanNetwork = local & (int) prefixMask(scanPrefixLength);

                String gateway = "";
                for (RouteInfo route : properties.getRoutes()) {
                    final InetAddress routeGateway = route.getGateway();
                    if (route.isDefaultRoute() && routeGateway instanceof Inet4Address) {
                        gateway = routeGateway.getHostAddress();
                        break;
                    }
                }

                final String networkKey = intToIpv4(actualNetwork) + "/" + actualPrefixLength + "|" + gateway;
                return new LanNetwork(local, scanNetwork, scanPrefixLength, networkKey);
            }
        }

        return null;
    }

    public static List<Integer> parsePorts(final String rawPorts) {
        final Set<Integer> ports = new LinkedHashSet<>();
        final String value = rawPorts == null ? DEFAULT_PORTS : rawPorts;
        for (String part : value.split(",")) {
            try {
                final int port = Integer.parseInt(part.trim());
                if (port >= 1 && port <= 65535) {
                    ports.add(port);
                }
            } catch (NumberFormatException ignored) {
                // Ignore invalid entries and keep the remaining configured ports.
            }
        }
        if (ports.isEmpty()) {
            ports.add(80);
            ports.add(3000);
        }
        return new ArrayList<>(ports);
    }

    public static String scan(final LanNetwork network, final List<Integer> ports) {
        if (network == null) {
            return null;
        }

        final ExecutorService pool = Executors.newFixedThreadPool(SCAN_WORKERS);
        final CompletionService<String> completion = new ExecutorCompletionService<>(pool);
        int submitted = 0;

        try {
            for (String host : network.candidateHosts()) {
                for (Integer port : ports) {
                    completion.submit(() -> probe(host, port));
                    submitted++;
                }
            }

            for (int i = 0; i < submitted; i++) {
                final Future<String> completed = completion.take();
                final String found = completed.get();
                if (found != null) {
                    return found;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Log.w("LanServerScanner", "LAN scan failed", e);
        } finally {
            pool.shutdownNow();
            try {
                pool.awaitTermination(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return null;
    }

    private static String probe(final String host, final int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        } catch (Exception e) {
            return null;
        }

        final String url = "http://" + host + (port == 80 ? "" : ":" + port);
        return PairDropInstanceVerifier.isPairDropInstance(url, HTTP_TIMEOUT_MILLIS) ? url : null;
    }

    private static int ipv4ToInt(final Inet4Address address) {
        final byte[] bytes = address.getAddress();
        return ((bytes[0] & 0xff) << 24)
                | ((bytes[1] & 0xff) << 16)
                | ((bytes[2] & 0xff) << 8)
                | (bytes[3] & 0xff);
    }

    private static long prefixMask(final int prefixLength) {
        if (prefixLength == 0) {
            return 0L;
        }
        return (0xffffffffL << (32 - prefixLength)) & 0xffffffffL;
    }

    private static String intToIpv4(final int address) {
        return ((address >>> 24) & 0xff) + "."
                + ((address >>> 16) & 0xff) + "."
                + ((address >>> 8) & 0xff) + "."
                + (address & 0xff);
    }
}
