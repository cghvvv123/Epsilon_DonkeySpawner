package com.github.epsilon.modules.impl.render.bettertooltips;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.awt.Color;

/**
 * 容器内容预览 tooltip 组件（潜影盒/末影箱 27 格图形化预览，背景按容器颜色染色）。
 */
public class ContainerTooltipComponent implements ClientTooltipComponent, EpsilonTooltipData {

    private static final Identifier TEXTURE_CONTAINER_BACKGROUND = Identifier.fromNamespaceAndPath("epsilon", "textures/container.png");
    private static final Identifier SLOT_TEXTURE = Identifier.withDefaultNamespace("container/slot");
    private static final int SLOT_SIZE = 18;
    private static final int COMPACT_PADDING = 4;

    private final ItemStack[] items;
    private final Color color;
    private final int columns;

    public ContainerTooltipComponent(ItemStack[] items, Color color) {
        this(items, color, 9);
    }

    public ContainerTooltipComponent(ItemStack[] items, Color color, int columns) {
        this.items = items;
        this.color = color;
        this.columns = Math.max(1, Math.min(9, columns));
    }

    @Override
    public ClientTooltipComponent getComponent() {
        return this;
    }

    @Override
    public int getHeight(Font font) {
        return columns == 3 ? COMPACT_PADDING * 2 + SLOT_SIZE * 3 : 67;
    }

    @Override
    public int getWidth(Font font) {
        return columns == 3 ? COMPACT_PADDING * 2 + SLOT_SIZE * columns : 176;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        if (columns == 3) {
            extractCompactImage(font, x, y, graphics);
            return;
        }

        // 背景
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_CONTAINER_BACKGROUND, x, y, 0, 0, 176, 67, 176, 67, color.getRGB());

        // 内容物
        int slotCount = Math.min(items.length, columns * 3);
        int left = x + (176 - columns * SLOT_SIZE) / 2;

        for (int index = 0; index < slotCount; index++) {
            ItemStack itemStack = items[index];
            int column = index % columns;
            int row = index / columns;
            drawItem(graphics, itemStack, left + column * SLOT_SIZE, y + 7 + row * SLOT_SIZE, 1);
        }
    }

    private void extractCompactImage(Font font, int x, int y, GuiGraphicsExtractor graphics) {
        int width = getWidth(font);
        int height = getHeight(font);
        graphics.fill(x, y, x + width, y + height, color.getRGB());

        int slotLeft = x + COMPACT_PADDING;
        int slotTop = y + COMPACT_PADDING;
        for (int index = 0; index < Math.min(items.length, 9); index++) {
            int column = index % 3;
            int row = index / 3;
            int slotX = slotLeft + column * SLOT_SIZE;
            int slotY = slotTop + row * SLOT_SIZE;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_TEXTURE, slotX, slotY, SLOT_SIZE, SLOT_SIZE);
            drawItem(graphics, items[index], slotX + 1, slotY + 1, 1);
        }
    }

    private static void drawItem(GuiGraphicsExtractor graphics, ItemStack itemStack, int x, int y, float scale) {
        graphics.item(itemStack, x, y);
        graphics.itemDecorations(net.minecraft.client.Minecraft.getInstance().font, itemStack, x, y);
    }

}
