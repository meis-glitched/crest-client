package com.crest.client.core.hud.editor;

import com.crest.client.core.CrestModules;
import com.crest.client.core.HudModule;
import com.crest.client.core.hud.widgets.CategoryHeader;
import com.crest.client.core.hud.widgets.ModuleCard;
import com.crest.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.*;

public class HudModuleSidebar {
    
    private static final int WIDTH = 200;
    private static final int SEARCH_HEIGHT = 28;
    private static final int CATEGORY_HEIGHT = 28;
    private static final int MODULE_HEIGHT = 32;
    private static final int PADDING = 8;
    
    private String searchQuery = "";
    private final Map<String, CategoryHeader> categories = new LinkedHashMap<>();
    private final Map<String, List<ModuleCard>> modulesByCategory = new LinkedHashMap<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean scrollbarDragging = false;
    
    private final Animated slideAnim;
    private boolean visible = true;
    
    public HudModuleSidebar() {
        this.slideAnim = new Animated(1f, 12f);
        initializeCategories();
    }
    
    private void initializeCategories() {
        categories.put("combat", new CategoryHeader("Combat", "⚔️", true));
        categories.put("visuals", new CategoryHeader("Visuals", "👁️", true));
        categories.put("info", new CategoryHeader("Info", "📊", true));
        categories.put("performance", new CategoryHeader("Performance", "⚡", true));
        categories.put("other", new CategoryHeader("Other", "📦", true));
        
        for (String cat : categories.keySet()) {
            modulesByCategory.put(cat, new ArrayList<>());
        }
        
        for (HudModule module : CrestModules.getHudModules()) {
            String category = categorizeModule(module);
            String icon = getModuleIcon(module);
            ModuleCard card = new ModuleCard(module.getId(), module.getName(), icon, module.isEnabled());
            modulesByCategory.get(category).add(card);
        }
    }
    
    private String categorizeModule(HudModule module) {
        String id = module.getId().toLowerCase();
        if (id.contains("cps") || id.contains("combo") || id.contains("reach") || id.contains("armor")) {
            return "combat";
        } else if (id.contains("zoom") || id.contains("particle") || id.contains("effect") || id.contains("animation")) {
            return "visuals";
        } else if (id.contains("fps") || id.contains("tps") || id.contains("ping") || id.contains("coord") || id.contains("clock")) {
            return "info";
        } else if (id.contains("culling") || id.contains("lod") || id.contains("performance")) {
            return "performance";
        }
        return "other";
    }
    
    private String getModuleIcon(HudModule module) {
        String id = module.getId().toLowerCase();
        if (id.contains("cps")) return "⚔️";
        if (id.contains("armor")) return "🛡️";
        if (id.contains("zoom")) return "🔍";
        if (id.contains("fps")) return "📊";
        if (id.contains("clock")) return "🕐";
        if (id.contains("coord")) return "📍";
        if (id.contains("music")) return "🎵";
        if (id.contains("waypoint")) return "🗺️";
        return "📦";
    }
    
    public void render(GuiGraphicsExtractor g, int screenWidth, int screenHeight, int mouseX, int mouseY, float delta) {
        slideAnim.set(visible ? 1f : 0f); slideAnim.tick(delta);
        float slideProgress = slideAnim.get();
        
        if (slideProgress < 0.01f) return;
        
        int x = (int)(-WIDTH * (1f - slideProgress));
        int y = 40;
        int height = screenHeight - y - 10;
        
        Panel.draw(g, x, y, WIDTH, height, ColorUtil.withAlpha(Theme.BACKGROUND, 240));
        Panel.drawHollowRect(g, x, y, WIDTH, height, ColorUtil.withAlpha(Theme.BORDER, 200));
        
        renderSearchBar(g, x + PADDING, y + PADDING, WIDTH - PADDING * 2, mouseX, mouseY);
        
        int contentY = y + PADDING + SEARCH_HEIGHT + PADDING;
        int contentHeight = height - SEARCH_HEIGHT - PADDING * 3;
        
        
        g.enableScissor(x, contentY, x + WIDTH, contentY + contentHeight);
        
        int drawY = contentY - scrollOffset;
        
        for (Map.Entry<String, CategoryHeader> entry : categories.entrySet()) {
            String catKey = entry.getKey();
            CategoryHeader header = entry.getValue();
            
            boolean headerHover = mouseX >= x && mouseX <= x + WIDTH &&
                                 mouseY >= drawY && mouseY <= drawY + CATEGORY_HEIGHT;
            
            header.render(g, x + PADDING, drawY, WIDTH - PADDING * 2, CATEGORY_HEIGHT, headerHover, delta);
            drawY += CATEGORY_HEIGHT + 4;
            
            if (header.isExpanded()) {
                List<ModuleCard> modules = modulesByCategory.get(catKey);
                for (ModuleCard card : modules) {
                    if (!matchesSearch(card)) continue;
                    
                    boolean cardHover = mouseX >= x && mouseX <= x + WIDTH &&
                                       mouseY >= drawY && mouseY <= drawY + MODULE_HEIGHT;
                    
                    card.render(g, x + PADDING + 8, drawY, WIDTH - PADDING * 2 - 8, MODULE_HEIGHT, cardHover, delta);
                    drawY += MODULE_HEIGHT + 2;
                }
            }
            
            drawY += 4;
        }
        
        maxScroll = Math.max(0, drawY - contentY - contentHeight);
        
        g.disableScissor();
        
        
        if (maxScroll > 0) {
            renderScrollbar(g, x + WIDTH - 8, contentY, 6, contentHeight, delta);
        }
    }
    
    private void renderSearchBar(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + SEARCH_HEIGHT;
        
        int bgColor = hover ? ColorUtil.withAlpha(Theme.CARD, 250) : ColorUtil.withAlpha(Theme.CARD, 200);
        Panel.draw(g, x, y, width, SEARCH_HEIGHT, bgColor);
        
        int borderColor = hover ? Theme.ACCENT : Theme.BORDER;
        Panel.drawHollowRect(g, x, y, width, SEARCH_HEIGHT, ColorUtil.withAlpha(borderColor, hover ? 200 : 100));
        
        String searchIcon = "🔍";
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(searchIcon), x + 8, y + (SEARCH_HEIGHT - 10) / 2, Theme.MUTED_FOREGROUND);
        
        String displayText = searchQuery.isEmpty() ? "Search modules..." : searchQuery;
        int textColor = searchQuery.isEmpty() ? Theme.MUTED_FOREGROUND : Theme.FOREGROUND;
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(displayText), x + 24, y + (SEARCH_HEIGHT - net.minecraft.client.Minecraft.getInstance().font.lineHeight) / 2, textColor);
    }
    
    private void renderScrollbar(GuiGraphicsExtractor g, int x, int y, int width, int height, float delta) {
        int trackColor = ColorUtil.withAlpha(Theme.BORDER, 100);
        Panel.draw(g, x, y, width, height, trackColor);
        
        float thumbHeight = Math.max(20, height * ((float)height / (height + maxScroll)));
        float thumbY = y + (scrollOffset / (float)maxScroll) * (height - thumbHeight);
        
        int thumbColor = scrollbarDragging ? Theme.ACCENT : ColorUtil.withAlpha(Theme.MUTED_FOREGROUND, 180);
        Panel.draw(g, x, (int)thumbY, width, (int)thumbHeight, thumbColor);
    }
    
    private boolean matchesSearch(ModuleCard card) {
        if (searchQuery.isEmpty()) return true;
        return card.getDisplayName().toLowerCase().contains(searchQuery.toLowerCase());
    }
    
    public boolean handleClick(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!visible) return false;
        
        int x = 0;
        int y = 40;
        int height = screenHeight - y - 10;
        
        if (mouseX < 0 || mouseX > WIDTH || mouseY < y || mouseY > y + height) {
            return false;
        }
        
        int searchY = y + PADDING;
        if (mouseY >= searchY && mouseY <= searchY + SEARCH_HEIGHT) {
            return true;
        }
        
        int contentY = y + PADDING + SEARCH_HEIGHT + PADDING;
        int drawY = contentY - scrollOffset;
        
        for (Map.Entry<String, CategoryHeader> entry : categories.entrySet()) {
            CategoryHeader header = entry.getValue();
            if (mouseY >= drawY && mouseY <= drawY + CATEGORY_HEIGHT) {
                header.toggle();
                return true;
            }
            drawY += CATEGORY_HEIGHT + 4;
            
            if (header.isExpanded()) {
                List<ModuleCard> modules = modulesByCategory.get(entry.getKey());
                for (ModuleCard card : modules) {
                    if (!matchesSearch(card)) continue;
                    if (mouseY >= drawY && mouseY <= drawY + MODULE_HEIGHT) {
                        toggleModule(card.getModuleId());
                        return true;
                    }
                    drawY += MODULE_HEIGHT + 2;
                }
            }
            drawY += 4;
        }
        
        return true;
    }
    
    private void toggleModule(String moduleId) {
        if (CrestModules.isEnabled(moduleId)) {
            CrestModules.toggle(moduleId);
        } else {
            CrestModules.toggle(moduleId);
        }
    }
    
    public boolean handleScroll(double scrollDelta) {
        if (!visible) return false;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)(scrollDelta * 20)));
        return true;
    }
    
    public boolean handleCharTyped(char chr) {
        if (!visible) return false;
        searchQuery += chr;
        return true;
    }
    
    public boolean handleKeyPress(int key) {
        if (!visible) return false;
        if (key == 259 && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            return true;
        }
        return false;
    }
    
    public void toggle() {
        visible = !visible;
    }
    
    public boolean isVisible() {
        return visible && slideAnim.get() > 0.01f;
    }
    
    public int getWidth() {
        return (int)(WIDTH * slideAnim.get());
    }
}
