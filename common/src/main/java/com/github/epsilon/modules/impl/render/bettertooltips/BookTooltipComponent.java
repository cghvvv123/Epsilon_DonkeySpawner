package com.github.epsilon.modules.impl.render.bettertooltips;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;

public class BookTooltipComponent implements ClientTooltipComponent, EpsilonTooltipData {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/book.png");
    private final Component page;

    public BookTooltipComponent(Component page) {
        this.page = page;
    }

    @Override
    public ClientTooltipComponent getComponent() {
        return this;
    }

    @Override
    public int getHeight(Font font) {
        return 134;
    }

    @Override
    public int getWidth(Font font) {
        return 112;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x - 10, y, 0, 0, 128, 128, 179, 179);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x + 16, y + 12);
        pose.scale(0.7f, 0.7f);
        int offset = 0;
        for (FormattedCharSequence line : font.split(page, 112)) {
            graphics.text(font, line, 0, offset, 0xFF000000, false);
            offset += 8;
        }
        pose.popMatrix();
    }
}
