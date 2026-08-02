package com.github.epsilon.modules.impl.render.bettertooltips;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;

public class BundleTooltipComponent implements ClientTooltipComponent, EpsilonTooltipData {
    private static final Identifier SLOT = Identifier.withDefaultNamespace("container/bundle/slot_background");
    private static final Identifier BORDER = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_border");
    private static final Identifier FILL = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_fill");
    private static final Identifier FULL = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_full");
    private static final int SLOTS_PER_ROW = 9;
    private static final int SLOT_SIZE = 24;
    private static final int WIDTH = 8 + SLOTS_PER_ROW * SLOT_SIZE + 8;
    private static final int BAR_WIDTH = 94;
    private static final int BAR_HEIGHT = 13;

    private final ItemStack[] items;
    private final BundleContents contents;
    private final int height;

    public BundleTooltipComponent(ItemStack[] items, BundleContents contents) {
        this.items = items;
        this.contents = contents;
        int rows = (items.length + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW;
        height = 8 + rows * SLOT_SIZE + 8 + BAR_HEIGHT + 4;
    }

    @Override
    public ClientTooltipComponent getComponent() { return this; }

    @Override
    public int getHeight(Font font) { return height; }

    @Override
    public int getWidth(Font font) { return WIDTH; }

    @Override
    public void extractImage(Font font, int x, int y, int width, int unusedHeight, GuiGraphicsExtractor graphics) {
        int row = 0;
        int col = 0;
        for (int index = 0; index < items.length; index++) {
            ItemStack item = items[index];
            if (!item.isEmpty()) {
                int slotX = x + 8 + col * SLOT_SIZE;
                int slotY = y + 8 + row * SLOT_SIZE;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, slotX, slotY, SLOT_SIZE, SLOT_SIZE);
                graphics.item(item, slotX + 4, slotY + 4, 0);
                graphics.itemDecorations(font, item, slotX + 4, slotY + 4);
            }
            col++;
            if (col >= SLOTS_PER_ROW) { col = 0; row++; }
        }
        int barX = x + (WIDTH - BAR_WIDTH) / 2;
        int barY = y + height - BAR_HEIGHT - 4;
        int fill = Mth.clamp(Mth.mulAndTruncate(contents.weight().getOrThrow(), BAR_WIDTH), 0, BAR_WIDTH);
        Identifier fillTexture = contents.weight().getOrThrow().compareTo(Fraction.ONE) >= 0 ? FULL : FILL;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, fillTexture, barX + 1, barY, fill, BAR_HEIGHT);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BORDER, barX, barY, BAR_WIDTH, BAR_HEIGHT);
        Component label = contents.weight().getOrThrow().compareTo(Fraction.ONE) >= 0
                ? Component.translatable("item.minecraft.bundle.full")
                : Component.literal(String.format("%.2f%%", contents.weight().getOrThrow().floatValue() * 100));
        graphics.centeredText(font, label, barX + BAR_WIDTH / 2, barY + 3, CommonColors.WHITE);
    }
}
