package com.worm.io;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "worm_prefs";
    private static final String KEY_PLAYER_NAME = "player_name";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private Rtdb rtdb;

    private EditText etName, etCode;
    private LinearLayout layoutHome, layoutMulti, layoutJoinInput;
    private Button btnSingle, btnMulti, btnCreateOption, btnJoinOption, btnSubmitJoin, btnBack;
    private TextView tvStatus, tvModeSubtitle;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        hideSystemUI();

        SoundManager.init(this);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        GradientDrawable bgRoot = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF070B12, 0xFF101B2B, 0xFF09101A});
        root.setBackground(bgRoot);
        root.setPadding(40, 20, 40, 20);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(56, 32, 56, 30);
        card.setBackground(makeBox(0xE6141F30, 0xFF26466D, 36f, 3));

        TextView title = new TextView(this);
        title.setText("WORM.IO");
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF00E676);
        title.setShadowLayer(16f, 0f, 0f, 0x9900E676);
        title.setGravity(Gravity.CENTER);
        add(card, title, 4, LinearLayout.LayoutParams.WRAP_CONTENT);

        tvModeSubtitle = new TextView(this);
        tvModeSubtitle.setText("ARENA SLITHER • PILIH MODE PERMAINAN");
        tvModeSubtitle.setTextSize(12);
        tvModeSubtitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvModeSubtitle.setTextColor(0xFF80CBC4);
        tvModeSubtitle.setGravity(Gravity.CENTER);
        add(card, tvModeSubtitle, 18, LinearLayout.LayoutParams.WRAP_CONTENT);

        etName = new EditText(this);
        etName.setHint("Masukkan Nickname Kamu...");
        etName.setTextColor(Color.WHITE);
        etName.setHintTextColor(0xFF6C8299);
        etName.setGravity(Gravity.CENTER);
        etName.setSingleLine(true);
        etName.setTextSize(16);
        etName.setTypeface(Typeface.DEFAULT_BOLD);
        etName.setPadding(32, 18, 32, 18);
        etName.setBackground(makeBox(0xFF0B121E, 0xFF00E676, 24f, 2));

        String savedName = prefs.getString(KEY_PLAYER_NAME, "");
        if (!savedName.isEmpty()) {
            etName.setText(savedName);
            etName.setSelection(savedName.length());
        }
        etName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String clean = s.toString().trim();
                if (!clean.isEmpty()) {
                    prefs.edit().putString(KEY_PLAYER_NAME,
                            clean.length() > 12 ? clean.substring(0, 12) : clean).apply();
                }
            }
        });
        add(card, etName, 20, 460);

        layoutHome = new LinearLayout(this);
        layoutHome.setOrientation(LinearLayout.HORIZONTAL);
        layoutHome.setGravity(Gravity.CENTER);

        btnSingle = makeButton("🐍  SINGLE PLAYER", 0xFF00C853, 0xFF69F0AE);
        btnSingle.setOnClickListener(v -> startSinglePlayer());
        addHorizontal(layoutHome, btnSingle, 18, 250);

        btnMulti = makeButton("🌐  MULTIPLAYER", 0xFF2979FF, 0xFF82B1FF);
        btnMulti.setOnClickListener(v -> showMultiplayerMenu());
        addHorizontal(layoutHome, btnMulti, 0, 250);

        add(card, layoutHome, 8, LinearLayout.LayoutParams.WRAP_CONTENT);

        layoutMulti = new LinearLayout(this);
        layoutMulti.setOrientation(LinearLayout.VERTICAL);
        layoutMulti.setGravity(Gravity.CENTER);
        layoutMulti.setVisibility(View.GONE);

        LinearLayout multiChoices = new LinearLayout(this);
        multiChoices.setOrientation(LinearLayout.HORIZONTAL);
        multiChoices.setGravity(Gravity.CENTER);

        btnCreateOption = makeButton("➕ BUAT ROOM", 0xFF00BFA5, 0xFF64FFDA);
        btnCreateOption.setOnClickListener(v -> createRoom());
        addHorizontal(multiChoices, btnCreateOption, 16, 210);

        btnJoinOption = makeButton("🔑 JOIN ROOM", 0xFFFF6D00, 0xFFFFAB40);
        btnJoinOption.setOnClickListener(v -> {
            layoutJoinInput.setVisibility(View.VISIBLE);
            tvStatus.setText("");
        });
        addHorizontal(multiChoices, btnJoinOption, 16, 210);

        btnBack = makeButton("⬅ KEMBALI", 0xFF37474F, 0xFF78909C);
        btnBack.setOnClickListener(v -> showHomeMenu());
        addHorizontal(multiChoices, btnBack, 0, 160);

        add(layoutMulti, multiChoices, 14, LinearLayout.LayoutParams.WRAP_CONTENT);

        layoutJoinInput = new LinearLayout(this);
        layoutJoinInput.setOrientation(LinearLayout.HORIZONTAL);
        layoutJoinInput.setGravity(Gravity.CENTER);
        layoutJoinInput.setVisibility(View.GONE);

        etCode = new EditText(this);
        etCode.setHint("KODE 4 DIGIT");
        etCode.setTextColor(0xFFFFD740);
        etCode.setHintTextColor(0xFF6C8299);
        etCode.setGravity(Gravity.CENTER);
        etCode.setSingleLine(true);
        etCode.setTextSize(16);
        etCode.setTypeface(Typeface.DEFAULT_BOLD);
        etCode.setInputType(InputType.TYPE_CLASS_NUMBER);
        etCode.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        etCode.setPadding(24, 16, 24, 16);
        etCode.setBackground(makeBox(0xFF0B121E, 0xFFFFAB40, 20f, 2));
        addHorizontal(layoutJoinInput, etCode, 16, 220);

        btnSubmitJoin = makeButton("MASUK 🚀", 0xFFFF9100, 0xFFFFD180);
        btnSubmitJoin.setOnClickListener(v -> joinRoom());
        addHorizontal(layoutJoinInput, btnSubmitJoin, 0, 180);

        add(layoutMulti, layoutJoinInput, 6, LinearLayout.LayoutParams.WRAP_CONTENT);
        add(card, layoutMulti, 8, LinearLayout.LayoutParams.WRAP_CONTENT);

        tvStatus = new TextView(this);
        tvStatus.setTextColor(0xFFFF8A80);
        tvStatus.setTextSize(13);
        tvStatus.setGravity(Gravity.CENTER);
        add(card, tvStatus, 0, LinearLayout.LayoutParams.MATCH_PARENT);

        add(root, card, 0, LinearLayout.LayoutParams.WRAP_CONTENT);
        setContentView(root);

        try {
            rtdb = new Rtdb(Config.databaseUrl(this));
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        SoundManager.init(this);
        SoundManager.startLobbyMusic();
    }

    @Override
    protected void onPause() {
        super.onPause();
        SoundManager.stopLobbyMusic();
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

    private GradientDrawable makeBox(int fillColor, int strokeColor, float radius, int strokeWidth) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius(radius);
        gd.setStroke(strokeWidth, strokeColor);
        return gd;
    }

    private Button makeButton(String text, int bgColor, int strokeColor) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        b.setPadding(24, 18, 24, 18);
        b.setBackground(makeBox(bgColor, strokeColor, 24f, 2));
        return b;
    }

    private void add(LinearLayout parent, View v, int bottomMargin, int width) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                width, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = bottomMargin;
        parent.addView(v, lp);
    }

    private void addHorizontal(LinearLayout parent, View v, int rightMargin, int width) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                width, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = rightMargin;
        parent.addView(v, lp);
    }

    private void showMultiplayerMenu() {
        if (playerName() == null) return;
        tvStatus.setText("");
        tvModeSubtitle.setText("MULTIPLAYER LOBBY • BUAT ATAU MASUK ROOM");
        layoutHome.setVisibility(View.GONE);
        layoutMulti.setVisibility(View.VISIBLE);
        layoutJoinInput.setVisibility(View.GONE);
    }

    private void showHomeMenu() {
        tvStatus.setText("");
        tvModeSubtitle.setText("ARENA SLITHER • PILIH MODE PERMAINAN");
        layoutMulti.setVisibility(View.GONE);
        layoutHome.setVisibility(View.VISIBLE);
    }

    private String playerName() {
        String n = etName.getText().toString().trim();
        if (n.isEmpty()) {
            tvStatus.setText("Isi nickname kamu terlebih dahulu!");
            return null;
        }
        String finalName = n.length() > 12 ? n.substring(0, 12) : n;
        prefs.edit().putString(KEY_PLAYER_NAME, finalName).apply();
        return finalName;
    }

    private void startSinglePlayer() {
        String name = playerName();
        if (name == null) return;
        SoundManager.stopLobbyMusic();
        Intent i = new Intent(this, GameActivity.class);
        i.putExtra(GameActivity.EXTRA_SINGLE, true);
        i.putExtra(GameActivity.EXTRA_NAME, name);
        startActivity(i);
    }

    private void createRoom() {
        String name = playerName();
        if (name == null) return;
        busy(true, "Sedang menyiapkan room...");
        new Thread(() -> {
            try {
                if (rtdb == null) rtdb = new Rtdb(Config.databaseUrl(this));
                RoomManager.Joined j = RoomManager.create(rtdb, name);
                ui.post(() -> showCreatedRoomDialog(j, name));
            } catch (Exception e) {
                fail(e);
            }
        }).start();
    }

    private void showCreatedRoomDialog(RoomManager.Joined j, String name) {
        busy(false, "");
        new AlertDialog.Builder(this)
                .setTitle("Room Berhasil Dibuat!")
                .setMessage("Kode Room Kamu: " + j.code + "\n\nBagikan kode 4 digit ini ke teman yang ingin Join Room.")
                .setCancelable(false)
                .setPositiveButton("Mulai Bermain", (d, w) -> {
                    SoundManager.stopLobbyMusic();
                    new Thread(() -> {
                        try {
                            String path = RoomManager.ROOM_NODE + "/" + j.code;
                            RoomManager.putPlayer(rtdb, path, j.playerId, name, RoomManager.colorFor(j.playerId));
                        } catch (Exception ignored) {}
                    }).start();
                    openGame(j, name);
                })
                .show();
    }

    private void joinRoom() {
        String name = playerName();
        String code = etCode.getText().toString().trim();
        if (name == null) return;
        if (code.length() != 4) {
            tvStatus.setText("Kode room wajib 4 digit angka!");
            return;
        }
        busy(true, "Menghubungkan ke room " + code + "...");
        new Thread(() -> {
            try {
                if (rtdb == null) rtdb = new Rtdb(Config.databaseUrl(this));
                RoomManager.Joined j = RoomManager.join(rtdb, code, name);
                ui.post(() -> {
                    SoundManager.stopLobbyMusic();
                    openGame(j, name);
                });
            } catch (Exception e) {
                fail(e);
            }
        }).start();
    }

    private void openGame(RoomManager.Joined j, String name) {
        busy(false, "");
        Intent i = new Intent(this, GameActivity.class);
        i.putExtra(GameActivity.EXTRA_SINGLE, false);
        i.putExtra(GameActivity.EXTRA_CODE, j.code);
        i.putExtra(GameActivity.EXTRA_NAME, name);
        i.putExtra(GameActivity.EXTRA_ID, j.playerId);
        startActivity(i);
    }

    private void fail(Exception e) {
        ui.post(() -> busy(false, "Gagal: " + e.getMessage()));
    }

    private void busy(boolean on, String msg) {
        btnCreateOption.setEnabled(!on);
        btnJoinOption.setEnabled(!on);
        btnSubmitJoin.setEnabled(!on);
        btnBack.setEnabled(!on);
        tvStatus.setText(msg);
    }
}