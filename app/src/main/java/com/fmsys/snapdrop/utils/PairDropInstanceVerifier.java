package com.fmsys.snapdrop.utils;

import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public final class PairDropInstanceVerifier {
    private PairDropInstanceVerifier() {
        // utility class
    }

    public static boolean isPairDropInstance(final String url, final int timeoutMillis) {
        try {
            final Document doc = Jsoup.connect(url)
                    .timeout(timeoutMillis)
                    .followRedirects(true)
                    .get();
            return doc.selectFirst("x-peers") != null;
        } catch (Exception e) {
            Log.d("PairDropVerifier", "Instance check failed for " + url + ": " + e.getMessage());
            return false;
        }
    }
}
