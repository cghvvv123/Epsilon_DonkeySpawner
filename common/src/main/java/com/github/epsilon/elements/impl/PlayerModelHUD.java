package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.util.Mth;

import java.awt.*;

public class PlayerModelHUD extends HudModule {

    public static final PlayerModelHUD INSTANCE = new PlayerModelHUD();

    private final BoolSetting copyYaw = boolSetting("Copy Yaw", true);
    private final BoolSetting copyPitch = boolSetting("Copy Pitch", true);
    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.5, 0.1);
    private final BoolSetting background = boolSetting("Background", false);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 130), background::getValue);

    private PlayerModelHUD() {
        super("Player Model HUD", 0f, 0f, 60f, 82f);
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        float s = scale.getValue().floatValue();
        float width = 60f * s;
        float height = 82f * s;
        setBounds(width, height);
        if (background.getValue()) renderScope().roundRect(this.x, this.y, width, height, 4f, backgroundColor.getValue());
    }

    @Override
    public void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (mc.player == null) return;

        float s = scale.getValue().floatValue();
        boolean inEditor = mc.screen instanceof HudEditorScreen;
        // 编辑器使用原版 GUI 投影，普通 HUD 使用 Epsilon 投影；PIP 状态本身不会继承外层 pose。
        float coordinateScale = inEditor
                ? (float) (LuminRenderSystem.getGuiScale() / mc.gameRenderer.getGameRenderState().windowRenderState.guiScale)
                : 1f;
        int x0 = Math.round(this.x * coordinateScale);
        int y0 = Math.round(this.y * coordinateScale);
        int x1 = Math.round((this.x + 60f * s) * coordinateScale);
        int y1 = Math.round((this.y + 82f * s) * coordinateScale);
        float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
        float yaw = copyYaw.getValue() ? Mth.wrapDegrees(mc.player.yRotO + (mc.player.getYRot() - mc.player.yRotO) * partial) : 0f;
        float pitch = copyPitch.getValue() ? mc.player.getXRot() : 0f;
        float centerX = (x0 + x1) / 2f;
        float centerY = (y0 + y1) / 2f;
        float fakeMouseX = centerX - (float) Math.tan(Math.toRadians(Mth.clamp(yaw, -85f, 85f) / 20f)) * 40f;
        float fakeMouseY = centerY - (float) Math.tan(Math.toRadians(Mth.clamp(pitch, -85f, 85f) / 20f)) * 40f;
        if (!inEditor) graphics.nextStratum();
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x0, y0, x1, y1,
                Math.round(30f * s * coordinateScale), 0.0625f, fakeMouseX, fakeMouseY, mc.player);
    }
}
