package com.worm.io;

import android.content.Context;
import org.json.JSONObject;
import java.io.InputStream;

public class Config {
    public static String databaseUrl(Context ctx) throws Exception {
        try (InputStream is = ctx.getAssets().open("firebase.json")) {
            String json = Rtdb.readAll(is);
            String url = new JSONObject(json).getString("databaseURL");
            return url.replaceAll("/+$", "");
        }
    }
}