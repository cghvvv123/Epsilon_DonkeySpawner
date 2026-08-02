package com.github.epsilon.utils.render;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jetbrains.annotations.Nullable;

public record CustomBannerGuiElementRenderState(
        BannerFlagModel flag,
        DyeColor baseColor,
        BannerPatternLayers patterns,
        int x0,
        int y0,
        int x1,
        int y1,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds,
        float scale
) implements PictureInPictureRenderState {
    public CustomBannerGuiElementRenderState(BannerFlagModel flag, DyeColor baseColor, BannerPatternLayers patterns,
                                              int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea, float scale) {
        this(flag, baseColor, patterns, x0, y0, x1, y1, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea), scale);
    }
}
