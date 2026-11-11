#include <jni.h>
#include <cmath>
#include <vector>
#include <deque>
#include <random>
#include <algorithm>

struct Point {
    float x, y;
};

struct Pellet {
    float x, y, r, val;
    int c;
};

struct BotCacing {
    float hx, hy, angle, targetAngle, len;
    int timer;
    std::deque<Point> trail;
};

// State Dunia di Level C++
static float gWorld = 6000.0f;
static float gHeadR = 10.0f;
static float gSeg = 4.0f;
static float gStartLen = 120.0f;

// Pemain Lokal
static float pX = 3000.0f, pY = 3000.0f;
static float pAngle = 0.0f;
static float pLen = 120.0f;
static bool pAlive = true;
static std::deque<Point> pTrail;

// Objek Makanan & Bot
static std::vector<Pellet> gFoods;
static std::vector<BotCacing> gBots;
static std::mt19937 rng(1337);

static const int FOOD_COLORS[] = {
    (int)0xFFFF5252, (int)0xFFFFD740, (int)0xFF69F0AE, 
    (int)0xFF40C4FF, (int)0xFFE040FB, (int)0xFFFFAB40
};

static float wrapAngle(float a) {
    while (a > M_PI) a -= 2.0f * M_PI;
    while (a < -M_PI) a += 2.0f * M_PI;
    return a;
}

static float calcRadius(float len) {
    float extra = std::max(0.0f, len - gStartLen);
    return gHeadR + std::sqrt(extra) * 0.45f;
}

static void randomizeFood(Pellet& p) {
    std::uniform_real_distribution<float> distWorld(0.0f, gWorld);
    std::uniform_int_distribution<int> distTier(0, 99);
    std::uniform_int_distribution<int> distColor(0, 5);

    p.x = distWorld(rng);
    p.y = distWorld(rng);
    p.c = FOOD_COLORS[distColor(rng)];

    int tier = distTier(rng);
    if (tier < 50) {
        p.r = 4.5f;
        p.val = 3.0f;
    } else if (tier < 85) {
        p.r = 8.0f;
        p.val = 6.0f;
    } else {
        p.r = 13.0f;
        p.val = 12.0f;
    }
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_worm_io_GameEngine_nativeInitWorld(JNIEnv* env, jclass clazz, jfloat worldSize, jint foodCount) {
    gWorld = worldSize;
    gFoods.clear();
    gFoods.resize(foodCount);
    for (int i = 0; i < foodCount; i++) {
        randomizeFood(gFoods[i]);
    }

    // Reset Pemain
    std::uniform_real_distribution<float> distSpawn(gWorld * 0.3f, gWorld * 0.7f);
    pX = distSpawn(rng);
    pY = distSpawn(rng);
    pAngle = 0.0f;
    pLen = gStartLen;
    pAlive = true;
    pTrail.clear();

    int n = (int)(pLen / gSeg);
    for (int i = 0; i <= n; i++) {
        pTrail.push_back({pX - std::cos(pAngle) * gSeg * i, pY - std::sin(pAngle) * gSeg * i});
    }
}

JNIEXPORT void JNICALL
Java_com_worm_io_GameEngine_nativeInitBots(JNIEnv* env, jclass clazz, jint count) {
    gBots.clear();
    gBots.resize(count);
    std::uniform_real_distribution<float> distSpawn(gWorld * 0.2f, gWorld * 0.8f);
    std::uniform_real_distribution<float> distAngle(0.0f, 2.0f * M_PI);

    for (int i = 0; i < count; i++) {
        gBots[i].hx = distSpawn(rng);
        gBots[i].hy = distSpawn(rng);
        gBots[i].angle = distAngle(rng);
        gBots[i].targetAngle = gBots[i].angle;
        gBots[i].len = gStartLen + (i * 25.0f);
        gBots[i].timer = 30;
        gBots[i].trail.clear();

        int n = (int)(gBots[i].len / gSeg);
        for (int j = 0; j <= n; j++) {
            gBots[i].trail.push_back({
                gBots[i].hx - std::cos(gBots[i].angle) * gSeg * j,
                gBots[i].hy - std::sin(gBots[i].angle) * gSeg * j
            });
        }
    }
}

/**
 * Update fisika cacing di C++
 * outState: [hx, hy, angle, len, radius, alive(1/0)]
 */
JNIEXPORT void JNICALL
Java_com_worm_io_GameEngine_nativeUpdate(JNIEnv* env, jclass clazz,
                                        jfloat joyDx, jfloat joyDy, jboolean joyActive,
                                        jboolean isBoosting, jfloatArray outState) {
    if (!pAlive) return;

    if (joyActive) {
        float target = std::atan2(joyDy, joyDx);
        float diff = wrapAngle(target - pAngle);
        pAngle += std::max(-0.09f, std::min(0.09f, diff));
    }
    pAngle = wrapAngle(pAngle);

    float speed = isBoosting ? 6.6f : 3.4f;
    int steps = isBoosting ? 2 : 1;
    float stepDist = speed / steps;

    for (int s = 0; s < steps; s++) {
        pX += std::cos(pAngle) * stepDist;
        pY += std::sin(pAngle) * stepDist;

        if (!pTrail.empty()) {
            Point& anchor = pTrail.front();
            float ax = pX - anchor.x, ay = pY - anchor.y;
            if (ax * ax + ay * ay >= gSeg * gSeg) {
                pTrail.push_front({pX, pY});
            }
        }
    }

    // Tabrak Dinding
    if (pX < 0 || pY < 0 || pX > gWorld || pY > gWorld) {
        pAlive = false;
    }

    int maxSeg = (int)(pLen / gSeg) + 1;
    while ((int)pTrail.size() > maxSeg) {
        pTrail.pop_back();
    }

    float currentRadius = calcRadius(pLen);

    // Makan Pellet (C++ Native Loop sangat cepat)
    for (size_t i = 0; i < gFoods.size(); i++) {
        float dx = gFoods[i].x - pX;
        float dy = gFoods[i].y - pY;
        float eatDist = currentRadius + gFoods[i].r + 4.0f;
        if (dx * dx + dy * dy < eatDist * eatDist) {
            pLen += gFoods[i].val;
            randomizeFood(gFoods[i]);
        }
    }

    // Salin status cacing ke array Java
    jfloat temp[6];
    temp[0] = pX;
    temp[1] = pY;
    temp[2] = pAngle;
    temp[3] = pLen;
    temp[4] = currentRadius;
    temp[5] = pAlive ? 1.0f : 0.0f;

    env->SetFloatArrayRegion(outState, 0, 6, temp);
}

JNIEXPORT jint JNICALL
Java_com_worm_io_GameEngine_nativeGetTrail(JNIEnv* env, jclass clazz, jfloatArray outPts) {
    int count = pTrail.size();
    if (count == 0) return 0;

    std::vector<jfloat> buf(count * 2);
    for (int i = 0; i < count; i++) {
        buf[2 * i] = pTrail[i].x;
        buf[2 * i + 1] = pTrail[i].y;
    }
    env->SetFloatArrayRegion(outPts, 0, count * 2, buf.data());
    return count;
}

JNIEXPORT void JNICALL
Java_com_worm_io_GameEngine_nativeGetFoodData(JNIEnv* env, jclass clazz, jfloatArray outFoods, jintArray outColors) {
    int n = gFoods.size();
    std::vector<jfloat> fBuf(n * 3);
    std::vector<jint> cBuf(n);

    for (int i = 0; i < n; i++) {
        fBuf[3 * i] = gFoods[i].x;
        fBuf[3 * i + 1] = gFoods[i].y;
        fBuf[3 * i + 2] = gFoods[i].r;
        cBuf[i] = gFoods[i].c;
    }

    env->SetFloatArrayRegion(outFoods, 0, n * 3, fBuf.data());
    env->SetIntArrayRegion(outColors, 0, n, cBuf.data());
}

} // extern "C"