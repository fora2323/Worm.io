package com.worm.io;

import android.content.Context;
import org.json.JSONObject;
import java.io.InputStream;

public class Config {
    public static final String DEFAULT_DATABASE_URL = "https://cacing-io-default-rtdb.firebaseio.com";

    public static String databaseUrl(Context ctx) {
        try (InputStream is = ctx.getAssets().open("firebase.json")) {
            String json = Rtdb.readAll(is);
            String url = new JSONObject(json).getString("databaseURL");
            return url.replaceAll("/+$", "");
        } catch (Exception e) {
            return DEFAULT_DATABASE_URL;
        }
    }
}