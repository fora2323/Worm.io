package com.worm.io;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class GameActivity extends Activity {
    public static final String EXTRA_SINGLE = "single";
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_ID = "id";
    private static final long SYNC_DELAY_MS = 150;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private GameView view;
    private Rtdb rtdb;
    private boolean singlePlayer;
    private String code, myId, myName, roomPath;
    private int myColor;
    private volatile boolean syncing = true;
    private boolean leaving;
    private Thread syncThread;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        hideSystemUI();

        singlePlayer = getIntent().getBooleanExtra(EXTRA_SINGLE, false);
        myName = getIntent().getStringExtra(EXTRA_NAME);
        if (myName == null || myName.isEmpty()) myName = "Pemain";

        if (singlePlayer) {
            myId = UUID.randomUUID().toString();
            myColor = RoomManager.colorFor(myName + System.currentTimeMillis());
            view = new GameView(this);
            view.setPlayer(myName, myColor);
            view.setMode(true, null);
            view.setListener(this::onDied);
            setContentView(view);
            view.start();
            return;
        }

        code = getIntent().getStringExtra(EXTRA_CODE);
        myId = getIntent().getStringExtra(EXTRA_ID);
        roomPath = RoomManager.ROOM_NODE + "/" + code;
        myColor = RoomManager.colorFor(myId);

        try {
            rtdb = new Rtdb(Config.databaseUrl(this));
        } catch (Exception e) {
            Toast.makeText(this, "Config error", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        view = new GameView(this);
        view.setPlayer(myName, myColor);
        view.setMode(false, code);
        view.setListener(this::onDied);
        setContentView(view);
        view.start();

        syncThread = new Thread(this::syncLoop, "rtdb-sync");
        syncThread.start();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    private void hideSystemUI() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private void syncLoop() {
        while (syncing) {
            try {
                if (view != null && view.isAlive()) {
                    rtdb.request("PUT", roomPath + "/players/" + myId,
                            RoomManager.playerJson(myName, myColor, view.snapshot()).toString());
                }

                String res = rtdb.request("GET", roomPath + "/players", null);
                JSONObject all = RoomManager.parseObj(res);
                List<GameView.Remote> list = new ArrayList<>();
                long now = System.currentTimeMillis();

                Iterator<String> it = all.keys();
                while (it.hasNext()) {
                    String pid = it.next();
                    if (pid.equals(myId)) continue;

                    JSONObject p = all.optJSONObject(pid);
                    if (p == null) continue;

                    if (now - p.optLong("t", 0) > RoomManager.STALE_MS) {
                        try {
                            rtdb.request("DELETE", roomPath + "/players/" + pid, null);
                        } catch (Exception ignored) {}
                        continue;
                    }

                    JSONArray b = p.optJSONArray("b");
                    int n = b == null ? 0 : b.length() / 2;
                    float[] pts = new float[n * 2];
                    for (int i = 0; i < n * 2; i++) pts[i] = (float) b.optDouble(i);

                    float headX = (float) p.optDouble("x", n > 0 ? pts[0] : 3000);
                    float headY = (float) p.optDouble("y", n > 0 ? pts[1] : 3000);
                    float angle = (float) p.optDouble("a", 0);
                    boolean boost = p.optInt("s", 0) == 1;

                    list.add(new GameView.Remote(
                            pid,
                            p.optString("n", "?"),
                            p.optInt("c", Color.WHITE),
                            headX,
                            headY,
                            angle,
                            boost,
                            (float) p.optDouble("l", 120),
                            pts,
                            n));
                }
                view.setRemotes(list);
            } catch (Exception ignored) {}

            try {
                Thread.sleep(SYNC_DELAY_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void onDied(float score) {
        if (!singlePlayer) {
            new Thread(() -> {
                try {
                    RoomManager.removePlayer(rtdb, roomPath, myId, false);
                } catch (Exception ignored) {}
            }).start();
        }

        new AlertDialog.Builder(this)
                .setTitle("Kamu mati")
                .setMessage("Panjang akhir: " + (int) score)
                .setCancelable(false)
                .setPositiveButton("Main lagi", (d, w) -> respawn())
                .setNegativeButton("Keluar", (d, w) -> leaveRoom())
                .show();
    }

    private void respawn() {
        if (singlePlayer) {
            view.respawn();
            return;
        }
        new Thread(() -> {
            final boolean ok = checkRoom();
            ui.post(() -> {
                if (ok) {
                    view.respawn();
                } else {
                    Toast.makeText(this, "Room sudah dihapus", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        }).start();
    }

    private boolean checkRoom() {
        try {
            return RoomManager.roomExists(rtdb, code);
        } catch (Exception e) {
            return false;
        }
    }

    private void leaveRoom() {
        if (leaving) return;
        leaving = true;
        syncing = false;
        if (view != null) view.stop();
        if (singlePlayer) {
            finish();
            return;
        }
        new Thread(() -> {
            try {
                if (syncThread != null) syncThread.join(500);
                RoomManager.removePlayer(rtdb, roomPath, myId, true);
            } catch (Exception ignored) {}
            ui.post(this::finish);
        }).start();
    }

    @Override
    public void onBackPressed() {
        leaveRoom();
    }

    @Override
    protected void onDestroy() {
        syncing = false;
        if (view != null) view.stop();
        super.onDestroy();
    }
}