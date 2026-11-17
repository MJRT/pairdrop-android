package com.fmsys.snapdrop.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class LanServerScannerTest {
    @Test
    public void parsePortsKeepsValidUniquePorts() {
        assertEquals(Arrays.asList(80, 3000), LanServerScanner.parsePorts("80, 3000,80,invalid,0,70000"));
    }

    @Test
    public void parsePortsFallsBackToDefaults() {
        assertEquals(Arrays.asList(80, 3000), LanServerScanner.parsePorts("invalid"));
    }

    @Test
    public void candidateHostsScansCurrentSlash24AndSkipsLocalAddress() {
        final int localAddress = ipv4(192, 168, 1, 5);
        final int networkAddress = ipv4(192, 168, 1, 0);
        final LanServerScanner.LanNetwork network = new LanServerScanner.LanNetwork(
                localAddress, networkAddress, 24, "192.168.1.0/24|192.168.1.1");

        final List<String> hosts = network.candidateHosts();
        assertEquals(253, hosts.size());
        assertEquals("192.168.1.1", hosts.get(0));
        assertEquals("192.168.1.254", hosts.get(hosts.size() - 1));
        assertFalse(hosts.contains("192.168.1.5"));
    }

    private static int ipv4(final int a, final int b, final int c, final int d) {
        return (a << 24) | (b << 16) | (c << 8) | d;
    }
}
