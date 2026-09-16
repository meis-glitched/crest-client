package com.crest.client.catstrokes;

final class ExpressionController {
    private static final int BROW_LY = 0, BROW_RY = 1, BROW_LA = 2, BROW_RA = 3,
            BROW_LF = 4, BROW_RF = 5, MOUTH_FORM = 6, CHEEK = 7, EYE_SMILE = 8,
            EYE_OPEN = 9, DROOP = 10, BLINK = 11;

    private static final float[][] T = {
        {0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.00f, 0.0f, 1.2f},
        {0.05f, 0.05f, -0.12f, 0.12f, 0.30f, 0.30f, 0.05f, 0.15f, 0.05f, 0.95f, 1.0f, 0.85f},
        {0.55f, 0.55f, -0.30f, 0.30f, 0.10f, 0.10f, 0.45f, 0.70f, 0.50f, 1.00f, 0.0f, 0.55f},
        {0.90f, 0.90f, 0.40f, -0.40f, -0.30f, -0.30f, -0.20f, 0.20f, 0.00f, 1.00f, 0.0f, 1.0f},
        {-0.25f, -0.25f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.55f, 12.0f, 3.2f},
        {0.25f, 0.25f, 0.00f, 0.00f, 0.00f, 0.00f, 0.40f, 0.30f, 0.30f, 1.00f, 0.0f, 1.3f}
    };

    private static final double SURPRISE_DURATION = 0.7;
    private static final double YAWN_DURATION = 2.5;
    private static final double SLEEPY_AFTER_IDLE = 30.0;

    private enum Mood { NEUTRAL, FOCUSED, EXCITED, SURPRISED, SLEEPY, HAPPY_IDLE }

    float angleX, angleY, angleZ;
    float bodyAngleX, bodyAngleY, bodyAngleZ;
    float hairFront, hairSide, hairBack;
    private float velX, velY;
    private float prevNX, prevNY;

    float browLY, browRY, browLAngle, browRAngle, browLForm, browRForm;
    float mouthForm, cheek, eyeSmile, eyeOpenMod, blinkMul;
    float mouthOpenOverride = -1f;

    private Mood mood = Mood.NEUTRAL;
    private double moodSince;
    private double surpriseT = -1;
    private double yawnT = -1;
    private double nextYawn;

    private float browLYsm, browRYsm, browLAsm, browRAsm, browLFsm, browRFsm;
    private float mouthFormSm, cheekSm, eyeSmileSm, eyeOpenSm = 1f, droopSm;
    private float blinkSm = 1f;

    void update(float dt, float nx, float ny, boolean mouseMoving, double idleSeconds,
                float pressRate, float clickRate, boolean mouseJump, double activityAge,
                double lifeSeconds) {
        updateHead(dt, nx, ny);
        updateMood(dt, mouseMoving, idleSeconds, pressRate, clickRate, mouseJump, activityAge, lifeSeconds);
        updateExpression(dt);
        updateYawn(dt, lifeSeconds);
        if (surpriseT >= 0) {
            surpriseT += dt;
            if (surpriseT >= SURPRISE_DURATION) {
                surpriseT = -1;
            }
        }
    }

    private void updateHead(float dt, float nx, float ny) {
        float vx = (nx - prevNX) / Math.max(dt, 1.0e-4f);
        float vy = (ny - prevNY) / Math.max(dt, 1.0e-4f);
        prevNX = nx;
        prevNY = ny;
        velX = approach(velX, vx, dt, 6f);
        velY = approach(velY, vy, dt, 6f);

        float targetX = clamp(nx * 30f, -30f, 30f);
        float targetY = clamp(-ny * 30f + droopSm, -30f, 30f);
        float targetZ = clamp(-velX * 1.6f, -6f, 6f);

        angleX = approach(angleX, targetX, dt, 5f);
        angleY = approach(angleY, targetY, dt, 5f);
        angleZ = approach(angleZ, targetZ, dt, 8f);
        bodyAngleX = approach(bodyAngleX, targetX * 0.35f, dt, 4f);
        bodyAngleY = approach(bodyAngleY, targetY * 0.3f, dt, 4f);
        bodyAngleZ = approach(bodyAngleZ, targetZ * 0.5f, dt, 8f);

        hairFront = approach(hairFront, clamp(-velY * 0.9f, -0.4f, 0.4f), dt, 3f);
        hairSide = approach(hairSide, clamp(-velX * 1.2f, -0.4f, 0.4f), dt, 3f);
        hairBack = approach(hairBack, clamp(-velY * 0.4f, -0.25f, 0.25f), dt, 3f);
    }

    private void updateMood(float dt, boolean mouseMoving, double idleSeconds, float pressRate,
                            float clickRate, boolean mouseJump, double activityAge, double lifeSeconds) {
        if (mouseJump && surpriseT < 0) {
            surpriseT = 0;
        }

        Mood desired;
        if (activityAge >= SLEEPY_AFTER_IDLE) {
            desired = Mood.SLEEPY;
        } else if (surpriseT >= 0) {
            desired = Mood.SURPRISED;
        } else if (pressRate >= 4f || clickRate >= 4f) {
            desired = Mood.EXCITED;
        } else if (pressRate >= 0.3f || clickRate >= 0.3f) {
            desired = Mood.FOCUSED;
        } else if (activityAge < 25.0) {
            desired = Mood.HAPPY_IDLE;
        } else {
            desired = Mood.NEUTRAL;
        }

        if (mood != desired) {
            if (desired == Mood.SURPRISED || lifeSeconds - moodSince >= 0.8) {
                mood = desired;
                moodSince = lifeSeconds;
            }
        } else {
            moodSince = lifeSeconds;
        }
    }

    private void updateExpression(float dt) {
        float[] t = T[mood.ordinal()];
        browLYsm = approach(browLYsm, t[BROW_LY], dt, 9f);
        browRYsm = approach(browRYsm, t[BROW_RY], dt, 9f);
        browLAsm = approach(browLAsm, t[BROW_LA], dt, 9f);
        browRAsm = approach(browRAsm, t[BROW_RA], dt, 9f);
        browLFsm = approach(browLFsm, t[BROW_LF], dt, 9f);
        browRFsm = approach(browRFsm, t[BROW_RF], dt, 9f);
        mouthFormSm = approach(mouthFormSm, t[MOUTH_FORM], dt, 9f);
        cheekSm = approach(cheekSm, t[CHEEK], dt, 7f);
        eyeSmileSm = approach(eyeSmileSm, t[EYE_SMILE], dt, 9f);
        eyeOpenSm = approach(eyeOpenSm, t[EYE_OPEN], dt, 6f);
        droopSm = approach(droopSm, t[DROOP], dt, 2.5f);
        blinkSm = approach(blinkSm, t[BLINK], dt, 4f);

        browLY = browLYsm;
        browRY = browRYsm;
        browLAngle = browLAsm;
        browRAngle = browRAsm;
        browLForm = browLFsm;
        browRForm = browRFsm;
        mouthForm = mouthFormSm;
        cheek = cheekSm;
        eyeSmile = eyeSmileSm;
        blinkMul = clamp(blinkSm, 0.3f, 4f);
    }

    private void updateYawn(float dt, double lifeSeconds) {
        if (mood == Mood.SLEEPY && yawnT < 0 && lifeSeconds >= nextYawn) {
            yawnT = 0;
        }

        mouthOpenOverride = -1f;
        float yawnBell = -1f;
        if (yawnT >= 0) {
            yawnT += dt;
            float y = (float) Math.min(yawnT / YAWN_DURATION, 1.0);
            yawnBell = (float) Math.sin(y * Math.PI);
            mouthOpenOverride = 0.75f * yawnBell;
            if (yawnT >= YAWN_DURATION) {
                yawnT = -1;
                nextYawn = lifeSeconds + 40.0 + Math.random() * 30.0;
            }
        }

        if (surpriseT >= 0 && yawnBell < 0f) {
            float normSurprise = (float) (surpriseT / SURPRISE_DURATION);
            mouthOpenOverride = 0.4f * (1f - normSurprise);
        }

        float eyeOpen = eyeOpenSm;
        if (yawnBell >= 0f) {
            eyeOpen = Math.min(eyeOpen, 0.2f + 0.35f * (1f - yawnBell));
        }
        eyeOpenMod = eyeOpen;
    }

    private static float approach(float cur, float target, float dt, float rate) {
        if (cur == target) {
            return target;
        }
        float k = 1f - (float) Math.exp(-dt * rate);
        return cur + (target - cur) * k;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}