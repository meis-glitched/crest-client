package com.crest.client.core.hud.editor;

import com.crest.client.core.CrestModules;
import com.crest.client.core.HudModule;
import com.crest.client.core.HudSettings;
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
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HudEditorV2 extends Screen {

    private final ScreenAnim screenAnim = new ScreenAnim();
    private final GridRenderer gridRenderer = new GridRenderer();
    private final SelectionGlow selectionGlow = new SelectionGlow();
    private final HudEditorHistory history = new HudEditorHistory();
    private final HudPropertiesPanel propertiesPanel = new HudPropertiesPanel();
    private final HudContextMenu contextMenu = new HudContextMenu();

    private HudModule selectedModule;
    private HudProfile.ModuleSettings selectedSettings;
    private boolean dragging;
    private boolean resizing;
    private boolean previewMode;
    private String resizeMode;
    private int dragOffsetX, dragOffsetY;
    private int startDragX, startDragY;
    private int resizeStartW, resizeStartH;
    private final List<HudModule> selectedModules = new ArrayList<>();
    private final Map<String, int[]> groupStartPositions = new HashMap<>();

    private int mouseX, mouseY;
    private boolean hasChanges = false;

    private static final int SNAP_THRESHOLD = 8;
    private static final int MIN_MODULE_SIZE = 24;
    private static final int HANDLE_SIZE = 10;

    public HudEditorV2() {
        super(Component.literal("HUD Editor"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new HudEditorV2());
    }

    protected void init() {
        Theme.load();
        screenAnim.begin();
        HudProfileManager.getInstance().load();
        gridRenderer.setShowGrid(true);
        propertiesPanel.setPreviewToggleHandler(() -> {
            previewMode = !previewMode;
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        this.mouseX = mx;
        this.mouseY = my;
        Theme.tick(delta);
        screenAnim.update(delta);

        renderBlurredBackground(g, delta);

        if (!previewMode) {
            gridRenderer.render(g, width, height, delta);
        }

        renderModules(g, delta);

        if (!previewMode && selectedModule != null && dragging) {
            int[][] snapLines = HudAlignmentTools.findSnapLines(
                selectedModule,
                CrestModules.getHudModules(),
                SNAP_THRESHOLD
            );
            int rx = rx(selectedModule);
            gridRenderer.renderSnapLines(g,
                rx,
                selectedModule.getY(),
                selectedModule.getRenderWidth(),
                selectedModule.getRenderHeight(),
                snapLines[0], snapLines[1], width, height, delta
            );
        }

        propertiesPanel.setPreviewActive(previewMode);
        propertiesPanel.render(g, width, height, mouseX, mouseY, delta);
        contextMenu.render(g, mouseX, mouseY, delta);

        if (!previewMode) {
            renderHelpText(g);
        }
    }

    private void renderBlurredBackground(GuiGraphicsExtractor g, float delta) {
        int bgColor = ColorUtil.withAlpha(0x000000, 180);
        g.fill(0, 0, width, height, bgColor);

        float bokehProgress = 1f;
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

    /** Anchor-aware screen x: modules with a negative x anchor to the right edge. */
    private int rx(HudModule module) {
        return module.getX() < 0 ? width - module.getRenderWidth() : module.getX();
    }

    /** HUD modules ordered by z-order (highest order drawn last = on top). */
    private List<HudModule> getHudModulesZSorted() {
        List<HudModule> list = new ArrayList<>(CrestModules.getHudModules());
        list.sort(java.util.Comparator.comparingInt(m -> HudSettings.getOrder(m.getId())));
        return list;
    }

    private void renderModules(GuiGraphicsExtractor g, float delta) {
        List<HudModule> modules = getHudModulesZSorted();

        for (HudModule module : modules) {
            int rx = rx(module);
            int ry = module.getY();
            int rw = module.getRenderWidth();
            int rh = module.getRenderHeight();
            if (rw <= 0 || rh <= 0) continue;

            boolean enabled = module.isEnabled();
            boolean isSelected = selectedModules.contains(module);

            if (isSelected && !previewMode) {
                selectionGlow.render(g, module, rx, ry, rw, rh, delta, true);
            }

            HudProfile profile = HudProfileManager.getInstance().getCurrentProfile();
            HudProfile.ModuleSettings settings = profile != null ? profile.getModuleSettings(module.getId()) : null;
            if (settings != null && module.supportsChrome()) {
                HudModule.renderChrome(g, rx, ry, rw, rh, settings);
            }

            try {
                module.render(g, minecraft, deltaTracker(delta));
            } catch (Exception ignored) {
            }

            if (!enabled) {
                g.fill(rx, ry, rx + rw, ry + rh, ColorUtil.withAlpha(0x000000, 100));
            }

            if (!previewMode) {
                int outlineColor = isSelected ? ColorUtil.withAlpha(Theme.getAnimatedAccent(), 230)
                        : enabled ? ColorUtil.withAlpha(Theme.BORDER_LIGHT, 170)
                        : ColorUtil.withAlpha(Theme.BORDER, 120);
                Panel.drawHollowRect(g, rx - 1, ry - 1, rw + 2, rh + 2, outlineColor);

                renderModuleOverlay(g, module, rx, ry, rw, enabled, isSelected);
                if (isSelected) {
                    selectionGlow.renderResizeHandles(g, rx, ry, rw, rh, delta, true);
                }
            }
        }
    }

    /** Small floating header on each module: name + eye toggle. */
    private void renderModuleOverlay(GuiGraphicsExtractor g, HudModule module, int rx, int ry, int rw, boolean enabled, boolean isSelected) {
        int[] rect = eyeRect(module);
        int ex = rect[0];
        int oy = rect[1];

        int nameWidth = this.font.width(module.getName());
        int headerW = Math.min(rw, Math.max(nameWidth + 34, 46));
        int headerX = rx;
        int headerY = oy;

        Panel.draw(g, headerX, headerY, headerW, 14, ColorUtil.withAlpha(enabled ? Theme.BACKGROUND : Theme.CARD, 225));
        Panel.drawHollowRect(g, headerX, headerY, headerW, 14,
                ColorUtil.withAlpha(isSelected ? Theme.getAnimatedAccent() : Theme.BORDER, 210));

        int nameColor = enabled ? Theme.FOREGROUND : ColorUtil.withAlpha(Theme.MUTED_FOREGROUND, 180);
        g.text(this.font, Component.literal(module.getName()), headerX + 4, headerY + 3, nameColor);

        String eye = enabled ? "\uD83D\uDC41" : "\uD83D\uDEAB";
        g.text(this.font, Component.literal(eye), ex + 2, headerY + 3,
                enabled ? Theme.FOREGROUND : ColorUtil.withAlpha(0xFF0000, 230));
    }

    /** Top-right eye toggle rect on a module's overlay bar. */
    private int[] eyeRect(HudModule module) {
        int rx = rx(module);
        int rw = module.getRenderWidth();
        int nameWidth = this.font.width(module.getName());
        int headerW = Math.min(rw, Math.max(nameWidth + 34, 46));
        int oy = module.getRenderHeight() < 24 ? module.getY() : Math.max(0, module.getY() - 14);
        return new int[]{Math.max(4, rx + headerW - 20), oy, 20, 14};
    }

    /** Finds a module whose eye-toggle button contains the point (topmost first). */
    private HudModule findModuleAtEye(int x, int y) {
        List<HudModule> modules = getHudModulesZSorted();
        for (int i = modules.size() - 1; i >= 0; i--) {
            HudModule module = modules.get(i);
            int[] rect = eyeRect(module);
            if (x >= rect[0] && x <= rect[0] + rect[2] && y >= rect[1] && y <= rect[1] + rect[3]) {
                return module;
            }
        }
        return null;
    }

    private void toggleEye(HudModule module) {
        boolean next = !module.isEnabled();
        CrestModules.setEnabledSilent(module.getId(), next);
        hasChanges = true;
        refreshProperties();
        if (!selectedModules.contains(module)) {
            selectModule(module);
        }
    }

    private void renderHelpText(GuiGraphicsExtractor g) {
        String[] tips = {
            "\uD83D\uDCA1 Drag modules to reposition",
            "Right-click for more options",
            "Shift+Click to select multiple",
            "Corner handle = size both · bottom = height · right = width",
            "Ctrl+Z/Y to undo/redo"
        };

        int tipIndex = (int)((System.currentTimeMillis() / 4000) % tips.length);
        String tip = tips[tipIndex];
        int tipWidth = net.minecraft.client.Minecraft.getInstance().font.width(tip);
        int tipX = (width - tipWidth) / 2;
        int tipY = height - 20;

        Panel.draw(g, tipX - 8, tipY - 4, tipWidth + 16, 20, ColorUtil.withAlpha(Theme.BACKGROUND, 240));
        g.text(net.minecraft.client.Minecraft.getInstance().font, Component.literal(tip), tipX, tipY, Theme.MUTED_FOREGROUND);
    }

    /** Returns resize mode for the 3 handles, or null. */
    private String resizeModeAt(HudModule module, double mx, double my) {
        int rx = rx(module);
        int ry = module.getY();
        int rw = module.getRenderWidth();
        int rh = module.getRenderHeight();

        // Bottom-right corner -> diagonal resize (both axes)
        // Bottom-center -> height only
        // Right-center -> width only
        int[][] handles = {
            {rx + rw - HANDLE_SIZE/2, ry + rh - HANDLE_SIZE/2},
            {rx + rw/2 - HANDLE_SIZE/2, ry + rh - HANDLE_SIZE/2},
            {rx + rw - HANDLE_SIZE/2, ry + rh/2 - HANDLE_SIZE/2}
        };
        String[] modes = {"corner", "height", "width"};

        for (int i = 0; i < handles.length; i++) {
            if (mx >= handles[i][0] && mx <= handles[i][0] + HANDLE_SIZE &&
                my >= handles[i][1] && my <= handles[i][1] + HANDLE_SIZE) {
                return modes[i];
            }
        }
        return null;
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        mouseX = (int)event.x();
        mouseY = (int)event.y();
        int button = event.buttonInfo().input();

        if (button == 0) {
            contextMenu.hide();

            if (propertiesPanel.handleClick(mouseX, mouseY, width, height)) {
                return true;
            }

            HudModule eyeModule = findModuleAtEye(mouseX, mouseY);
            if (eyeModule != null) {
                toggleEye(eyeModule);
                return true;
            }

            HudModule clickedModule = findModuleAt(mouseX, mouseY);

            if (clickedModule != null) {
                String mode = resizeModeAt(clickedModule, mouseX, mouseY);
                if (mode != null) {
                    resizing = true;
                    resizeMode = mode;
                    resizeStartW = clickedModule.getRenderWidth();
                    resizeStartH = clickedModule.getRenderHeight();
                    selectModule(clickedModule);
                    return true;
                }
            }

            boolean shiftHeld = isShiftDown();
            if (clickedModule != null && shiftHeld) {
                toggleSelection(clickedModule);
                return true;
            }

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

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        this.mouseX = (int)event.x();
        this.mouseY = (int)event.y();

        if (propertiesPanel.handleDrag(event.x(), event.y(), width, height)) {
            hasChanges = true;
            return true;
        }

        if (resizing && selectedModule != null) {
            int rx = rx(selectedModule);
            int ry = selectedModule.getY();
            int newW = selectedModule.getRenderWidth();
            int newH = selectedModule.getRenderHeight();

            if ("width".equals(resizeMode)) {
                newW = (int)(Math.max(MIN_MODULE_SIZE, Math.min(width - rx, mouseX - rx)));
            } else if ("height".equals(resizeMode)) {
                newH = (int)(Math.max(MIN_MODULE_SIZE, Math.min(height - ry, mouseY - ry)));
            } else {
                newW = (int)(Math.max(MIN_MODULE_SIZE, Math.min(width - rx, mouseX - rx)));
                newH = (int)(Math.max(MIN_MODULE_SIZE, Math.min(height - ry, mouseY - ry)));
            }

            newW = gridRenderer.snapToGrid(newW);
            newH = gridRenderer.snapToGrid(newH);
            selectedModule.setSize(newW, newH);
            HudSettings.setSize(selectedModule.getId(), newW, newH);
            syncGroupSizes(selectedModule, resizeStartW, resizeStartH, newW, newH);
            hasChanges = true;
            return true;
        }

        if (dragging && selectedModule != null) {
            int newX = (int)mouseX - dragOffsetX;
            int newY = (int)mouseY - dragOffsetY;

            newX = gridRenderer.snapToGrid(newX);
            newY = gridRenderer.snapToGrid(newY);

            selectedModule.setX(newX); selectedModule.setY(newY);
            int[][] snapLines = HudAlignmentTools.findSnapLines(
                    selectedModule,
                    CrestModules.getHudModules(),
                    SNAP_THRESHOLD
                );
                if (snapLines[0] != null || snapLines[1] != null) {
                    HudAlignmentTools.snapToPosition(selectedModule, snapLines[0], snapLines[1]);
                    newX = selectedModule.getX();
                    newY = selectedModule.getY();
                }

            int dxGroup = newX - startDragX;
            int dyGroup = newY - startDragY;
            for (HudModule module : selectedModules) {
                int[] start = groupStartPositions.get(module.getId());
                if (start != null) {
                    module.setX(start[0] + dxGroup);
                    module.setY(start[1] + dyGroup);
                    HudSettings.setPosition(module.getId(), module.getX(), module.getY());
                }
            }
            selectedModule.setX(newX);
            selectedModule.setY(newY);
            HudSettings.setPosition(selectedModule.getId(), newX, newY);

            hasChanges = true;
            return true;
        }

        return super.mouseDragged(event, dx, dy);
    }

    private void syncGroupSizes(HudModule primary, int oldW, int oldH, int newW, int newH) {
        if (selectedModules.size() <= 1 || oldW <= 0 || oldH <= 0) return;
        float sx = (float) newW / oldW;
        float sy = (float) newH / oldH;
        for (HudModule module : selectedModules) {
            if (module == primary) continue;
            int w = Math.max(MIN_MODULE_SIZE, Math.round(module.getRenderWidth() * sx));
            int h = Math.max(MIN_MODULE_SIZE, Math.round(module.getRenderHeight() * sy));
            module.setSize(w, h);
            HudSettings.setSize(module.getId(), w, h);
        }
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        this.mouseX = (int)event.x();
        this.mouseY = (int)event.y();

        if (event.buttonInfo().input() == 0) {
            String contextAction = contextMenu.handleClick((int)mouseX, (int)mouseY);
            if (contextAction != null) {
                handleContextMenuAction(contextAction);
                return true;
            }

            if (resizing && selectedModule != null) {
                history.recordResize(selectedModule, resizeStartW, resizeStartH,
                    selectedModule.getRenderWidth(), selectedModule.getRenderHeight());
                resizing = false;
                resizeMode = null;
            }

            if (dragging && selectedModule != null) {
                history.recordMove(selectedModule, startDragX, startDragY,
                    selectedModule.getX(), selectedModule.getY());
                dragging = false;
            }

            propertiesPanel.handleMouseUp();
        }

        return super.mouseReleased(event);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (propertiesPanel.handleScroll(deltaY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }

        if (isCtrlDown()) {
            if (key == GLFW.GLFW_KEY_Z) {
                history.undo();
                return true;
            }
            if (key == GLFW.GLFW_KEY_Y) {
                history.redo();
                return true;
            }
            if (key == GLFW.GLFW_KEY_S) {
                saveAndClose();
                return true;
            }
        }

        if (key == GLFW.GLFW_KEY_DELETE && !selectedModules.isEmpty()) {
            for (HudModule module : selectedModules) {
                CrestModules.setEnabled(module.getId(), false);
            }
            deselectModule();
            hasChanges = true;
            return true;
        }

        if (!selectedModules.isEmpty()) {
            int moveAmount = isShiftDown() ? 10 : 1;
            boolean moved = false;

            for (HudModule module : selectedModules) {
                switch (key) {
                    case GLFW.GLFW_KEY_LEFT -> module.setX(module.getX() - moveAmount);
                    case GLFW.GLFW_KEY_RIGHT -> module.setX(module.getX() + moveAmount);
                    case GLFW.GLFW_KEY_UP -> module.setY(module.getY() - moveAmount);
                    case GLFW.GLFW_KEY_DOWN -> module.setY(module.getY() + moveAmount);
                    default -> {
                    }
                }
                HudSettings.setPosition(module.getId(), module.getX(), module.getY());
            }
            moved = switch (key) {
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT,
                     GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> true;
                default -> false;
            };

            if (moved) {
                hasChanges = true;
                return true;
            }
        }

        return super.keyPressed(event);
    }

    private boolean isShiftDown() {
        long win = GLFW.glfwGetCurrentContext();
        return GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
               GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private boolean isCtrlDown() {
        long win = GLFW.glfwGetCurrentContext();
        return GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
               GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    public boolean charTyped(CharacterEvent event) {
        return super.charTyped(event);
    }

    private void handleContextMenuAction(String action) {
        if (selectedModule == null) return;

        switch (action) {
            case "duplicate" -> CrestModules.notify("Duplication is not supported for this module");
            case "delete" -> {
                for (HudModule module : selectedModules) {
                    CrestModules.setEnabled(module.getId(), false);
                }
                deselectModule();
                hasChanges = true;
            }
            case "front" -> {
                for (int i = 0; i < selectedModules.size(); i++) {
                    HudSettings.bringToFront(selectedModules.get(i).getId());
                }
                hasChanges = true;
            }
            case "back" -> {
                for (HudModule module : selectedModules) {
                    sendToBack(module.getId());
                }
                hasChanges = true;
            }
            case "align_left" -> {
                HudAlignmentTools.alignLeft(selectedModules, width);
                persistGroupPosition();
                hasChanges = true;
            }
            case "align_center" -> {
                HudAlignmentTools.alignCenterHorizontal(selectedModules, width);
                persistGroupPosition();
                hasChanges = true;
            }
            case "align_right" -> {
                HudAlignmentTools.alignRight(selectedModules, width);
                persistGroupPosition();
                hasChanges = true;
            }
            case "align_top" -> {
                HudAlignmentTools.alignTop(selectedModules, height);
                persistGroupPosition();
                hasChanges = true;
            }
            case "align_middle" -> {
                HudAlignmentTools.alignCenterVertical(selectedModules, height);
                persistGroupPosition();
                hasChanges = true;
            }
            case "align_bottom" -> {
                HudAlignmentTools.alignBottom(selectedModules, height);
                persistGroupPosition();
                hasChanges = true;
            }
            case "reset" -> {
                for (HudModule module : selectedModules) {
                    HudSettings.setPosition(module.getId(), 10, 10);
                    HudSettings.setSize(module.getId(), null, null);
                    module.setX(10); module.setY(10); module.setSize(null, null);
                }
                hasChanges = true;
            }
        }
    }

    private void persistGroupPosition() {
        for (HudModule module : selectedModules) {
            HudSettings.setPosition(module.getId(), module.getX(), module.getY());
        }
    }

    private void sendToBack(String moduleId) {
        int min = Integer.MAX_VALUE;
        for (int v : HudSettings.getInstance().order.values()) min = Math.min(min, v);
        HudSettings.getInstance().order.put(moduleId, min - 1);
    }

    private HudModule findModuleAt(int x, int y) {
        List<HudModule> modules = getHudModulesZSorted();
        for (int i = modules.size() - 1; i >= 0; i--) {
            HudModule module = modules.get(i);

            int mx = rx(module);
            int my = module.getY();
            int mw = module.getRenderWidth();
            int mh = module.getRenderHeight();

            if (x >= mx && x <= mx + mw && y >= my && y <= my + mh) {
                return module;
            }
        }
        return null;
    }

    private void toggleSelection(HudModule module) {
        if (selectedModules.contains(module)) {
            selectedModules.remove(module);
            if (selectedModule == module) {
                selectedModule = selectedModules.isEmpty() ? null : selectedModules.get(0);
            }
        } else {
            selectedModules.add(module);
            selectedModule = module;
        }
        refreshProperties();
    }

    private void refreshProperties() {
        if (selectedModule == null) {
            selectedSettings = null;
            propertiesPanel.setSelectedModule(null, null);
            return;
        }
        HudProfile profile = HudProfileManager.getInstance().getCurrentProfile();
        if (profile != null) {
            selectedSettings = profile.getModuleSettings(selectedModule.getId());
            propertiesPanel.setSelectedModule(selectedModule, selectedSettings, new ArrayList<>(selectedModules));
        }
    }

    private void selectModule(HudModule module) {
        selectedModule = module;
        if (!selectedModules.contains(module)) {
            selectedModules.clear();
            selectedModules.add(module);
        }
        HudProfile profile = HudProfileManager.getInstance().getCurrentProfile();
        if (profile != null) {
            selectedSettings = profile.getModuleSettings(module.getId());
            propertiesPanel.setSelectedModule(module, selectedSettings, new ArrayList<>(selectedModules));
        }
    }

    private void deselectModule() {
        selectedModule = null;
        selectedSettings = null;
        selectedModules.clear();
        propertiesPanel.setSelectedModule(null, null);
    }

    private void startDrag(HudModule module, int mouseX, int mouseY) {
        dragging = true;
        dragOffsetX = mouseX - rx(module);
        dragOffsetY = mouseY - module.getY();
        startDragX = module.getX();
        startDragY = module.getY();
        groupStartPositions.clear();
        for (HudModule m : selectedModules) {
            groupStartPositions.put(m.getId(), new int[]{m.getX(), m.getY()});
        }
    }

    private static DeltaTracker deltaTracker(float delta) {
        float d = delta;
        return new DeltaTracker() {
            @Override public float getGameTimeDeltaTicks() { return d; }
            @Override public float getGameTimeDeltaPartialTick(boolean ignore) { return d; }
            @Override public float getRealtimeDeltaTicks() { return d; }
        };
    }

    private void saveAndClose() {
        HudSettings.save();
        HudProfileManager.getInstance().save();
        onClose();
    }

    public void onClose() {
        if (hasChanges) {
            HudSettings.save();
            HudProfileManager.getInstance().save();
        }
        super.onClose();
    }

    public boolean isPauseScreen() {
        return false;
    }
}