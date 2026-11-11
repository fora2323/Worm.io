package com.worm.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Rtdb {
    private final String baseUrl;

    public Rtdb(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String request(String method, String path, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + "/" + path + ".json").openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            c.setRequestProperty("Connection", "keep-alive");
            if (body != null) {
                byte[] data = body.getBytes("UTF-8");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setFixedLengthStreamingMode(data.length);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(data);
                }
            }
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String res = is == null ? "" : readAll(is);
            if (code >= 400) throw new IOException("HTTP " + code + " " + res);
            return res;
        } finally {
            c.disconnect();
        }
    }

    public static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) bo.write(buf, 0, n);
        is.close();
        return bo.toString("UTF-8");
    }
}