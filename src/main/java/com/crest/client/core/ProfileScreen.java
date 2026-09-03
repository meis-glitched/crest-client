package com.crest.client.core;

import com.crest.client.ui.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ProfileScreen extends Screen {
    private final Screen parent;
    private final ScreenAnim anim = new ScreenAnim();
    private TextInput nameInput;
    private final ScrollContainer profileList;
    private int mx, my;

    protected ProfileScreen(Screen parent) {
        super(Component.literal("Profiles"));
        this.parent = parent;
        anim.begin();

        nameInput = new TextInput("", this::onNameChanged);

        profileList = new ScrollContainer()
            .rowHeight(30)
            .children(buildProfileWidgets());
    }

    public static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new ProfileScreen(parent));
    }

    private List<Widget> buildProfileWidgets() {
        List<Widget> list = new ArrayList<>();
        for (String name : Profiles.names()) {
            list.add(new ProfileRow(name, p -> {
                Profiles.apply(p);
                Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal("[Crest] Applied profile '" + p + "'"));
            }, p -> {
                Profiles.delete(p);
                refreshList();
            }));
        }
        return list;
    }

    private void refreshList() {
        profileList.children(buildProfileWidgets());
    }

    private void onNameChanged(String name) {
    }

    @Override
    protected void init() {
        Theme.load();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        this.mx = mx;
        this.my = my;
        Theme.tick(delta);
        anim.update(delta);

        int pX = 40, pY = 40, pW = width - 80, pH = height - 80;

        anim.backdrop(g, width, height);
        int wy = anim.slideY(-12);
        g.pose().pushMatrix();
        g.pose().translate(0, wy);
        Panel.draw(g, pX, pY, pW, pH, Theme.GLASS_BG);
        Panel.drawHollowRect(g, pX, pY, pW, pH, Theme.BORDER_LIGHT);

        g.text(font, Component.literal("Config Profiles"), 60, 56, Theme.FOREGROUND);

        int inX = 60, inY = 84, inW = 260, inH = 28;
        nameInput.render(g, font, inX, inY, inW, mx, my, delta);

        drawButton(g, "Save", inX + inW + 12, inY, 90, inH, true);

        int listX = 60, listY = inY + inH + 12, listW = pX + pW - listX - 40, listH = pY + pH - listY - 40;
        profileList.hoverColor = ColorUtil.withAlpha(Theme.MUTED, 100);
        profileList.render(g, font, listX, listY, listW, mx, my, delta);

        g.text(font, Component.literal("ESC to go back  |  click a profile to Apply, Delete to remove"),
            60, height - 56, Theme.MUTED_FOREGROUND);

        g.pose().popMatrix();
    }

    private void drawButton(GuiGraphicsExtractor g, String label, int x, int y, int w, int h, boolean primary) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        int fill = hover ? ColorUtil.withAlpha(Theme.getAnimatedAccent(), 200)
                        : ColorUtil.withAlpha(Theme.getAnimatedAccent(), 150);
        g.fill(x, y, x + w, y + h, fill);
        Panel.drawHollowRect(g, x, y, w, h, Theme.BORDER_LIGHT);
        g.text(font, Component.literal(label), x + (w - font.width(label)) / 2, y + (h - font.lineHeight) / 2, Theme.FOREGROUND);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mxx = event.x(), myy = event.y();
        int btn = event.buttonInfo().input();
        if (btn != 0) return super.mouseClicked(event, doubleClick);

        int inX = 60, inY = 84, inW = 260, inH = 28;

        if (mxx >= inX && mxx <= inX + inW && myy >= inY && myy <= inY + inH) {
            nameInput.mouseClicked(mxx, myy, 0);
            return true;
        }
        nameInput.blur();

        if (mxx >= inX + inW + 12 && mxx <= inX + inW + 102 && myy >= inY && myy <= inY + inH) {
            if (!nameInput.getText().trim().isEmpty()) {
                Profiles.save(nameInput.getText().trim());
                nameInput = new TextInput("", this::onNameChanged);
                refreshList();
            }
            return true;
        }

        int listX = 60, listY = inY + inH + 12;
        if (profileList.mouseClicked(mxx, myy, btn)) return true;

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (profileList.mouseDragged(event.x(), event.y())) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        profileList.mouseScrolled(deltaY);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (nameInput.charTyped(event.codepoint(), 0)) return true;
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 256) {
            nameInput.blur();
            onClose();
            return true;
        }
        if (nameInput.keyPressed(key, 0, 0)) return true;
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

    private static class ProfileRow implements Widget {
        private final String name;
        private final Consumer<String> onApply;
        private final Consumer<String> onDelete;
        private int lastX, lastY, lastW;

        ProfileRow(String name, Consumer<String> onApply, Consumer<String> onDelete) {
            this.name = name;
            this.onApply = onApply;
            this.onDelete = onDelete;
        }

        @Override
        public int getWidth() {
        return 0;
    }

    public int getHeight() {
            return 28;
        }

        @Override
        public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mx, int my, float delta) {
            lastX = x;
            lastY = y;
            lastW = w;

            boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + 28;
            g.fill(x, y, x + w, y + 28,
                hover ? ColorUtil.withAlpha(Theme.getAnimatedAccent(), 40)
                     : ColorUtil.withAlpha(Theme.SURFACE_VARIANT, 60));
            Panel.drawHollowRect(g, x, y, w, 28, Theme.BORDER_LIGHT);

            g.text(font, Component.literal(name), x + 10, y + 9, Theme.FOREGROUND);
            g.text(font, Component.literal("Apply"), x + w - 120, y + 9, Theme.getAnimatedAccent());
            g.text(font, Component.literal("Delete"), x + w - 50, y + 9, Theme.DESTRUCTIVE);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (mx >= lastX && mx <= lastX + lastW && my >= lastY && my <= lastY + 28) {
                if (mx >= lastX + lastW - 120 && mx <= lastX + lastW - 55) {
                    onApply.accept(name);
                    return true;
                }
                if (mx >= lastX + lastW - 50 && mx <= lastX + lastW) {
                    onDelete.accept(name);
                    return true;
                }
                onApply.accept(name);
                return true;
            }
            return false;
        }
    }
}
