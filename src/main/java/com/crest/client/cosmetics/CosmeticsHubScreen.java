package com.crest.client.cosmetics;

import com.crest.client.cosmetics.ui.SkinCard;
import com.crest.client.cosmetics.ui.CapeCard;
import com.crest.client.core.ScreenAnim;
import com.crest.client.core.SkinChanger;
import com.crest.client.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CosmeticsHubScreen extends Screen {
    private final Screen parent;
    
    private static final String[] TABS = {"Skins", "Capes", "3D Layers"};
    private int currentTab = 0;
    
    private final ModelViewer3D modelViewer = new ModelViewer3D();
    private final ScrollContainer scrollContainer = new ScrollContainer();
    private final ScreenAnim anim = new ScreenAnim();
    private final SearchBar searchBar;
    
    private int mx, my;
    private int modelX, modelY, modelW, modelH;
    private int contentX, contentY, contentW, contentH;
    private int tabBarY, tabBarH = 36;
    private int actionBarY, actionBarH = 32;
    
    private List<Widget> currentItems = new ArrayList<>();
    private String searchQuery = "";
    private String statusMessage = "";
    private boolean isLoading = false;
    private String appliedSkinId = SkinChanger.getAppliedLibraryId();
    
    public CosmeticsHubScreen(Screen parent) {
        super(Component.literal("Cosmetics"));
        this.parent = parent;
        this.searchBar = new SearchBar(this::onSearch, "Search cosmetics...");
        anim.begin();
    }
    
    @Override
    protected void init() {
        computeLayout();
        refreshItems();
    }
    
    private void computeLayout() {
        Breakpoints.Size size = Breakpoints.getCurrentSize(width);
        boolean compact = size == Breakpoints.Size.XS || size == Breakpoints.Size.SM;
        
        int headerH = 40;  // Reserve space for header
        int topMargin = Spacing.S4;  // 16px
        
        if (compact) {
            modelW = Math.min(280, width - Spacing.S4 * 2);
            modelH = 340;
            modelX = (width - modelW) / 2;
            modelY = headerH + topMargin;
            
            contentX = Spacing.S4;
            contentY = modelY + modelH + Spacing.S4;
            contentW = width - Spacing.S4 * 2;
            contentH = height - contentY - Spacing.S4;
            tabBarY = contentY;
        } else {
            modelW = Math.min(380, (int)(width * 0.28f));
            modelH = height - (headerH + topMargin) - Spacing.S6;
            modelX = Spacing.S6;
            modelY = headerH + topMargin;
            
            contentX = modelX + modelW + Spacing.S6;
            contentY = headerH + topMargin;
            contentW = width - contentX - Spacing.S6;
            contentH = height - (headerH + topMargin) - Spacing.S6;
            tabBarY = contentY;
        }
        
        // Calculate proper model viewer bounds - MAXIMIZE the model viewer
        int previewTitleH = 20;  // Minimal space for "Preview" text
        int resetRandomH = 32;   // Minimal space for buttons
        int innerPadding = 6;    // Small padding
        
        int viewerX = modelX + innerPadding;
        int viewerY = modelY + previewTitleH;
        int viewerW = modelW - (innerPadding * 2);
        int viewerH = modelH - previewTitleH - resetRandomH;
        
        modelViewer.setPosition(viewerX, viewerY, viewerW, viewerH);
        scrollContainer.h = contentH - tabBarH - Spacing.S4 - 44;
    }
    
    private void onSearch(String query) {
        searchQuery = query.toLowerCase();
        refreshItems();
    }
    
    private void refreshItems() {
        currentItems.clear();
        
        switch (currentTab) {
            case 0 -> refreshSkins();
            case 1 -> refreshCapes();
            case 2 -> refresh3DLayers();
        }
        
        scrollContainer.children(currentItems);
    }
    
    private void refreshSkins() {
        currentItems.clear();
        
        // Add action buttons at the top
        currentItems.add(new ActionButtonWidget("+ From Username", this::openUsernameDialog));
        currentItems.add(new ActionButtonWidget("+ Upload File", this::openSkinUpload));
        
        List<SkinLibrary.SkinEntry> skins = searchQuery.isEmpty() 
            ? SkinLibrary.getAllSkins() 
            : filterSkins(SkinLibrary.getAllSkins());
        
        for (SkinLibrary.SkinEntry skin : skins) {
            SkinCard card = new SkinCard(skin, this::onSkinSelected, this::onSkinApply, this::onSkinFavorite, this::onSkinDelete);
            card.setEquipped(skin.id.equals(appliedSkinId));
            currentItems.add(card);
        }
    }
    
    private void refreshCapes() {
        currentItems.clear();
        
        // Add action button at the top
        currentItems.add(new ActionButtonWidget("+ Upload Cape", this::openCapeUpload));
        
        List<CapeLibrary.CapeEntry> capes = searchQuery.isEmpty() 
            ? CapeLibrary.getAllCapes() 
            : filterCapes(CapeLibrary.getAllCapes());
        
        for (CapeLibrary.CapeEntry cape : capes) {
            currentItems.add(new CapeCard(cape, this::onCapeSelected, this::onCapeFavorite, this::onCapeDelete));
        }
    }
    
    private void refresh3DLayers() {
        currentItems.clear();
        currentItems.add(new InfoWidget("3D Layers settings - Coming soon!"));
    }
    
    private List<SkinLibrary.SkinEntry> filterSkins(List<SkinLibrary.SkinEntry> skins) {
        return skins.stream()
            .filter(s -> s.name.toLowerCase().contains(searchQuery))
            .toList();
    }
    
    private List<CapeLibrary.CapeEntry> filterCapes(List<CapeLibrary.CapeEntry> capes) {
        return capes.stream()
            .filter(c -> c.name.toLowerCase().contains(searchQuery))
            .toList();
    }
    
    private void onSkinSelected(String skinId) {
        PlayerSkin skin = SkinLibrary.getPlayerSkin(skinId);
        if (skin != null) {
            modelViewer.setSkin(skin);
            SkinLibrary.addToHistory(skinId);
        }
    }
    
    private void onSkinApply(String skinId) {
        PlayerSkin skin = SkinLibrary.getPlayerSkin(skinId);
        if (skin != null) {
            SkinChanger.applySkin(skin, "lib:" + skinId);
            modelViewer.setSkin(skin);
            SkinLibrary.addToHistory(skinId);
            appliedSkinId = skinId;
            statusMessage = "Applied skin to your player";
        } else {
            statusMessage = "Failed to apply skin";
        }
    }
    
    private void onSkinFavorite(String skinId) {
        SkinLibrary.toggleFavorite(skinId);
        refreshItems();
    }
    
    private void onSkinDelete(String skinId) {
        SkinLibrary.removeSkin(skinId);
        refreshItems();
    }
    
    private void onCapeSelected(String capeId) {
        CapeLibrary.setActiveCape(capeId);
        modelViewer.setCape(CapeLibrary.getTexture(capeId));
    }
    
    private void onCapeFavorite(String capeId) {
        CapeLibrary.toggleFavorite(capeId);
        refreshItems();
    }
    
    private void onCapeDelete(String capeId) {
        CapeLibrary.removeCape(capeId);
        refreshItems();
    }
    
    private void openUsernameDialog() {
        // Simple prompt - in production this would be a proper dialog
        minecraft.setScreen(new UsernameInputScreen(this, username -> {
            isLoading = true;
            statusMessage = "Downloading skin for " + username + "...";
            SkinLibrary.addFromUsername(username, (skinId) -> {
                isLoading = false;
                statusMessage = "Added skin: " + username;
                refreshItems();
                // Apply the downloaded skin to the model viewer
                onSkinSelected(skinId);
            }, () -> {
                isLoading = false;
                statusMessage = "Failed to find player: " + username;
            });
        }));
    }
    
    private void openSkinUpload() {
        java.awt.FileDialog fd = new java.awt.FileDialog((java.awt.Frame) null, "Select skin PNG");
        fd.setMode(java.awt.FileDialog.LOAD);
        fd.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".png"));
        fd.setVisible(true);
        String file = fd.getFile();
        if (file != null) {
            File chosen = new File(fd.getDirectory(), file);
            try {
                String id = SkinLibrary.addFromFile(chosen);
                statusMessage = "Added skin: " + chosen.getName();
                refreshItems();
                onSkinSelected(id);
            } catch (Exception e) {
                statusMessage = "Failed to add skin: " + e.getMessage();
            }
        }
    }
    
    private void openCapeUpload() {
        java.awt.FileDialog fd = new java.awt.FileDialog((java.awt.Frame) null, "Select cape PNG");
        fd.setMode(java.awt.FileDialog.LOAD);
        fd.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".png"));
        fd.setVisible(true);
        String file = fd.getFile();
        if (file != null) {
            File chosen = new File(fd.getDirectory(), file);
            try {
                String id = CapeLibrary.addFromFile(chosen);
                statusMessage = "Added cape: " + chosen.getName();
                refreshItems();
                onCapeSelected(id);
            } catch (Exception e) {
                statusMessage = "Failed to add cape: " + e.getMessage();
            }
        }
    }
    
    // Simple action button widget
    private static class ActionButtonWidget implements Widget {
        private final String label;
        private final Runnable action;
        private int lastX, lastY, lastW;
        
        public ActionButtonWidget(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
        
        @Override
        public int getWidth() { return lastW; }
        
        @Override
        public int getHeight() { return 40; }
        
        @Override
        public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mx, int my, float delta) {
            lastX = x; lastY = y; lastW = w;
            boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + 36;
            
            int bg = hover ? ColorUtil.withAlpha(Theme.ACCENT, 40) : ColorUtil.withAlpha(Theme.SURFACE_VARIANT, 80);
            g.fill(x, y, x + w, y + 36, bg);
            Panel.drawHollowRect(g, x, y, w, 36, hover ? Theme.ACCENT : Theme.BORDER);
            
            g.centeredText(font, Component.literal(label), x + w / 2, y + 14, hover ? Theme.ACCENT : Theme.FOREGROUND);
        }
        
        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && mx >= lastX && mx <= lastX + lastW && my >= lastY && my <= lastY + 36) {
                action.run();
                return true;
            }
            return false;
        }
        
        @Override
        public boolean mouseDragged(double mx, double my) { return false; }
    }
    
    // Simple info widget
    private static class InfoWidget implements Widget {
        private final String text;
        
        public InfoWidget(String text) {
            this.text = text;
        }
        
        @Override
        public int getWidth() { return 0; }
        
        @Override
        public int getHeight() { return 60; }
        
        @Override
        public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mx, int my, float delta) {
            g.fill(x, y, x + w, y + 50, ColorUtil.withAlpha(Theme.SURFACE_VARIANT, 60));
            Panel.drawHollowRect(g, x, y, w, 50, Theme.BORDER);
            g.centeredText(font, Component.literal(text), x + w / 2, y + 20, Theme.MUTED_FOREGROUND);
        }
        
        @Override
        public boolean mouseClicked(double mx, double my, int button) { return false; }
        
        @Override
        public boolean mouseDragged(double mx, double my) { return false; }
    }
    
    // Simple username input screen
    private static class UsernameInputScreen extends Screen {
        private final Screen parent;
        private final java.util.function.Consumer<String> onSubmit;
        private String username = "";
        
        protected UsernameInputScreen(Screen parent, java.util.function.Consumer<String> onSubmit) {
            super(Component.literal("Enter Username"));
            this.parent = parent;
            this.onSubmit = onSubmit;
        }
        
        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            g.fill(0, 0, width, height, ColorUtil.withAlpha(Theme.BACKGROUND, 200));
            
            int dialogW = 320;
            int dialogH = 140;
            int dialogX = (width - dialogW) / 2;
            int dialogY = (height - dialogH) / 2;
            
            Panel.drawElevated(g, dialogX, dialogY, dialogW, dialogH, ColorUtil.withAlpha(Theme.CARD, 250), Theme.ELEVATION_3);
            
            g.centeredText(font, Component.literal("Enter Minecraft Username"), dialogX + dialogW / 2, dialogY + 20, Theme.FOREGROUND);
            
            int inputX = dialogX + 20;
            int inputY = dialogY + 50;
            int inputW = dialogW - 40;
            g.fill(inputX, inputY, inputX + inputW, inputY + 24, ColorUtil.withAlpha(Theme.BACKGROUND, 180));
            Panel.drawHollowRect(g, inputX, inputY, inputW, 24, Theme.BORDER);
            g.text(font, Component.literal(username.isEmpty() ? "Username..." : username), inputX + 8, inputY + 8, username.isEmpty() ? Theme.MUTED_FOREGROUND : Theme.FOREGROUND);
            
            int btnY = dialogY + 90;
            int btnW = 80;
            int cancelX = dialogX + dialogW / 2 - btnW - 10;
            int okX = dialogX + dialogW / 2 + 10;
            
            boolean cancelHover = mx >= cancelX && mx <= cancelX + btnW && my >= btnY && my <= btnY + 28;
            boolean okHover = mx >= okX && mx <= okX + btnW && my >= btnY && my <= btnY + 28;
            
            g.fill(cancelX, btnY, cancelX + btnW, btnY + 28, ColorUtil.withAlpha(cancelHover ? Theme.MUTED : Theme.BACKGROUND, 200));
            Panel.drawHollowRect(g, cancelX, btnY, btnW, 28, Theme.BORDER);
            g.centeredText(font, Component.literal("Cancel"), cancelX + btnW / 2, btnY + 10, Theme.FOREGROUND);
            
            g.fill(okX, btnY, okX + btnW, btnY + 28, ColorUtil.withAlpha(okHover ? Theme.ACCENT : Theme.SURFACE_VARIANT, 200));
            Panel.drawHollowRect(g, okX, btnY, btnW, 28, okHover ? Theme.ACCENT : Theme.BORDER);
            g.centeredText(font, Component.literal("OK"), okX + btnW / 2, btnY + 10, okHover ? Theme.ACCENT : Theme.FOREGROUND);
        }
        
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.buttonInfo().input() != 0) return false;
            
            int dialogW = 320;
            int dialogH = 140;
            int dialogX = (width - dialogW) / 2;
            int dialogY = (height - dialogH) / 2;
            
            int btnY = dialogY + 90;
            int btnW = 80;
            int cancelX = dialogX + dialogW / 2 - btnW - 10;
            int okX = dialogX + dialogW / 2 + 10;
            
            double mx = event.x(), my = event.y();
            
            if (mx >= cancelX && mx <= cancelX + btnW && my >= btnY && my <= btnY + 28) {
                minecraft.setScreen(parent);
                return true;
            }
            
            if (mx >= okX && mx <= okX + btnW && my >= btnY && my <= btnY + 28) {
                if (!username.trim().isEmpty()) {
                    minecraft.setScreen(parent);
                    onSubmit.accept(username.trim());
                }
                return true;
            }
            
            return super.mouseClicked(event, doubleClick);
        }
        
        @Override
        public boolean keyPressed(KeyEvent event) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                minecraft.setScreen(parent);
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                if (!username.trim().isEmpty()) {
                    minecraft.setScreen(parent);
                    onSubmit.accept(username.trim());
                }
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !username.isEmpty()) {
                username = username.substring(0, username.length() - 1);
                return true;
            }
            return super.keyPressed(event);
        }
        
        @Override
        public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
            char codepoint = (char) event.codepoint();
            if (username.length() < 16 && (Character.isLetterOrDigit(codepoint) || codepoint == '_')) {
                username += codepoint;
                return true;
            }
            return super.charTyped(event);
        }
        
        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }
        
        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        this.mx = mx;
        this.my = my;
        Theme.tick(delta);
        anim.update(delta);
        
        anim.backdrop(g, width, height);
        
        int wy = anim.slideY(16);
        g.pose().pushMatrix();
        g.pose().translate(0, wy);
        
        drawModelPanel(g, delta);
        drawContentPanel(g, delta);
        drawHeader(g);
        
        g.pose().popMatrix();
    }
    
    private void drawHeader(GuiGraphicsExtractor g) {
        int headerY = Spacing.S4;
        int backW = font.width("← Back") + Spacing.S4;
        boolean backHover = mx >= Spacing.S4 && mx <= Spacing.S4 + backW && my >= headerY && my <= headerY + 24;
        
        if (backHover) {
            g.fill(Spacing.S4, headerY, Spacing.S4 + backW, headerY + 24, ColorUtil.withAlpha(Theme.ACCENT, 20));
        }
        
        g.text(font, Component.literal("← Back"), Spacing.S4 + Spacing.S2, headerY + 6, backHover ? Theme.ACCENT : Theme.FOREGROUND);
        
        String title = "COSMETICS HUB";
        int titleW = font.width(title);
        g.text(font, Component.literal(title), (width - titleW) / 2, headerY + 6, Theme.FOREGROUND);
    }
    
    private void drawModelPanel(GuiGraphicsExtractor g, float delta) {
        Panel.drawElevated(g, modelX, modelY, modelW, modelH, ColorUtil.withAlpha(Theme.CARD, 235), Theme.ELEVATION_2);
        g.fill(modelX + 3, modelY + 2, modelX + modelW - 3, modelY + 3, ColorUtil.withAlpha(Theme.ACCENT, 120));
        
        g.text(font, Component.literal("Preview"), modelX + Spacing.S3, modelY + Spacing.S3, Theme.FOREGROUND);
        
        modelViewer.render(g, mx, my, delta);
        
        int btnY = modelY + modelH - 40;
        int btnW = (modelW - Spacing.S6) / 2 - Spacing.S2;
        int resetX = modelX + Spacing.S3;
        int randomX = resetX + btnW + Spacing.S2;
        
        boolean resetHover = mx >= resetX && mx <= resetX + btnW && my >= btnY && my <= btnY + 28;
        boolean randomHover = mx >= randomX && mx <= randomX + btnW && my >= btnY && my <= btnY + 28;
        
        g.fill(resetX, btnY, resetX + btnW, btnY + 28, ColorUtil.lerpARGB(Theme.BACKGROUND, Theme.FOREGROUND, resetHover ? 0.2f : 0.1f));
        Panel.drawHollowRect(g, resetX, btnY, btnW, 28, Theme.BORDER);
        g.centeredText(font, Component.literal("Reset"), resetX + btnW / 2, btnY + 10, Theme.FOREGROUND);
        
        g.fill(randomX, btnY, randomX + btnW, btnY + 28, ColorUtil.lerpARGB(Theme.BACKGROUND, Theme.FOREGROUND, randomHover ? 0.2f : 0.1f));
        Panel.drawHollowRect(g, randomX, btnY, btnW, 28, Theme.BORDER);
        g.centeredText(font, Component.literal("Random"), randomX + btnW / 2, btnY + 10, Theme.FOREGROUND);
    }
    
    private void drawContentPanel(GuiGraphicsExtractor g, float delta) {
        Panel.drawElevated(g, contentX, contentY, contentW, contentH, ColorUtil.withAlpha(Theme.CARD, 235), Theme.ELEVATION_2);
        g.fill(contentX + 3, contentY + 2, contentX + contentW - 3, contentY + 3, ColorUtil.withAlpha(Theme.ACCENT, 120));
        
        drawTabs(g);
        
        int searchY = tabBarY + tabBarH + Spacing.S2;
        searchBar.render(g, font, contentX + Spacing.S3, searchY, contentW - Spacing.S6, mx, my, delta);
        
        int scrollY = searchY + 36 + Spacing.S2;
        int scrollX = contentX + Spacing.S3;
        int scrollW = contentW - Spacing.S6;
        
        scrollContainer.h = contentY + contentH - scrollY - Spacing.S3;
        scrollContainer.render(g, font, scrollX, scrollY, scrollW, mx, my, delta);
    }
    
    private void drawTabs(GuiGraphicsExtractor g) {
        int tabW = (contentW - Spacing.S6) / TABS.length;
        int tabX = contentX + Spacing.S3;
        int tabY = tabBarY;
        
        for (int i = 0; i < TABS.length; i++) {
            boolean selected = i == currentTab;
            boolean hover = mx >= tabX && mx <= tabX + tabW && my >= tabY && my <= tabY + tabBarH;
            
            int bg = selected 
                ? ColorUtil.withAlpha(Theme.ACCENT, 30)
                : hover ? ColorUtil.withAlpha(Theme.SURFACE_VARIANT, 100) : 0;
            
            if (bg != 0) g.fill(tabX, tabY, tabX + tabW, tabY + tabBarH, bg);
            
            if (selected) {
                g.fill(tabX, tabY + tabBarH - 3, tabX + tabW, tabY + tabBarH, Theme.ACCENT);
            }
            
            int textColor = selected ? Theme.ACCENT : hover ? Theme.FOREGROUND : Theme.MUTED_FOREGROUND;
            g.centeredText(font, Component.literal(TABS[i]), tabX + tabW / 2, tabY + (tabBarH - font.lineHeight) / 2, textColor);
            
            tabX += tabW;
        }
    }
    
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int btn = event.buttonInfo().input();
        double mxx = event.x(), myy = event.y();
        
        if (btn == 0) {
            int headerY = Spacing.S4;
            int backW = font.width("← Back") + Spacing.S4;
            if (mxx >= Spacing.S4 && mxx <= Spacing.S4 + backW && myy >= headerY && myy <= headerY + 24) {
                onClose();
                return true;
            }
            
            int tabW = (contentW - Spacing.S6) / TABS.length;
            int tabX = contentX + Spacing.S3;
            for (int i = 0; i < TABS.length; i++) {
                if (mxx >= tabX && mxx <= tabX + tabW && myy >= tabBarY && myy <= tabBarY + tabBarH) {
                    if (currentTab != i) {
                        currentTab = i;
                        searchQuery = "";
                        searchBar.setText("");
                        refreshItems();
                    }
                    return true;
                }
                tabX += tabW;
            }
            
            int btnY = modelY + modelH - 40;
            int btnW = (modelW - Spacing.S6) / 2 - Spacing.S2;
            int resetX = modelX + Spacing.S3;
            int randomX = resetX + btnW + Spacing.S2;
            
            if (mxx >= resetX && mxx <= resetX + btnW && myy >= btnY && myy <= btnY + 28) {
                modelViewer.resetView();
                return true;
            }
            
            if (mxx >= randomX && mxx <= randomX + btnW && myy >= btnY && myy <= btnY + 28) {
                if (currentTab == 0) {
                    List<SkinLibrary.SkinEntry> skins = SkinLibrary.getAllSkins();
                    if (!skins.isEmpty()) {
                        SkinLibrary.SkinEntry random = skins.get((int)(Math.random() * skins.size()));
                        onSkinSelected(random.id);
                    }
                } else if (currentTab == 1) {
                    List<CapeLibrary.CapeEntry> capes = CapeLibrary.getAllCapes();
                    if (!capes.isEmpty()) {
                        CapeLibrary.CapeEntry random = capes.get((int)(Math.random() * capes.size()));
                        onCapeSelected(random.id);
                    }
                }
                return true;
            }
            
            if (modelViewer.mouseClicked((int)mxx, (int)myy, btn)) return true;
            if (scrollContainer.mouseClicked(mxx, myy, btn)) return true;
            if (searchBar.mouseClicked(mxx, myy, btn)) return true;
        }
        
        return super.mouseClicked(event, doubleClick);
    }
    
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        modelViewer.mouseReleased((int)event.x(), (int)event.y(), event.buttonInfo().input());
        return super.mouseReleased(event);
    }
    
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (scrollContainer.mouseDragged(event.x(), event.y())) return true;
        return super.mouseDragged(event, dx, dy);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (modelViewer.mouseScrolled((int)mouseX, (int)mouseY, deltaY)) return true;
        if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= contentY && mouseY <= contentY + contentH) {
            scrollContainer.mouseScrolled(deltaY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }
    
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (searchBar.keyPressed(event.key(), event.scancode(), event.modifiers())) return true;
        return super.keyPressed(event);
    }
    
    @Override
    public void onClose() {
        anim.closeTo(parent);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
