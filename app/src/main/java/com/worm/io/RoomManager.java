package com.worm.io;

import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.Random;
import java.util.UUID;

public class RoomManager {
    static final String ROOM_NODE = "cacing_rooms";
    static final int MAX_PLAYERS = 50;
    static final long STALE_MS = 15000;
    private static final String CODE_CHARS = "0123456789";
    private static final Random RND = new Random();

    public static class Joined {
        public final String code;
        public final String playerId;
        Joined(String code, String playerId) {
            this.code = code;
            this.playerId = playerId;
        }
    }

    public static Joined create(Rtdb rtdb, String name) throws Exception {
        String code;
        do {
            code = randomCode();
        } while (roomExists(rtdb, code));

        String path = ROOM_NODE + "/" + code;
        JSONObject meta = new JSONObject()
                .put("max", MAX_PLAYERS)
                .put("created", System.currentTimeMillis());
        rtdb.request("PUT", path + "/meta", meta.toString());

        String id = newPlayerId();
        putPlayer(rtdb, path, id, name, colorFor(id));
        return new Joined(code, id);
    }

    public static Joined join(Rtdb rtdb, String code, String name) throws Exception {
        if (!roomExists(rtdb, code)) throw new Exception("Room tidak ditemukan");

        String path = ROOM_NODE + "/" + code;
        int active = countActive(rtdb, path);
        if (active == 0) {
            rtdb.request("DELETE", path, null);
            throw new Exception("Room tidak ditemukan");
        }
        if (active >= MAX_PLAYERS) {
            throw new Exception("Room penuh (" + MAX_PLAYERS + "/" + MAX_PLAYERS + ")");
        }

        String id = newPlayerId();
        putPlayer(rtdb, path, id, name, colorFor(id));
        return new Joined(code, id);
    }

    public static void removePlayer(Rtdb rtdb, String roomPath, String id, boolean cleanupRoom) throws Exception {
        rtdb.request("DELETE", roomPath + "/players/" + id, null);
        if (cleanupRoom && countActive(rtdb, roomPath) == 0) {
            rtdb.request("DELETE", roomPath, null);
        }
    }

    public static boolean roomExists(Rtdb rtdb, String code) throws Exception {
        String r = rtdb.request("GET", ROOM_NODE + "/" + code + "/meta", null);
        return r != null && !r.trim().isEmpty() && !r.trim().equals("null");
    }

    static int countActive(Rtdb rtdb, String roomPath) throws Exception {
        JSONObject players = parseObj(rtdb.request("GET", roomPath + "/players", null));
        long now = System.currentTimeMillis();
        int active = 0;
        Iterator<String> it = players.keys();
        while (it.hasNext()) {
            String pid = it.next();
            long t = players.getJSONObject(pid).optLong("t");
            if (now - t > STALE_MS) {
                rtdb.request("DELETE", roomPath + "/players/" + pid, null);
            } else {
                active++;
            }
        }
        return active;
    }

    public static void putPlayer(Rtdb rtdb, String path, String id, String name, int color) throws Exception {
        JSONObject p = new JSONObject()
                .put("n", name)
                .put("c", color)
                .put("x", 3000)
                .put("y", 3000)
                .put("l", 120)
                .put("a", 0)
                .put("s", 0)
                .put("t", System.currentTimeMillis())
                .put("b", new JSONArray());
        rtdb.request("PUT", path + "/players/" + id, p.toString());
    }

    static JSONObject playerJson(String name, int color, float[] s) throws JSONException {
        JSONArray b = new JSONArray();
        for (int i = 5; i < s.length; i++) {
            b.put(Math.round(s[i]));
        }
        return new JSONObject()
                .put("n", name)
                .put("c", color)
                .put("x", Math.round(s[0]))
                .put("y", Math.round(s[1]))
                .put("l", Math.round(s[2]))
                .put("a", Math.round(s[3] * 100f) / 100.0)
                .put("s", Math.round(s[4]))
                .put("t", System.currentTimeMillis())
                .put("b", b);
    }

    static JSONObject parseObj(String r) throws JSONException {
        if (r == null || r.trim().isEmpty() || r.trim().equals("null")) return new JSONObject();
        return new JSONObject(r);
    }

    public static int colorFor(String id) {
        float hue = Math.abs(id.hashCode()) % 360;
        return Color.HSVToColor(new float[]{hue, 0.85f, 1f});
    }

    private static String newPlayerId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(CODE_CHARS.charAt(RND.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}