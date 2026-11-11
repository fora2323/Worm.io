package com.worm.io;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameView extends View {

    public interface Listener {
        void onDied(float score);
    }

    public static class Remote {
        public final String id;
        public final String name;
        public final int color;
        public final float hx, hy, angle;
        public final boolean boosting;
        public final float len;
        public final float[] pts;
        public final int n;

        public Remote(String id, String name, int color, float hx, float hy,
                      float angle, boolean boosting, float len, float[] pts, int n) {
            this.id = id; this.name = name; this.color = color;
            this.hx = hx; this.hy = hy; this.angle = angle;
            this.boosting = boosting; this.len = len;
            this.pts = pts; this.n = n;
        }
    }

    private static class SmoothRemote {
        String id, name;
        int color;
        float hx, hy, tx, ty;
        float angle, targetAngle;
        float len;
        boolean boosting;
        long lastServerUpdate = 0;
        final ArrayList<float[]> trail = new ArrayList<>();
    }

    static final float WORLD = 6000f;
    static final int FOOD_COUNT = 800;
    static final int MAX_FOOD_CAP = 1200;
    static final float SEG = 4f;
    static final float START_LEN = 120f;
    static final float BASE_HEAD_R = 10f;
    static final int SYNC_STEP = 4;
    static final int SYNC_MAX = 35;

    private static final String[] BOT_NAMES = {
        "Viper", "Cobra", "Python", "Anaconda", "Dragon", "Turbo", "Titan", "Hydra"
    };

    public static float calcBodyRadius(float length) {
        float extra = Math.max(0, length - START_LEN);
        return BASE_HEAD_R + (float) Math.sqrt(extra) * 0.45f;
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF minimapRect = new RectF();
    private final Map<String, SmoothRemote> smoothRemotes = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private volatile List<Remote> incomingRemotes = null;
    private final List<Remote> renderRemotes = new ArrayList<>();
    private volatile float[] snapshot = new float[5];
    private volatile boolean alive = true;

    private final float[] nativeState = new float[6];
    private final float[] trailBuffer = new float[12000];
    private final float[] foodBuffer = new float[MAX_FOOD_CAP * 3];
    private final int[] foodColors = new int[MAX_FOOD_CAP];
    private final float[] botInfoBuffer = new float[10 * 6];
    private final float[] botTrailsBuffer = new float[10 * 600 * 2];
    private final int[] botTrailOffsets = new int[10 * 2];
    private int trailPointsCount = 0;
    private int currentFoodCount = 0;

    private boolean singlePlayer = false;
    private String roomCode = null;
    private String myName = "Saya";
    private int myColor = Color.WHITE;
    private float hx, hy, angle, len, myRadius;
    private boolean running;
    private Listener listener;

    private int joyPointerId = -1;
    private float joyCenterX, joyCenterY;
    private float joyKnobX, joyKnobY;
    private static final float JOY_RADIUS = 95f;
    private static final float JOY_KNOB_RADIUS = 42f;

    private int boostPointerId = -1;
    private boolean isBoosting = false;
    private float boostCenterX, boostCenterY;
    private static final float BOOST_RADIUS = 80f;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            update();
            invalidate();
            handler.postDelayed(this, 16);
        }
    };

    public GameView(Context ctx) {
        super(ctx);
        GameEngine.nativeInitWorld(WORLD, FOOD_COUNT);
    }

    public void setPlayer(String name, int color) { myName = name; myColor = color; }

    public void setMode(boolean single, String code) {
        this.singlePlayer = single;
        this.roomCode = code;
        smoothRemotes.clear();
        renderRemotes.clear();
        GameEngine.nativeInitBots(single ? 8 : 0);
    }

    public void setListener(Listener l) { listener = l; }
    public void setRemotes(List<Remote> r) { incomingRemotes = r; }
    public boolean isAlive() { return alive; }
    public float[] snapshot() { return snapshot; }

    public void start() { running = true; spawn(); handler.post(tick); }
    public void stop() { running = false; handler.removeCallbacks(tick); }
    public void respawn() { spawn(); }

    private void spawn() {
        GameEngine.nativeInitWorld(WORLD, FOOD_COUNT);
        if (singlePlayer) GameEngine.nativeInitBots(8);
        joyPointerId = -1; boostPointerId = -1; isBoosting = false; alive = true;
        smoothRemotes.clear(); renderRemotes.clear();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        joyCenterX = 170f; joyCenterY = h - 150f;
        joyKnobX = joyCenterX; joyKnobY = joyCenterY;
        boostCenterX = w - 170f; boostCenterY = h - 150f;
    }

    private void update() {
        if (!alive) return;

        float jdx = joyKnobX - joyCenterX;
        float jdy = joyKnobY - joyCenterY;
        boolean joyActive = (joyPointerId != -1) && (jdx * jdx + jdy * jdy > 25f);

        GameEngine.nativeUpdate(jdx, jdy, joyActive, isBoosting, nativeState);
        hx = nativeState[0]; hy = nativeState[1];
        angle = nativeState[2]; len = nativeState[3];
        myRadius = nativeState[4]; alive = nativeState[5] == 1.0f;

        if (!alive) { if (listener != null) listener.onDied(len); return; }

        trailPointsCount = GameEngine.nativeGetTrail(trailBuffer);
        currentFoodCount = GameEngine.nativeGetFoodData(foodBuffer, foodColors);

        if (singlePlayer) {
            int botCount = GameEngine.nativeGetBotsData(botInfoBuffer, botTrailsBuffer, botTrailOffsets);
            renderRemotes.clear();
            for (int i = 0; i < botCount; i++) {
                float bhx = botInfoBuffer[i * 6];
                float bhy = botInfoBuffer[i * 6 + 1];
                float bangle = botInfoBuffer[i * 6 + 2];
                float blen = botInfoBuffer[i * 6 + 3];
                int bcolor = (int) botInfoBuffer[i * 6 + 5];
                int offset = botTrailOffsets[i * 2];
                int count = botTrailOffsets[i * 2 + 1];
                float[] bpts = new float[count * 2];
                System.arraycopy(botTrailsBuffer, offset, bpts, 0, count * 2);
                renderRemotes.add(new Remote("bot_" + i,
                    BOT_NAMES[i % BOT_NAMES.length], bcolor,
                    bhx, bhy, bangle, false, blen, bpts, count));
            }
        } else {
            updateSmoothRemotes();
        }

        for (Remote r : renderRemotes) {
            float enemyRadius = calcBodyRadius(r.len);
            float killDist = myRadius * 0.7f + enemyRadius * 0.7f;
            for (int i = 0; i < r.n; i++) {
                float dx = r.pts[2 * i] - hx, dy = r.pts[2 * i + 1] - hy;
                if (dx * dx + dy * dy < killDist * killDist) {
                    alive = false;
                    if (listener != null) listener.onDied(len);
                    return;
                }
            }
        }

        float[] s = new float[5 + 2 * SYNC_MAX];
        s[0] = hx; s[1] = hy; s[2] = len; s[3] = angle; s[4] = isBoosting ? 1f : 0f;
        s[5] = hx; s[6] = hy;
        int cnt = 1;
        for (int i = SYNC_STEP; i < trailPointsCount && cnt < SYNC_MAX; i += SYNC_STEP) {
            s[5 + 2 * cnt] = trailBuffer[2 * i];
            s[6 + 2 * cnt] = trailBuffer[2 * i + 1];
            cnt++;
        }
        snapshot = Arrays.copyOf(s, 5 + 2 * cnt);
    }

    private void updateSmoothRemotes() {
        List<Remote> latest = incomingRemotes;
        if (latest != null) {
            incomingRemotes = null;
            Set<String> activeIds = new HashSet<>();
            for (Remote r : latest) {
                activeIds.add(r.id);
                SmoothRemote sr = smoothRemotes.get(r.id);
                float distJump = sr == null ? 9999f
                    : (float) Math.hypot(r.hx - sr.tx, r.hy - sr.ty);

                if (sr == null || distJump > 600f) {
                    // Pemain baru atau teleport — reset total
                    sr = new SmoothRemote();
                    sr.id = r.id; sr.name = r.name; sr.color = r.color;
                    sr.hx = r.hx; sr.hy = r.hy;
                    sr.tx = r.hx; sr.ty = r.hy;
                    sr.angle = r.angle; sr.targetAngle = r.angle;
                    sr.len = Math.max(START_LEN, r.len);
                    sr.boosting = r.boosting;
                    sr.lastServerUpdate = System.currentTimeMillis();
                    rebuildSmoothTrail(sr, r.pts, r.n);
                    smoothRemotes.put(r.id, sr);
                } else {
                    // Update target saja, jangan langsung pindah
                    sr.name = r.name; sr.color = r.color;
                    sr.tx = r.hx; sr.ty = r.hy;
                    sr.targetAngle = r.angle;
                    sr.len = Math.max(START_LEN, r.len);
                    sr.boosting = r.boosting;
                    sr.lastServerUpdate = System.currentTimeMillis();
                    // Rebuild trail dari data server biar ekor nggak patah
                    if (r.n > 1) rebuildSmoothTrail(sr, r.pts, r.n);
                }
            }
            smoothRemotes.keySet().retainAll(activeIds);
        }

        renderRemotes.clear();
        for (SmoothRemote sr : smoothRemotes.values()) {
            // Smooth angle
            float diffAngle = wrap(sr.targetAngle - sr.angle);
            sr.angle = wrap(sr.angle + diffAngle * 0.25f);

            // Hitung arah ke target
            float errX = sr.tx - sr.hx;
            float errY = sr.ty - sr.hy;
            float errDist = (float) Math.hypot(errX, errY);

            if (errDist > 0.5f) {
                // Gerak ke arah target, kecepatan proporsional jarak
                // Tapi dibatasi biar nggak loncat
                float baseSpeed = sr.boosting ? 6.6f : 3.4f;
                float speed = Math.min(baseSpeed * 2.5f, errDist * 0.3f);
                speed = Math.max(baseSpeed * 0.5f, speed);

                float dirX = errX / errDist;
                float dirY = errY / errDist;

                float nextX = sr.hx + dirX * speed;
                float nextY = sr.hy + dirY * speed;

                // Clamp biar nggak overshoot
                float newErrX = sr.tx - nextX;
                float newErrY = sr.ty - nextY;
                if (newErrX * errX < 0) nextX = sr.tx;
                if (newErrY * errY < 0) nextY = sr.ty;

                appendSmoothTrail(sr, nextX, nextY);
            }

            int n = sr.trail.size() + 1;
            float[] pts = new float[n * 2];
            pts[0] = sr.hx; pts[1] = sr.hy;
            for (int i = 0; i < sr.trail.size(); i++) {
                float[] t = sr.trail.get(i);
                pts[2 * (i + 1)] = t[0];
                pts[2 * (i + 1) + 1] = t[1];
            }
            renderRemotes.add(new Remote(sr.id, sr.name, sr.color,
                sr.hx, sr.hy, sr.angle, sr.boosting, sr.len, pts, n));
        }
    }

    private void rebuildSmoothTrail(SmoothRemote sr, float[] pts, int n) {
        // Simpan posisi kepala sekarang biar nggak reset
        float savedHx = sr.hx;
        float savedHy = sr.hy;
        sr.trail.clear();
        if (n >= 2) {
            for (int i = 0; i < n - 1; i++) {
                float x1 = pts[2 * i], y1 = pts[2 * i + 1];
                float x2 = pts[2 * (i + 1)], y2 = pts[2 * (i + 1) + 1];
                float dist = (float) Math.hypot(x2 - x1, y2 - y1);
                int steps = Math.max(1, (int) (dist / SEG));
                for (int s = 0; s < steps; s++) {
                    float t = s / (float) steps;
                    sr.trail.add(new float[]{x1 + (x2 - x1) * t, y1 + (y2 - y1) * t});
                }
            }
        }
        // Restore kepala — jangan reset ke posisi server
        sr.hx = savedHx;
        sr.hy = savedHy;
    }

    private void appendSmoothTrail(SmoothRemote sr, float nextX, float nextY) {
        float dx = nextX - sr.hx;
        float dy = nextY - sr.hy;
        if (Math.abs(dx) < 0.1f && Math.abs(dy) < 0.1f) return;

        sr.hx = nextX;
        sr.hy = nextY;

        if (sr.trail.isEmpty()) {
            sr.trail.add(new float[]{sr.hx, sr.hy});
        } else {
            float[] anchor = sr.trail.get(0);
            float ax = sr.hx - anchor[0], ay = sr.hy - anchor[1];
            if (ax * ax + ay * ay >= SEG * SEG) {
                sr.trail.add(0, new float[]{sr.hx, sr.hy});
            }
        }

        int max = (int) (sr.len / SEG) + 1;
        while (sr.trail.size() > max) {
            sr.trail.remove(sr.trail.size() - 1);
        }
    }

    private static float wrap(float a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF0E1217);

        float scale = Math.max(0.4f, 1f - (len - START_LEN) / 2400f);
        c.save();
        c.translate(getWidth() / 2f, getHeight() / 2f);
        c.scale(scale, scale);
        c.translate(-hx, -hy);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0xFF1C2630);
        for (float g = 0; g <= WORLD; g += 150f) {
            c.drawLine(g, 0, g, WORLD, paint);
            c.drawLine(0, g, WORLD, g, paint);
        }
        paint.setStrokeWidth(16f);
        paint.setColor(0xFFFF3B30);
        c.drawRect(0, 0, WORLD, WORLD, paint);

        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < currentFoodCount; i++) {
            paint.setColor(foodColors[i]);
            c.drawCircle(foodBuffer[3 * i], foodBuffer[3 * i + 1], foodBuffer[3 * i + 2], paint);
        }

        for (Remote r : renderRemotes) {
            if (r.n < 1) continue;
            drawSnake(c, r.pts, r.n, r.color, r.name, r.angle, calcBodyRadius(r.len));
        }

        if (alive && trailPointsCount > 0) {
            drawSnake(c, trailBuffer, trailPointsCount, myColor, myName, angle, myRadius);
        }

        c.restore();
        drawHud(c);
        drawControls(c);
    }

    private void drawSnake(Canvas c, float[] p, int n, int color, String name, float knownAngle, float r) {
        if (n < 1) return;
        int darkBorder = darkenColor(color, 0.48f);
        int lightTint = lightenColor(color, 1.20f);
        int step = Math.max(1, Math.round(r * 0.40f / SEG));

        paint.setStyle(Paint.Style.FILL);
        for (int i = n - 1; i >= 1; i -= step) {
            paint.setColor(darkBorder);
            c.drawCircle(p[2 * i], p[2 * i + 1], r, paint);
            paint.setColor(((i / step) % 2 == 0) ? color : lightTint);
            c.drawCircle(p[2 * i], p[2 * i + 1], r - 2.2f, paint);
        }

        paint.setColor(darkBorder);
        c.drawCircle(p[0], p[1], r, paint);
        paint.setColor(color);
        c.drawCircle(p[0], p[1], r - 2.2f, paint);

        float dx = (float) Math.cos(knownAngle);
        float dy = (float) Math.sin(knownAngle);
        float eyeSpacing = r * 0.52f;
        float eyeForward = r * 0.36f;
        float px = -dy * eyeSpacing, py = dx * eyeSpacing;
        float ex = dx * eyeForward, ey = dy * eyeForward;

        float lx = p[0] + ex + px, ly = p[1] + ey + py;
        float rx2 = p[0] + ex - px, ry2 = p[1] + ey - py;
        float eyeR = r * 0.40f, pupilR = eyeR * 0.50f;

        paint.setColor(0xFF222222);
        c.drawCircle(lx, ly, eyeR + 0.8f, paint);
        c.drawCircle(rx2, ry2, eyeR + 0.8f, paint);
        paint.setColor(Color.WHITE);
        c.drawCircle(lx, ly, eyeR, paint);
        c.drawCircle(rx2, ry2, eyeR, paint);
        paint.setColor(Color.BLACK);
        c.drawCircle(lx + dx * (eyeR * 0.30f), ly + dy * (eyeR * 0.30f), pupilR, paint);
        c.drawCircle(rx2 + dx * (eyeR * 0.30f), ry2 + dy * (eyeR * 0.30f), pupilR, paint);
        paint.setColor(Color.WHITE);
        c.drawCircle(lx + dx * (eyeR * 0.30f) - 0.8f, ly + dy * (eyeR * 0.30f) - 0.8f, pupilR * 0.35f, paint);
        c.drawCircle(rx2 + dx * (eyeR * 0.30f) - 0.8f, ry2 + dy * (eyeR * 0.30f) - 0.8f, pupilR * 0.35f, paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.max(22f, r * 1.5f));
        paint.setColor(Color.WHITE);
        c.drawText(name, p[0], p[1] - r - 10f, paint);
    }

    private static int darkenColor(int color, float factor) {
        return Color.argb(Color.alpha(color),
            Math.max(0, Math.min(255, Math.round(Color.red(color) * factor))),
            Math.max(0, Math.min(255, Math.round(Color.green(color) * factor))),
            Math.max(0, Math.min(255, Math.round(Color.blue(color) * factor))));
    }

    private static int lightenColor(int color, float factor) {
        return Color.argb(Color.alpha(color),
            Math.max(0, Math.min(255, Math.round(Color.red(color) * factor))),
            Math.max(0, Math.min(255, Math.round(Color.green(color) * factor))),
            Math.max(0, Math.min(255, Math.round(Color.blue(color) * factor))));
    }

    private void drawHud(Canvas c) {
        float mapLeft = 24f, mapTop = 24f, mapSize = 145f;
        minimapRect.set(mapLeft, mapTop, mapLeft + mapSize, mapTop + mapSize);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xBB09101A);
        c.drawRoundRect(minimapRect, 16f, 16f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(0x4469F0AE);
        c.drawLine(mapLeft + mapSize / 2f, mapTop, mapLeft + mapSize / 2f, mapTop + mapSize, paint);
        c.drawLine(mapLeft, mapTop + mapSize / 2f, mapLeft + mapSize, mapTop + mapSize / 2f, paint);

        paint.setStrokeWidth(3f);
        paint.setColor(0xFF00E676);
        c.drawRoundRect(minimapRect, 16f, 16f, paint);

        paint.setStyle(Paint.Style.FILL);
        for (Remote r : renderRemotes) {
            float rx = mapLeft + Math.max(4f, Math.min(mapSize - 4f, (r.hx / WORLD) * mapSize));
            float ry = mapTop + Math.max(4f, Math.min(mapSize - 4f, (r.hy / WORLD) * mapSize));
            paint.setColor(r.color); c.drawCircle(rx, ry, 3.8f, paint);
        }

        if (alive) {
            float mx = mapLeft + Math.max(5f, Math.min(mapSize - 5f, (hx / WORLD) * mapSize));
            float my = mapTop + Math.max(5f, Math.min(mapSize - 5f, (hy / WORLD) * mapSize));
            paint.setColor(Color.WHITE); c.drawCircle(mx, my, 6.2f, paint);
            paint.setColor(0xFF00E676); c.drawCircle(mx, my, 4.2f, paint);
        }

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(15f); paint.setColor(0xAAFFFFFF);
        c.drawText("MAP", mapLeft + 8f, mapTop + 18f, paint);

        float infoX = mapLeft + mapSize + 22f;
        paint.setTextSize(26f); paint.setColor(Color.WHITE);
        c.drawText("Panjang: " + (int) len + " m", infoX, 52f, paint);
        paint.setColor(0xFFFFD54F);
        c.drawText("Berat: " + (int)(len * 1.5f) + " kg", infoX, 86f, paint);
        paint.setColor(Color.WHITE);
        c.drawText("Pemain: " + (renderRemotes.size() + 1), infoX, 120f, paint);

        if (!singlePlayer && roomCode != null) {
            paint.setColor(0xFF69F0AE);
            c.drawText("Room: " + roomCode, infoX, 154f, paint);
        }

        List<Remote> board = new ArrayList<>(renderRemotes);
        board.add(new Remote("me", myName, myColor, hx, hy, angle, isBoosting, len, null, 0));
        Collections.sort(board, (a, b) -> Float.compare(b.len, a.len));
        paint.setTextSize(24f); paint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i < board.size() && i < 6; i++) {
            Remote r = board.get(i);
            paint.setColor(r.color);
            c.drawText((i + 1) + ". " + r.name + "  " + (int) r.len, getWidth() - 28, 44 + i * 32, paint);
        }
    }

    private void drawControls(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x44FFFFFF);
        c.drawCircle(joyCenterX, joyCenterY, JOY_RADIUS, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f); paint.setColor(0x88FFFFFF);
        c.drawCircle(joyCenterX, joyCenterY, JOY_RADIUS, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xCC69F0AE);
        c.drawCircle(joyKnobX, joyKnobY, JOY_KNOB_RADIUS, paint);

        paint.setColor(isBoosting ? 0xDDFF5252 : 0x66FF9800);
        c.drawCircle(boostCenterX, boostCenterY, BOOST_RADIUS, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f); paint.setColor(0xFFFFFFFF);
        c.drawCircle(boostCenterX, boostCenterY, BOOST_RADIUS, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE); paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(28f);
        c.drawText("BUS", boostCenterX, boostCenterY + 10f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        int pid = e.getPointerId(idx);
        float x = e.getX(idx), y = e.getY(idx);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float dBoost = (float) Math.hypot(x - boostCenterX, y - boostCenterY);
                if (dBoost <= BOOST_RADIUS * 1.35f && boostPointerId == -1) {
                    boostPointerId = pid; isBoosting = true;
                } else if (x < getWidth() * 0.6f && joyPointerId == -1) {
                    joyPointerId = pid; updateJoystickKnob(x, y);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    if (e.getPointerId(i) == joyPointerId)
                        updateJoystickKnob(e.getX(i), e.getY(i));
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                if (pid == joyPointerId) {
                    joyPointerId = -1; joyKnobX = joyCenterX; joyKnobY = joyCenterY;
                } else if (pid == boostPointerId) {
                    boostPointerId = -1; isBoosting = false;
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                joyPointerId = -1; boostPointerId = -1; isBoosting = false;
                joyKnobX = joyCenterX; joyKnobY = joyCenterY;
                break;
            }
        }
        return true;
    }

    private void updateJoystickKnob(float touchX, float touchY) {
        float dx = touchX - joyCenterX, dy = touchY - joyCenterY;
        float dist = (float) Math.hypot(dx, dy);
        if (dist > JOY_RADIUS) { dx = (dx / dist) * JOY_RADIUS; dy = (dy / dist) * JOY_RADIUS; }
        joyKnobX = joyCenterX + dx; joyKnobY = joyCenterY + dy;
    }
}