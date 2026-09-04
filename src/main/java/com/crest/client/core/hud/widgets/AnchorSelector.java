package com.crest.client.core.hud.widgets;

import com.crest.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class AnchorSelector {
    
    private static final int GRID_SIZE = 3;
    private static final int CELL_SIZE = 24;
    private static final int CELL_GAP = 4;
    
    private int selectedAnchor;
    private final Animated hoverAnim;
    private int hoveredAnchor = -1;
    
    public AnchorSelector(int initialAnchor) {
        this.selectedAnchor = initialAnchor;
        this.hoverAnim = new Animated(0f, 8f);
    }
    
    public void render(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY, float delta) {
        int totalSize = GRID_SIZE * CELL_SIZE + (GRID_SIZE - 1) * CELL_GAP;
        
        hoveredAnchor = -1;
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int anchorIndex = row * GRID_SIZE + col;
                int cellX = x + col * (CELL_SIZE + CELL_GAP);
                int cellY = y + row * (CELL_SIZE + CELL_GAP);
                
                boolean isHovered = mouseX >= cellX && mouseX < cellX + CELL_SIZE &&
                                   mouseY >= cellY && mouseY < cellY + CELL_SIZE;
                if (isHovered) hoveredAnchor = anchorIndex;
                
                boolean isSelected = anchorIndex == selectedAnchor;
                renderCell(g, cellX, cellY, isSelected, isHovered, delta);
            }
        }
    }
    
    private void renderCell(GuiGraphicsExtractor g, int x, int y, boolean selected, boolean hovered, float delta) {
        int bgColor;
        if (selected) {
            bgColor = ColorUtil.withAlpha(Theme.ACCENT, 200);
        } else if (hovered) {
            bgColor = ColorUtil.withAlpha(Theme.ACCENT, 100);
        } else {
            bgColor = ColorUtil.withAlpha(Theme.CARD, 150);
        }
        
        Panel.draw(g, x, y, CELL_SIZE, CELL_SIZE, bgColor);
        
        int borderColor;
        if (selected) {
            borderColor = ColorUtil.withAlpha(Theme.ACCENT, 255);
        } else {
            borderColor = ColorUtil.withAlpha(Theme.BORDER, hovered ? 200 : 100);
        }
        Panel.drawHollowRect(g, x, y, CELL_SIZE, CELL_SIZE, borderColor);
        
        int dotSize = selected ? 6 : 4;
        int dotX = x + (CELL_SIZE - dotSize) / 2;
        int dotY = y + (CELL_SIZE - dotSize) / 2;
        int dotColor = selected ? 0xFFFFFFFF : ColorUtil.withAlpha(Theme.FOREGROUND, hovered ? 200 : 150);
        Panel.draw(g, dotX, dotY, dotSize, dotSize, dotColor);
    }
    
    public boolean handleClick(int mouseX, int mouseY, int x, int y) {
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int cellX = x + col * (CELL_SIZE + CELL_GAP);
                int cellY = y + row * (CELL_SIZE + CELL_GAP);
                
                if (mouseX >= cellX && mouseX < cellX + CELL_SIZE &&
                    mouseY >= cellY && mouseY < cellY + CELL_SIZE) {
                    selectedAnchor = row * GRID_SIZE + col;
                    return true;
                }
            }
        }
        return false;
    }
    
    public int getSelectedAnchor() {
        return selectedAnchor;
    }
    
    public void setSelectedAnchor(int anchor) {
        this.selectedAnchor = anchor;
    }
    
    public int getWidth() {
        return GRID_SIZE * CELL_SIZE + (GRID_SIZE - 1) * CELL_GAP;
    }
    
    public int getHeight() {
        return GRID_SIZE * CELL_SIZE + (GRID_SIZE - 1) * CELL_GAP;
    }
}
