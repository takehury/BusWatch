package ru.krsk.buswatch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;

/** Minimal HTTPS GET client (no third-party dependencies). */
public final class Http {

    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private Http() {
    }

    public static String get(String url, int connectMs, int readMs) throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(connectMs);
            c.setReadTimeout(readMs);
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", "KrskBusWatch/1.0 (Android; watch)");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Accept-Encoding", "gzip");
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " for " + url);
            }
            InputStream raw = c.getInputStream();
            String enc = c.getContentEncoding();
            InputStream in = enc != null && enc.toLowerCase().contains("gzip")
                    ? new GZIPInputStream(raw) : raw;
            ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BYTES) throw new IOException("response too large");
                out.write(buf, 0, n);
            }
            in.close();
            return new String(out.toByteArray(), "UTF-8");
        } catch (IOException ex) {
            if (c != null) c.disconnect();
            throw ex;
        }
    }
}
