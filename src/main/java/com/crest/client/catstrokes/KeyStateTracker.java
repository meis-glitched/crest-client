package com.crest.client.catstrokes;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class KeyStateTracker {
    private static KeyStateTracker instance;

    private static final int[] RAW_KEYS = {
        GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3,
        GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6,
        GLFW.GLFW_KEY_7,
        GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_R,
        GLFW.GLFW_KEY_SPACE,
        GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_W
    };

    private final boolean[] pressed = new boolean[15];
    private int lastPressedIndex = -1;
    private boolean initialized;

    private double cursorX, cursorY;
    private boolean leftMouseDown;
    private boolean rightMouseDown;
    
    // Reused scratch buffers so update() allocates nothing per frame.
    private final double[] cursorXBuf = new double[1];
    private final double[] cursorYBuf = new double[1];

    public static KeyStateTracker getInstance() {
        if (instance == null) instance = new KeyStateTracker();
        return instance;
    }

    public boolean tryInit() {
        if (initialized) return true;
        initialized = true;
        return true;
    }

    public void update() {
        long window = GLFW.glfwGetCurrentContext();
        if (window == 0) return;

        boolean anyPressed = false;
        for (int i = 0; i < 15; i++) {
            boolean down = GLFW.glfwGetKey(window, RAW_KEYS[i]) == GLFW.GLFW_PRESS;
            if (down && !pressed[i]) {
                lastPressedIndex = i;
            }
            pressed[i] = down;
            if (down) anyPressed = true;
        }
        if (!anyPressed) {
            lastPressedIndex = -1;
        }

        GLFW.glfwGetCursorPos(window, cursorXBuf, cursorYBuf);
        cursorX = cursorXBuf[0];
        cursorY = cursorYBuf[0];
        
        // Track mouse buttons
        leftMouseDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        rightMouseDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
    }

    public boolean isPressed(int index) {
        return index >= 0 && index < 15 && pressed[index];
    }

    public boolean anyPressed() {
        for (int i = 0; i < 15; i++) {
            if (pressed[i]) return true;
        }
        return false;
    }

    public int getLastPressedIndex() {
        return lastPressedIndex;
    }

    public double getCursorX() { return cursorX; }
    public double getCursorY() { return cursorY; }
    
    public boolean isLeftMouseDown() { return leftMouseDown; }
    public boolean isRightMouseDown() { return rightMouseDown; }
}
