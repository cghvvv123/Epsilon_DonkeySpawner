package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.ContainerItemUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.awt.*;

public class ContainerHUD extends HudModule {

    public static final ContainerHUD INSTANCE = new ContainerHUD();

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final DoubleSetting cornerRadius = doubleSetting("Corner Radius", 3.0, 0.0, 14.0, 0.5);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 135));
    private final ColorSetting slotColor = colorSetting("Slot Color", new Color(0, 0, 0, 70));
    private final BoolSetting drawShadow = boolSetting("Drop Shadow", true);
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", 2.2, 0.1, 32.0, 0.5, drawShadow::getValue);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", new Color(0, 0, 0, 70), drawShadow::getValue);
    private final BoolSetting backgroundBlur = boolSetting("Background Blur", true);
    private final IntSetting blurStrength = intSetting("Blur Strength", 5, 1, 16, 1);
    private final BoolSetting showCount = boolSetting("Show Count", true);

    private static final float SLOT_SIZE = 17.0f;
    private static final float SLOT_GAP = 1.5f;
    private static final float PADDING = 4.5f;
    private static final int COLS = 9;
    private static final int MAX_ROWS = 11;//mc原版理论所需要的行数极限
    private static final int MAX_SLOTS = COLS * MAX_ROWS;
    private final ItemStack[] containerItems = new ItemStack[MAX_SLOTS];

    private ContainerHUD() {
        super("Container HUD", 0f, 0f, 180f, 80f);
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        if (nullCheck()) {
            setBounds(180f, 80f);
            return;
        }
        float s = scale.getValue().floatValue();
        float slotSize = SLOT_SIZE * s;
        float gap = SLOT_GAP * s;
        float padding = PADDING * s;
        float radius = cornerRadius.getValue().floatValue() * s;

        ItemStack heldContainer = ContainerItemUtils.findHeldContainer();
        if (heldContainer.isEmpty() && !(mc.screen instanceof HudEditorScreen)) {
            setBounds(180f, 80f);
            return;
        }

        int rows = computeRows(heldContainer);

        float totalWidth = padding * 2f + COLS * slotSize + (COLS - 1) * gap;
        float totalHeight = padding * 2f + rows * slotSize + (rows - 1) * gap;

        Color color = heldContainer.isEmpty() ? backgroundColor.getValue() : ContainerItemUtils.backgroundColor(heldContainer);
        if (backgroundBlur.getValue()) BlurShader.INSTANCE.render(this.x, this.y, totalWidth, totalHeight, radius, blurStrength.getValue());
        if (drawShadow.getValue()) renderScope().shadow(this.x, this.y, totalWidth, totalHeight, radius,
                shadowBlur.getValue().floatValue(), shadowColor.getValue());
        renderScope().roundRect(this.x, this.y, totalWidth, totalHeight, radius, color);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < COLS; col++) {
                float slotX = this.x + padding + col * (slotSize + gap);
                float slotY = this.y + padding + row * (slotSize + gap);
                renderScope().roundRect(slotX, slotY, slotSize, slotSize, 2.0f * s, slotColor.getValue());
            }
        }
        setBounds(totalWidth, totalHeight);
    }

    @Override
    public void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (nullCheck()) return;
        ItemStack container = ContainerItemUtils.findHeldContainer();
        if (container.isEmpty()) return;
        ContainerItemUtils.copyItems(container, containerItems);

        int rows = computeRows(container);
        float s = scale.getValue().floatValue();
        float slotSize = SLOT_SIZE * s;
        float gap = SLOT_GAP * s;
        float padding = PADDING * s;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = row * COLS + col;
                if (index >= containerItems.length) break;
                ItemStack stack = containerItems[index];
                if (stack.isEmpty()) continue;
                float slotX = this.x + padding + col * (slotSize + gap);
                float slotY = this.y + padding + row * (slotSize + gap);
                graphics.pose().pushMatrix();
                graphics.pose().translate(slotX + s, slotY + s);
                graphics.pose().scale(s, s);
                graphics.item(stack, 0, 0);
                if (showCount.getValue() && stack.getCount() > 1) {
                    graphics.itemDecorations(mc.font, stack, 0, 0, String.valueOf(stack.getCount()));
                }
                graphics.pose().popMatrix();
            }
        }
    }

    private int computeRows(ItemStack container) {
        if (container.isEmpty()) {
            return 3; // 编辑器占位预览：保持 3 行
        }
        int itemCount = ContainerItemUtils.getItemCount(container);
        // 收纳袋 ≤ 9*3 = 27 个物品时固定 3 行（与潜影盒/末影箱一致）；仅当超过 27 个才动态增加行数
        if (itemCount <= COLS * 3) {
            return 3;
        }
        int rows = (itemCount + COLS - 1) / COLS; // ceil(itemCount / COLS)
        return Math.min(rows, MAX_ROWS);
    }
}
