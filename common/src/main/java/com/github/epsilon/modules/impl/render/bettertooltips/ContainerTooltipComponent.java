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

    private final ItemStack[] items;
    private final Color color;

    public ContainerTooltipComponent(ItemStack[] items, Color color) {
        this.items = items;
        this.color = color;
    }

    @Override
    public ClientTooltipComponent getComponent() {
        return this;
    }

    @Override
    public int getHeight(Font font) {
        return 67;
    }

    @Override
    public int getWidth(Font font) {
        return 176;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        // 背景
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_CONTAINER_BACKGROUND, x, y, 0, 0, 176, 67, 176, 67, color.getRGB());

        // 内容物
        int row = 0;
        int i = 0;

        for (ItemStack itemStack : items) {
            drawItem(graphics, itemStack, x + 8 + i * 18, y + 7 + row * 18, 1);

            i++;
            if (i >= 9) {
                i = 0;
                row++;
            }
        }
    }

    private static void drawItem(GuiGraphicsExtractor graphics, ItemStack itemStack, int x, int y, float scale) {
        graphics.item(itemStack, x, y);
        graphics.itemDecorations(net.minecraft.client.Minecraft.getInstance().font, itemStack, x, y);
    }

}
