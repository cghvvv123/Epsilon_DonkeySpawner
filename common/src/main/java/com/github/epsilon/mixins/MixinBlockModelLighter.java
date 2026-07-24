package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.Fullbright;
import com.github.epsilon.modules.impl.render.Xray;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.LightCoordsUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockModelLighter.class)
public class MixinBlockModelLighter {

    @Inject(method = "prepareQuadAmbientOcclusion", at = @At("RETURN"))
    private void forceFullBrightAmbientOcclusion(BlockAndTintGetter level, BlockState state, BlockPos centerPosition,
                                                 BakedQuad quad, QuadInstance outputInstance, CallbackInfo ci) {
        forceFullBright(outputInstance);
    }

    @Inject(method = "prepareQuadFlat", at = @At("RETURN"))
    private void forceFullBrightFlat(BlockAndTintGetter level, BlockState state, BlockPos pos, int lightCoords,
                                     BakedQuad quad, QuadInstance outputInstance, CallbackInfo ci) {
        forceFullBright(outputInstance);
    }

    private void forceFullBright(QuadInstance outputInstance) {
        if (Xray.INSTANCE.isEnabled() && Fullbright.INSTANCE.isGammaMode()) {
            outputInstance.setLightCoords(LightCoordsUtil.pack(15, 15));
            outputInstance.setColor(-1);
        }
    }
}
