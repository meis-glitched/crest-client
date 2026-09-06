package com.crest.client.core.hud.editor;

import com.crest.client.core.HudModule;

import java.util.List;

public class HudAlignmentTools {
    
    public static void alignLeft(List<HudModule> modules, int screenWidth) {
        if (modules.isEmpty()) return;
        int minX = modules.stream().mapToInt(HudModule::getX).min().orElse(0);
        for (HudModule module : modules) {
            module.setX(minX); module.setY(module.getY());
        }
    }
    
    public static void alignRight(List<HudModule> modules, int screenWidth) {
        if (modules.isEmpty()) return;
        int maxX = modules.stream().mapToInt(m -> m.getX() + m.getRenderWidth()).max().orElse(screenWidth);
        for (HudModule module : modules) {
            module.setX(maxX - module.getRenderWidth()); module.setY(module.getY());
        }
    }
    
    public static void alignTop(List<HudModule> modules, int screenHeight) {
        if (modules.isEmpty()) return;
        int minY = modules.stream().mapToInt(HudModule::getY).min().orElse(0);
        for (HudModule module : modules) {
            module.setX(module.getX()); module.setY(minY);
        }
    }
    
    public static void alignBottom(List<HudModule> modules, int screenHeight) {
        if (modules.isEmpty()) return;
        int maxY = modules.stream().mapToInt(m -> m.getY() + m.getRenderHeight()).max().orElse(screenHeight);
        for (HudModule module : modules) {
            module.setX(module.getX()); module.setY(maxY - module.getRenderHeight());
        }
    }
    
    public static void alignCenterHorizontal(List<HudModule> modules, int screenWidth) {
        if (modules.isEmpty()) return;
        int centerX = screenWidth / 2;
        for (HudModule module : modules) {
            module.setX(centerX - module.getRenderWidth() / 2); module.setY(module.getY());
        }
    }
    
    public static void alignCenterVertical(List<HudModule> modules, int screenHeight) {
        if (modules.isEmpty()) return;
        int centerY = screenHeight / 2;
        for (HudModule module : modules) {
            module.setX(module.getX()); module.setY(centerY - module.getRenderHeight() / 2);
        }
    }
    
    public static void distributeHorizontally(List<HudModule> modules, int screenWidth) {
        if (modules.size() < 2) return;
        
        modules.sort((a, b) -> Integer.compare(a.getX(), b.getX()));
        
        int minX = modules.get(0).getX();
        int maxX = modules.get(modules.size() - 1).getX() + modules.get(modules.size() - 1).getRenderWidth();
        int totalWidth = modules.stream().mapToInt(HudModule::getRenderWidth).sum();
        int spacing = (maxX - minX - totalWidth) / (modules.size() - 1);
        
        int currentX = minX;
        for (HudModule module : modules) {
            module.setX(currentX); module.setY(module.getY());
            currentX += module.getRenderWidth() + spacing;
        }
    }
    
    public static void distributeVertically(List<HudModule> modules, int screenHeight) {
        if (modules.size() < 2) return;
        
        modules.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        
        int minY = modules.get(0).getY();
        int maxY = modules.get(modules.size() - 1).getY() + modules.get(modules.size() - 1).getRenderHeight();
        int totalHeight = modules.stream().mapToInt(HudModule::getRenderHeight).sum();
        int spacing = (maxY - minY - totalHeight) / (modules.size() - 1);
        
        int currentY = minY;
        for (HudModule module : modules) {
            module.setX(module.getX()); module.setY(currentY);
            currentY += module.getRenderHeight() + spacing;
        }
    }
    
    public static int[][] findSnapLines(HudModule dragging, List<HudModule> others, int threshold) {
        int[] snapX = null;
        int[] snapY = null;
        
        int dragLeft = dragging.getX();
        int dragRight = dragging.getX() + dragging.getRenderWidth();
        int dragCenterX = dragging.getX() + dragging.getRenderWidth() / 2;
        int dragTop = dragging.getY();
        int dragBottom = dragging.getY() + dragging.getRenderHeight();
        int dragCenterY = dragging.getY() + dragging.getRenderHeight() / 2;
        
        int closestX = Integer.MAX_VALUE;
        int closestY = Integer.MAX_VALUE;
        int snapPosX = -1;
        int snapPosY = -1;
        
        for (HudModule other : others) {
            if (other == dragging) continue;
            
            int otherLeft = other.getX();
            int otherRight = other.getX() + other.getRenderWidth();
            int otherCenterX = other.getX() + other.getRenderWidth() / 2;
            int otherTop = other.getY();
            int otherBottom = other.getY() + other.getRenderHeight();
            int otherCenterY = other.getY() + other.getRenderHeight() / 2;
            
            int distLeft = Math.abs(dragLeft - otherLeft);
            if (distLeft < closestX && distLeft <= threshold) {
                closestX = distLeft;
                snapPosX = otherLeft;
            }
            
            int distRight = Math.abs(dragRight - otherRight);
            if (distRight < closestX && distRight <= threshold) {
                closestX = distRight;
                snapPosX = otherRight;
            }
            
            int distCenterX = Math.abs(dragCenterX - otherCenterX);
            if (distCenterX < closestX && distCenterX <= threshold) {
                closestX = distCenterX;
                snapPosX = otherCenterX;
            }
            
            int distTop = Math.abs(dragTop - otherTop);
            if (distTop < closestY && distTop <= threshold) {
                closestY = distTop;
                snapPosY = otherTop;
            }
            
            int distBottom = Math.abs(dragBottom - otherBottom);
            if (distBottom < closestY && distBottom <= threshold) {
                closestY = distBottom;
                snapPosY = otherBottom;
            }
            
            int distCenterY = Math.abs(dragCenterY - otherCenterY);
            if (distCenterY < closestY && distCenterY <= threshold) {
                closestY = distCenterY;
                snapPosY = otherCenterY;
            }
        }
        
        if (snapPosX != -1) snapX = new int[]{snapPosX};
        if (snapPosY != -1) snapY = new int[]{snapPosY};
        
        return new int[][]{snapX, snapY};
    }
    
    public static void snapToPosition(HudModule module, int[] snapX, int[] snapY) {
        if (snapX != null && snapX.length > 0) {
            int targetX = snapX[0];
            int width = module.getRenderWidth();
            int centerX = module.getX() + width / 2;
            int rightX = module.getX() + width;
            
            if (Math.abs(module.getX() - targetX) < Math.abs(centerX - targetX) &&
                Math.abs(module.getX() - targetX) < Math.abs(rightX - targetX)) {
                module.setX(targetX); module.setY(module.getY());
            } else if (Math.abs(centerX - targetX) < Math.abs(rightX - targetX)) {
                module.setX(targetX - width / 2); module.setY(module.getY());
            } else {
                module.setX(targetX - width); module.setY(module.getY());
            }
        }
        
        if (snapY != null && snapY.length > 0) {
            int targetY = snapY[0];
            int height = module.getRenderHeight();
            int centerY = module.getY() + height / 2;
            int bottomY = module.getY() + height;
            
            if (Math.abs(module.getY() - targetY) < Math.abs(centerY - targetY) &&
                Math.abs(module.getY() - targetY) < Math.abs(bottomY - targetY)) {
                module.setX(module.getX()); module.setY(targetY);
            } else if (Math.abs(centerY - targetY) < Math.abs(bottomY - targetY)) {
                module.setX(module.getX()); module.setY(targetY - height / 2);
            } else {
                module.setX(module.getX()); module.setY(targetY - height);
            }
        }
    }
}
