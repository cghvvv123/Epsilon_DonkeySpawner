package com.github.epsilon.utils.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.resources.model.sprite.SpriteGetter;

public class CustomBannerGuiElementRenderer extends PictureInPictureRenderer<CustomBannerGuiElementRenderState> {
    private final SpriteGetter sprites;

    public CustomBannerGuiElementRenderer(MultiBufferSource.BufferSource bufferSource, SpriteGetter sprites) {
        super(bufferSource);
        this.sprites = sprites;
    }

    @Override
    public Class<CustomBannerGuiElementRenderState> getRenderStateClass() {
        return CustomBannerGuiElementRenderState.class;
    }

    @Override
    protected void renderToTexture(CustomBannerGuiElementRenderState state, PoseStack pose) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_FLAT);
        pose.translate(0.0F, 0.25F, 0.0F);
        FeatureRenderDispatcher dispatcher = minecraft.gameRenderer.getFeatureRenderDispatcher();
        SubmitNodeStorage storage = dispatcher.getSubmitNodeStorage();
        BannerRenderer.submitPatterns(sprites, pose, storage, 15728880, OverlayTexture.NO_OVERLAY,
                state.flag(), 0.0F, true, state.baseColor(), state.patterns(), null);
        dispatcher.renderAllFeatures();
    }

    @Override
    protected String getTextureLabel() {
        return "epsilon banner";
    }
}
