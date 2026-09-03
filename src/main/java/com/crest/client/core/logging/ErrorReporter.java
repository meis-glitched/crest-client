package com.crest.client.core.logging;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ErrorReporter {
    private static final CrestLogger LOGGER = LoggerFactory.getLogger("ErrorReporter");
    
    public static void reportError(String context, Throwable throwable) {
        LOGGER.error("{} failed", throwable, context);
        notifyUser(context + " failed: " + throwable.getMessage());
    }
    
    public static void reportError(String context, String message) {
        LOGGER.error("{}: {}", context, message);
        notifyUser(context + ": " + message);
    }
    
    public static void reportError(String context, String message, Throwable throwable) {
        LOGGER.error("{}: {}", throwable, context, message);
        notifyUser(context + ": " + message);
    }
    
    public static void reportWarning(String context, String message) {
        LOGGER.warn("{}: {}", context, message);
    }
    
    private static void notifyUser(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[Crest] " + message));
        }
    }
    
    public static void logAndIgnore(String context, Throwable throwable) {
        LOGGER.warn("{} encountered non-fatal error (ignored)", throwable, context);
    }
}
