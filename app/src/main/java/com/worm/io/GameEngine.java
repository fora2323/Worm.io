package com.worm.io;

public class GameEngine {
    static {
        System.loadLibrary("worm_engine");
    }

    public static native void nativeInitWorld(float worldSize, int foodCount);
    public static native void nativeInitBots(int count);
    public static native void nativeUpdate(float joyDx, float joyDy, boolean joyActive, boolean isBoosting, float[] outState);
    public static native int nativeGetTrail(float[] outPts);
    public static native void nativeGetFoodData(float[] outFoods, int[] outColors);
    public static native int nativeGetBotsData(float[] outBotInfo, float[] outTrails, int[] outTrailOffsets);
}