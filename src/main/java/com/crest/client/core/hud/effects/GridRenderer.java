package com.crest.client.core.hud.effects;

import com.crest.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class GridRenderer {
    
    private int gridSize = 4;
    private boolean showGrid = true;
    private boolean showCenterLines = true;
    private boolean showRuleOfThirds = false;
    private final Animated gridFadeAnim;
    
    public GridRenderer() {
        this.gridFadeAnim = new Animated(0f, 10f);
    }
    
    public void render(GuiGraphicsExtractor g, int screenWidth, int screenHeight, float delta) {
        if (!showGrid) {
            gridFadeAnim.set(0f); gridFadeAnim.tick(delta);
            return;
        }
        
        gridFadeAnim.set(1f); gridFadeAnim.tick(delta);
        float fadeProgress = gridFadeAnim.get();
        
        if (fadeProgress < 0.01f) return;
        
        int gridAlpha = (int)(30 * fadeProgress);
        int gridColor = ColorUtil.withAlpha(Theme.MUTED_FOREGROUND, gridAlpha);
        
        for (int x = 0; x < screenWidth; x += gridSize) {
            g.fill(x, 0, x + 1, screenHeight, gridColor);
        }
        for (int y = 0; y < screenHeight; y += gridSize) {
            g.fill(0, y, screenWidth, y + 1, gridColor);
        }
        
        if (showCenterLines) {
            int centerAlpha = (int)(60 * fadeProgress);
            int centerColor = ColorUtil.withAlpha(Theme.ACCENT, centerAlpha);
            int centerX = screenWidth / 2;
            int centerY = screenHeight / 2;
            g.fill(centerX, 0, centerX + 1, screenHeight, centerColor);
            g.fill(0, centerY, screenWidth, centerY + 1, centerColor);
        }
        
        if (showRuleOfThirds) {
            int thirdAlpha = (int)(50 * fadeProgress);
            int thirdColor = ColorUtil.withAlpha(Theme.ACCENT, thirdAlpha);
            int thirdX1 = screenWidth / 3;
            int thirdX2 = screenWidth * 2 / 3;
            int thirdY1 = screenHeight / 3;
            int thirdY2 = screenHeight * 2 / 3;
            
            g.fill(thirdX1, 0, thirdX1 + 1, screenHeight, thirdColor);
            g.fill(thirdX2, 0, thirdX2 + 1, screenHeight, thirdColor);
            g.fill(0, thirdY1, screenWidth, thirdY1 + 1, thirdColor);
            g.fill(0, thirdY2, screenWidth, thirdY2 + 1, thirdColor);
        }
    }
    
    public void renderSnapLines(GuiGraphicsExtractor g, int x, int y, int width, int height, 
                                int[] snapX, int[] snapY, float delta) {
        if (snapX != null) {
            for (int snapPosX : snapX) {
                int lineColor = ColorUtil.withAlpha(0xFF0000, 200);
                g.fill(snapPosX, 0, snapPosX + 1, screenHeight, lineColor);
                
                Panel.draw(g, snapPosX - 1, y - 20, 2, 16, lineColor);
                Panel.draw(g, snapPosX - 1, y + height + 4, 2, 16, lineColor);
            }
        }
        
        if (snapY != null) {
            for (int snapPosY : snapY) {
                int lineColor = ColorUtil.withAlpha(0xFF0000, 200);
                g.fill(0, snapPosY, screenWidth, snapPosY + 1, lineColor);
                
                Panel.draw(g, x - 20, snapPosY - 1, 16, 2, lineColor);
                Panel.draw(g, x + width + 4, snapPosY - 1, 16, 2, lineColor);
            }
        }
    }
    
    public int snapToGrid(int value) {
        if (!showGrid || gridSize <= 1) return value;
        return Math.round((float)value / gridSize) * gridSize;
    }
    
    public void setGridSize(int size) {
        this.gridSize = Math.max(1, size);
    }
    
    public int getGridSize() {
        return gridSize;
    }
    
    public void setShowGrid(boolean show) {
        this.showGrid = show;
    }
    
    public boolean isShowGrid() {
        return showGrid;
    }
    
    public void toggleGrid() {
        showGrid = !showGrid;
    }
    
    public void setShowCenterLines(boolean show) {
        this.showCenterLines = show;
    }
    
    public void setShowRuleOfThirds(boolean show) {
        this.showRuleOfThirds = show;
    }
}
