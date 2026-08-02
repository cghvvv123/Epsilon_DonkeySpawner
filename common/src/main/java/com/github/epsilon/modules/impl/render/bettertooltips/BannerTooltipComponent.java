package com.github.epsilon.modules.impl.render.bettertooltips;

import com.github.epsilon.interfaces.GuiGraphicsExtractorAccessor;
import com.github.epsilon.utils.render.CustomBannerGuiElementRenderState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

public class BannerTooltipComponent implements ClientTooltipComponent, EpsilonTooltipData {
    private final DyeColor color;
    private final BannerPatternLayers patterns;
    private final BannerFlagModel bannerFlag;

    public BannerTooltipComponent(ItemStack banner) {
        this(((BannerItem) banner.getItem()).getColor(), banner.getOrDefault(net.minecraft.core.component.DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
    }

    public BannerTooltipComponent(DyeColor color, BannerPatternLayers patterns) {
        this.color = color;
        this.patterns = patterns;
        ModelPart modelPart = com.github.epsilon.Constants.mc.getEntityModels().bakeLayer(ModelLayers.STANDING_BANNER_FLAG);
        this.bannerFlag = new BannerFlagModel(modelPart);
    }

    @Override
    public ClientTooltipComponent getComponent() { return this; }

    @Override
    public int getHeight(Font font) { return 40; }

    @Override
    public int getWidth(Font font) { return 96; }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        GuiGraphicsExtractorAccessor accessor = (GuiGraphicsExtractorAccessor) graphics;
        int centerX = width / 2 - getWidth(null) / 2;
        accessor.epsilon$getGuiRenderState().addPicturesInPictureState(new CustomBannerGuiElementRenderState(
                bannerFlag, color, patterns,
                centerX + x, y, centerX + x + getWidth(null), y + getHeight(null),
                null, 32
        ));
    }
}
