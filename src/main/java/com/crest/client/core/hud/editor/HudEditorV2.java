package com.crest.client.core.hud.editor;

import com.crest.client.core.CrestModules;
import com.crest.client.core.HudModule;
import com.crest.client.core.ScreenAnim;
import com.crest.client.core.hud.effects.GridRenderer;
import com.crest.client.core.hud.effects.SelectionGlow;
import com.crest.client.core.hud.profile.HudProfile;
import com.crest.client.core.hud.profile.HudProfileManager;
import com.crest.client.ui.*;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HudEditorV2 extends Screen {
    
    private final ScreenAnim screenAnim = new ScreenAnim();
    private final GridRenderer gridRenderer = new GridRenderer();
    private final SelectionGlow selectionGlow = new SelectionGlow();
    private final HudEditorHistory history = new HudEditorHistory();
    private final HudModuleSidebar sidebar = new HudModuleSidebar();
    private final HudPropertiesPanel propertiesPanel = new HudPropertiesPanel();
    private final HudEditorToolbar toolbar = new HudEditorToolbar(history);
    private final HudContextMenu contextMenu = new HudContextMenu();
    
    private HudModule selectedModule;
    private HudProfile.ModuleSettings selectedSettings;
    private boolean dragging;
    private boolean resizing;
    private int dragOffsetX, dragOffsetY;
    private int startDragX, startDragY;
    private final List<HudModule> selectedModules = new ArrayList<>();
    
    private int mouseX, mouseY;
    private boolean hasChanges = false;
    
    private static final int SNAP_THRESHOLD = 8;
    private static final int MIN_MODULE_SIZE = 24;
    
    public HudEditorV2() {
        super(Component.literal("HUD Editor"));
    }
    
    public static void open() {
        Minecraft.getInstance().setScreen(new HudEditorV2());
    }
    
    @Override
    protected void init() {
        Theme.load();
        screenAnim.begin();
        HudProfileManager.getInstance().load();
    }
    
    @Override
    public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        super.render(g, deltaTracker);
        float delta = deltaTracker.getGameTimeDeltaTicks();
        
        renderBlurredBackground(g, delta);
        
        gridRenderer.render(g, width, height, delta);
        
        renderModules(g, delta);
        
        if (selectedModule != null && toolbar.isSnapEnabled() && dragging) {
            int[][] snapLines = HudAlignmentTools.findSnapLines(
                selectedModule, 
                CrestModules.getHudModules(), 
                SNAP_THRESHOLD
            );
            gridRenderer.renderSnapLines(g, 
                selectedModule.getX(), 
                selectedModule.getY(),
                selectedModule.getRenderWidth(),
                selectedModule.getRenderHeight(),
                snapLines[0], snapLines[1], delta
            );
        }
        
        toolbar.render(g, width, mouseX, mouseY, delta);
        sidebar.render(g, width, height, mouseX, mouseY, delta);
        propertiesPanel.render(g, width, height, mouseX, mouseY, delta);
        contextMenu.render(g, mouseX, mouseY, delta);
        
        renderHelpText(g);
    }
    
    private void renderBlurredBackground(GuiGraphicsExtractor g, float delta) {
        int bgColor = ColorUtil.withAlpha(0x000000, 180);
        g.fill(0, 0, width, height, bgColor);
        
        float bokehProgress = Math.min(1f, screenAnim.getAge() / 0.3f);
        if (bokehProgress > 0.01f) {
            int bokehAlpha = (int)(30 * bokehProgress);
            for (int i = 0; i < 20; i++) {
                int x = (int)(Math.sin(i * 1.5) * width * 0.3 + width / 2);
                int y = (int)(Math.cos(i * 1.2) * height * 0.3 + height / 2);
                int size = 40 + i * 5;
                int bokehColor = ColorUtil.withAlpha(Theme.ACCENT, bokehAlpha);
                Panel.draw(g, x - size/2, y - size/2, size, size, bokehColor);
            }
        }
    }
    
    private void renderModules(GuiGraphicsExtractor g, float delta) {
        List<HudModule> modules = CrestModules.getHudModules();
        
        for (HudModule module : modules) {
            if (!module.isEnabled()) continue;
            
            
            
            boolean isSelected = module == selectedModule;
            if (isSelected) {
                selectionGlow.render(g, module, 
                    module.getX(), module.getY(),
                    module.getRenderWidth(), module.getRenderHeight(),
                    delta, true);
            }
            
            try {
                module.render(g, DeltaTracker.ZERO);
            } catch (Exception e) {
            }
            
            if (isSelected) {
                selectionGlow.renderResizeHandles(g,
                    module.getX(), module.getY(),
                    module.getRenderWidth(), module.getRenderHeight(),
                    delta, true);
            }
            
            
        }
    }
    
    private void renderHelpText(GuiGraphicsExtractor g) {
        String[] tips = {
            "💡 Drag modules to reposition",
            "Right-click for more options",
            "Shift+Click to select multiple",
            "Ctrl+Z/Y to undo/redo"
        };
        
        int tipIndex = (int)((System.currentTimeMillis() / 4000) % tips.length);
        String tip = tips[tipIndex];
        
        int tipWidth = net.minecraft.client.Minecraft.getInstance().font.width(tip);
        int tipX = (width - tipWidth) / 2;
        int tipY = height - 20;
        
        Panel.draw(g, tipX - 8, tipY - 4, tipWidth + 16, 20, ColorUtil.withAlpha(Theme.BACKGROUND));
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(tip), tipX, tipY, Theme.MUTED_FOREGROUND);
    }
    
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        mouseX = (int)event.x();
        mouseY = (int)event.y();
        int button = event.buttonInfo().input();
        
        if (button == 0) {
            String toolbarAction = toolbar.handleClick(mouseX, mouseY);
            if (toolbarAction != null) {
                handleToolbarAction(toolbarAction);
                return true;
            }
            
            if (sidebar.handleClick(mouseX, mouseY, width, height)) {
                return true;
            }
            
            if (propertiesPanel.handleClick(mouseX, mouseY, width, height)) {
                return true;
            }
            
            HudModule clickedModule = findModuleAt(mouseX, mouseY);
            if (clickedModule != null) {
                selectModule(clickedModule);
                startDrag(clickedModule, mouseX, mouseY);
                return true;
            }
            
            deselectModule();
            return true;
        }
        
        if (button == 1) {
            HudModule clickedModule = findModuleAt(mouseX, mouseY);
            if (clickedModule != null) {
                selectModule(clickedModule);
                contextMenu.show(mouseX, mouseY);
                return true;
            }
        }
        
        return super.mouseClicked(event, doubleClick);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        this.mouseX = (int)mouseX;
        this.mouseY = (int)mouseY;
        
        if (propertiesPanel.handleDrag(mouseX, mouseY, width, height)) {
            hasChanges = true;
            return true;
        }
        
        if (dragging && selectedModule != null) {
            int newX = (int)mouseX - dragOffsetX;
            int newY = (int)mouseY - dragOffsetY;
            
            if (toolbar.isGridEnabled()) {
                newX = gridRenderer.snapToGrid(newX);
                newY = gridRenderer.snapToGrid(newY);
            }
            
            if (toolbar.isSnapEnabled()) {
                int oldX = selectedModule.getX();
                int oldY = selectedModule.getY();
                selectedModule.setX(newX); selectedModule.setY(newY);
                
                int[][] snapLines = HudAlignmentTools.findSnapLines(
                    selectedModule,
                    CrestModules.getHudModules(),
                    SNAP_THRESHOLD
                );
                
                if (snapLines[0] != null || snapLines[1] != null) {
                    HudAlignmentTools.snapToPosition(selectedModule, snapLines[0], snapLines[1]);
                } else {
                    selectedModule.setX(newX); selectedModule.setY(newY);
                }
            } else {
                selectedModule.setX(newX); selectedModule.setY(newY);
            }
            
            hasChanges = true;
            return true;
        }
        
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.mouseX = (int)mouseX;
        this.mouseY = (int)mouseY;
        
        if (button == 0) {
            String contextAction = contextMenu.handleClick((int)mouseX, (int)mouseY);
            if (contextAction != null) {
                handleContextMenuAction(contextAction);
                return true;
            }
            
            if (dragging && selectedModule != null) {
                history.recordMove(selectedModule, startDragX, startDragY, 
                    selectedModule.getX(), selectedModule.getY());
                dragging = false;
            }
            
            propertiesPanel.handleMouseUp();
        }
        
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (sidebar.handleScroll(deltaY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }
    
    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        
        if (hasControlDown()) {
            if (key == GLFW.GLFW_KEY_Z) {
                history.undo();
                return true;
            }
            if (key == GLFW.GLFW_KEY_Y) {
                history.redo();
                return true;
            }
            if (key == GLFW.GLFW_KEY_D && selectedModule != null) {
                duplicateModule(selectedModule);
                return true;
            }
            if (key == GLFW.GLFW_KEY_S) {
                saveAndClose();
                return true;
            }
        }
        
        if (key == GLFW.GLFW_KEY_DELETE && selectedModule != null) {
            CrestModules.setEnabled(selectedModule.getId(), true);
            deselectModule();
            hasChanges = true;
            return true;
        }
        
        if (selectedModule != null) {
            int moveAmount = hasShiftDown() ? 10 : 1;
            boolean moved = false;
            
            switch (key) {
                case GLFW.GLFW_KEY_LEFT -> {
                    selectedModule.setX(selectedModule.getX() - moveAmount); selectedModule.setY(selectedModule.getY());
                    moved = true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    selectedModule.setX(selectedModule.getX() + moveAmount); selectedModule.setY(selectedModule.getY());
                    moved = true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    selectedModule.setX(selectedModule.getX()); selectedModule.setY(selectedModule.getY() - moveAmount);
                    moved = true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    selectedModule.setX(selectedModule.getX()); selectedModule.setY(selectedModule.getY() + moveAmount);
                    moved = true;
                }
            }
            
            if (moved) {
                hasChanges = true;
                return true;
            }
        }
        
        if (sidebar.handleKeyPress(key)) {
            return true;
        }
        
        return super.keyPressed(event);
    }
    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (sidebar.handleCharTyped(chr)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }
    
    private void handleToolbarAction(String action) {
        switch (action) {
            case "save" -> saveAndClose();
            case "close" -> close();
            case "profile" -> {
            }
            default -> toolbar.handleAction(action);
        }
        
        if (action.equals("grid")) {
            gridRenderer.setShowGrid(toolbar.isGridEnabled());
        }
    }
    
    private void handleContextMenuAction(String action) {
        if (selectedModule == null) return;
        
        switch (action) {
            case "duplicate" -> duplicateModule(selectedModule);
            case "delete" -> {
                CrestModules.setEnabled(selectedModule.getId(), true);
                deselectModule();
                hasChanges = true;
            }
            case "front" -> {
            }
            case "back" -> {
            }
            case "align_left" -> {
                List<HudModule> modules = new ArrayList<>();
                modules.add(selectedModule);
                HudAlignmentTools.alignLeft(modules, width);
                hasChanges = true;
            }
            case "align_center" -> {
                List<HudModule> modules = new ArrayList<>();
                modules.add(selectedModule);
                HudAlignmentTools.alignCenterHorizontal(modules, width);
                hasChanges = true;
            }
            case "align_right" -> {
                List<HudModule> modules = new ArrayList<>();
                modules.add(selectedModule);
                HudAlignmentTools.alignRight(modules, width);
                hasChanges = true;
            }
            case "align_top" -> {
                List<HudModule> modules = new ArrayList<>();
                modules.add(selectedModule);
                HudAlignmentTools.alignTop(modules, height);
                hasChanges = true;
            }
            case "align_middle" -> {
                List<HudModule> modules = new ArrayList<>();
                modules.add(selectedModule);
                HudAlignmentTools.alignCenterVertical(modules, height);
                hasChanges = true;
            }
            case "align_bottom" -> {
                List<HudModule> modules = new ArrayList<>();
                modules.add(selectedModule);
                HudAlignmentTools.alignBottom(modules, height);
                hasChanges = true;
            }
            case "reset" -> {
                selectedModule.setX(0); selectedModule.setY(0);
                hasChanges = true;
            }
        }
    }
    
    private HudModule findModuleAt(int x, int y) {
        List<HudModule> modules = CrestModules.getHudModules();
        for (int i = modules.size() - 1; i >= 0; i--) {
            HudModule module = modules.get(i);
            if (!module.isEnabled()) continue;
            
            int mx = module.getX();
            int my = module.getY();
            int mw = module.getRenderWidth();
            int mh = module.getRenderHeight();
            
            if (x >= mx && x <= mx + mw && y >= my && y <= my + mh) {
                return module;
            }
        }
        return null;
    }
    
    private void selectModule(HudModule module) {
        selectedModule = module;
        HudProfile profile = HudProfileManager.getInstance().getCurrentProfile();
        if (profile != null) {
            selectedSettings = profile.getModuleSettings(module.getId());
            propertiesPanel.setSelectedModule(module, selectedSettings);
        }
    }
    
    private void deselectModule() {
        selectedModule = null;
        selectedSettings = null;
        propertiesPanel.setSelectedModule(null, null);
    }
    
    private void startDrag(HudModule module, int mouseX, int mouseY) {
        dragging = true;
        dragOffsetX = mouseX - module.getX();
        dragOffsetY = mouseY - module.getY();
        startDragX = module.getX();
        startDragY = module.getY();
    }
    
    private void duplicateModule(HudModule module) {
    }
    
    private void saveAndClose() {
        HudProfileManager.getInstance().save();
        close();
    }
    
    @Override
    public void onClose() {
        if (hasChanges) {
            HudProfileManager.getInstance().save();
        }
        super.onClose();
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
