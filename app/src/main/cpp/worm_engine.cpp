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
    int color;
    std::deque<Point> trail;
};

static float gWorld = 6000.0f;
static float gHeadR = 10.0f;
static float gSeg = 4.0f;
static float gStartLen = 120.0f;

static float pX = 3000.0f, pY = 3000.0f;
static float pAngle = 0.0f;
static float pAngularVel = 0.0f;
static float pLen = 120.0f;
static bool pAlive = true;
static std::deque<Point> pTrail;

static std::vector<Pellet> gFoods;
static std::vector<BotCacing> gBots;
static std::mt19937 rng(1337);

static std::vector<jfloat> sTrailBuf;
static std::vector<jfloat> sFoodBuf;
static std::vector<jint> sColorBuf;
static std::vector<jfloat> sBotInfoBuf;
static std::vector<jint> sBotOffsetBuf;
static std::vector<jfloat> sBotTrailBuf;

static const int FOOD_COLORS[] = {
    (int)0xFFFF5252, (int)0xFFFFD740, (int)0xFF69F0AE, 
    (int)0xFF40C4FF, (int)0xFFE040FB, (int)0xFFFFAB40
};

static const int BOT_COLORS[] = {
    (int)0xFFFF1744, (int)0xFF00E676, (int)0xFF2979FF, (int)0xFFFF9100,
    (int)0xFFD500F9, (int)0xFF00B0FF, (int)0xFFFFEA00, (int)0xFF00E5FF
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

    std::uniform_real_distribution<float> distSpawn(gWorld * 0.3f, gWorld * 0.7f);
    pX = distSpawn(rng);
    pY = distSpawn(rng);
    pAngle = 0.0f;
    pAngularVel = 0.0f;
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
        gBots[i].len = gStartLen + (i * 35.0f);
        gBots[i].timer = 20 + (i * 5);
        gBots[i].color = BOT_COLORS[i % 8];
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

JNIEXPORT void JNICALL
Java_com_worm_io_GameEngine_nativeUpdate(JNIEnv* env, jclass clazz,
                                        jfloat joyDx, jfloat joyDy, jboolean joyActive,
                                        jboolean isBoosting, jfloatArray outState) {
    if (!pAlive) return;

    if (joyActive) {
        float target = std::atan2(joyDy, joyDx);
        float diff = wrapAngle(target - pAngle);
        float maxTurn = isBoosting ? 0.07f : 0.10f;
        pAngularVel += diff * 0.18f;
        pAngularVel = std::max(-maxTurn, std::min(maxTurn, pAngularVel));
    } else {
        pAngularVel *= 0.75f;
    }
    pAngle = wrapAngle(pAngle + pAngularVel);

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

    if (pX < 0 || pY < 0 || pX > gWorld || pY > gWorld) {
        pAlive = false;
    }

    int maxSeg = (int)(pLen / gSeg) + 1;
    while ((int)pTrail.size() > maxSeg) {
        pTrail.pop_back();
    }

    float currentRadius = calcRadius(pLen);

    for (size_t i = 0; i < gFoods.size(); i++) {
        float dx = gFoods[i].x - pX;
        float dy = gFoods[i].y - pY;
        float eatDist = currentRadius + gFoods[i].r + 4.0f;
        if (dx * dx + dy * dy < eatDist * eatDist) {
            pLen += gFoods[i].val;
            randomizeFood(gFoods[i]);
        }
    }

    for (auto& b : gBots) {
        b.timer--;
        if (b.timer <= 0) {
            std::uniform_real_distribution<float> dAngle(-1.2f, 1.2f);
            b.targetAngle = wrapAngle(b.angle + dAngle(rng));
            b.timer = 25 + (rng() % 45);
        }

        float margin = 320.0f;
        if (b.hx < margin) b.targetAngle = 0.0f;
        else if (b.hx > gWorld - margin) b.targetAngle = (float)M_PI;
        if (b.hy < margin) b.targetAngle = (float)(M_PI / 2.0);
        else if (b.hy > gWorld - margin) b.targetAngle = (float)(-M_PI / 2.0);

        float diff = wrapAngle(b.targetAngle - b.angle);
        b.angle = wrapAngle(b.angle + diff * 0.08f);

        b.hx += std::cos(b.angle) * 3.4f;
        b.hy += std::sin(b.angle) * 3.4f;

        if (!b.trail.empty()) {
            Point& anchor = b.trail.front();
            float ax = b.hx - anchor.x, ay = b.hy - anchor.y;
            if (ax * ax + ay * ay >= gSeg * gSeg) {
                b.trail.push_front({b.hx, b.hy});
            }
        }
        int botMaxSeg = (int)(b.len / gSeg) + 1;
        while ((int)b.trail.size() > botMaxSeg) {
            b.trail.pop_back();
        }

        float botRadius = calcRadius(b.len);

        for (auto& f : gFoods) {
            float dx = f.x - b.hx;
            float dy = f.y - b.hy;
            float eatDist = botRadius + f.r + 3.0f;
            if (dx * dx + dy * dy < eatDist * eatDist) {
                b.len += f.val;
                randomizeFood(f);
            }
        }

        if (pAlive) {
            float killDist = currentRadius * 0.7f + botRadius * 0.7f;
            for (const auto& pt : b.trail) {
                float dx = pt.x - pX;
                float dy = pt.y - pY;
                if (dx * dx + dy * dy < killDist * killDist) {
                    pAlive = false;
                    break;
                }
            }
        }

        if (pAlive) {
            float killDist = botRadius * 0.7f + currentRadius * 0.7f;
            for (const auto& pt : pTrail) {
                float dx = pt.x - b.hx;
                float dy = pt.y - b.hy;
                if (dx * dx + dy * dy < killDist * killDist) {
                    for (size_t k = 0; k < b.trail.size(); k += 4) {
                        Pellet p;
                        p.x = b.trail[k].x;
                        p.y = b.trail[k].y;
                        p.r = 9.5f;
                        p.val = 8.0f;
                        p.c = b.color;
                        if (gFoods.size() < 1200) {
                            gFoods.push_back(p);
                        } else {
                            gFoods[rng() % gFoods.size()] = p;
                        }
                    }

                    std::uniform_real_distribution<float> distSpawn(gWorld * 0.2f, gWorld * 0.8f);
                    std::uniform_real_distribution<float> distAngle(0.0f, 2.0f * M_PI);
                    b.hx = distSpawn(rng);
                    b.hy = distSpawn(rng);
                    b.angle = distAngle(rng);
                    b.targetAngle = b.angle;
                    b.len = gStartLen + 20.0f;
                    b.trail.clear();
                    int bn = (int)(b.len / gSeg);
                    for (int j = 0; j <= bn; j++) {
                        b.trail.push_back({
                            b.hx - std::cos(b.angle) * gSeg * j,
                            b.hy - std::sin(b.angle) * gSeg * j
                        });
                    }
                    break;
                }
            }
        }
    }

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
    jsize maxPts = env->GetArrayLength(outPts);
    int totalPts = (int)pTrail.size() + 1;
    int nFloats = std::min(totalPts * 2, (int)maxPts);
    if (nFloats < 2) return 0;

    sTrailBuf.resize(nFloats);
    sTrailBuf[0] = pX;
    sTrailBuf[1] = pY;

    for (size_t i = 0; i < pTrail.size() && (2 * i + 3) < (size_t)nFloats; i++) {
        sTrailBuf[2 * (i + 1)] = pTrail[i].x;
        sTrailBuf[2 * (i + 1) + 1] = pTrail[i].y;
    }

    env->SetFloatArrayRegion(outPts, 0, nFloats, sTrailBuf.data());
    return nFloats / 2;
}

JNIEXPORT jint JNICALL
Java_com_worm_io_GameEngine_nativeGetFoodData(JNIEnv* env, jclass clazz, jfloatArray outFoods, jintArray outColors) {
    jsize maxColors = env->GetArrayLength(outColors);
    jsize maxFloats = env->GetArrayLength(outFoods);
    int n = std::min((int)gFoods.size(), (int)maxColors);
    n = std::min(n, (int)(maxFloats / 3));

    sFoodBuf.resize(n * 3);
    sColorBuf.resize(n);

    for (int i = 0; i < n; i++) {
        sFoodBuf[3 * i] = gFoods[i].x;
        sFoodBuf[3 * i + 1] = gFoods[i].y;
        sFoodBuf[3 * i + 2] = gFoods[i].r;
        sColorBuf[i] = gFoods[i].c;
    }

    env->SetFloatArrayRegion(outFoods, 0, n * 3, sFoodBuf.data());
    env->SetIntArrayRegion(outColors, 0, n, sColorBuf.data());
    return n;
}

JNIEXPORT jint JNICALL
Java_com_worm_io_GameEngine_nativeGetBotsData(JNIEnv* env, jclass clazz,
                                              jfloatArray outBotInfo,
                                              jfloatArray outTrails,
                                              jintArray outTrailOffsets) {
    int count = gBots.size();
    if (count == 0) return 0;

    jsize maxInfo = env->GetArrayLength(outBotInfo);
    jsize maxOffsets = env->GetArrayLength(outTrailOffsets);
    jsize maxTrails = env->GetArrayLength(outTrails);

    int safeCount = std::min(count, (int)(maxInfo / 6));
    safeCount = std::min(safeCount, (int)(maxOffsets / 2));

    sBotInfoBuf.resize(safeCount * 6);
    sBotOffsetBuf.resize(safeCount * 2);
    sBotTrailBuf.clear();

    int currentOffset = 0;
    for (int i = 0; i < safeCount; i++) {
        sBotInfoBuf[i * 6] = gBots[i].hx;
        sBotInfoBuf[i * 6 + 1] = gBots[i].hy;
        sBotInfoBuf[i * 6 + 2] = gBots[i].angle;
        sBotInfoBuf[i * 6 + 3] = gBots[i].len;
        sBotInfoBuf[i * 6 + 4] = calcRadius(gBots[i].len);
        sBotInfoBuf[i * 6 + 5] = (float)gBots[i].color;

        if (currentOffset + 2 <= maxTrails) {
            sBotTrailBuf.push_back(gBots[i].hx);
            sBotTrailBuf.push_back(gBots[i].hy);
        }

        int tCount = 1;
        for (size_t j = 0; j < gBots[i].trail.size(); j++) {
            if (currentOffset + (tCount + 1) * 2 <= maxTrails) {
                sBotTrailBuf.push_back(gBots[i].trail[j].x);
                sBotTrailBuf.push_back(gBots[i].trail[j].y);
                tCount++;
            } else {
                break;
            }
        }

        sBotOffsetBuf[i * 2] = currentOffset;
        sBotOffsetBuf[i * 2 + 1] = tCount;
        currentOffset += tCount * 2;
    }

    env->SetFloatArrayRegion(outBotInfo, 0, safeCount * 6, sBotInfoBuf.data());
    env->SetIntArrayRegion(outTrailOffsets, 0, safeCount * 2, sBotOffsetBuf.data());
    if (!sBotTrailBuf.empty()) {
        env->SetFloatArrayRegion(outTrails, 0, sBotTrailBuf.size(), sBotTrailBuf.data());
    }
    return safeCount;
}

} // extern "C"