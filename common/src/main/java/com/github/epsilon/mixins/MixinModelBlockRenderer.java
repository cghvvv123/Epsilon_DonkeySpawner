package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.Fullbright;
import com.github.epsilon.modules.impl.render.Xray;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public class MixinModelBlockRenderer {

    @Shadow
    private QuadInstance quadInstance;

    @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
    private void hookTesselateBlock(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level,
                                    BlockPos pos, BlockState blockState, BlockStateModel model, long seed,
                                    CallbackInfo ci) {
        Xray xray = Xray.INSTANCE;
        if (xray.isEnabled() && xray.wallHack.getValue() && !xray.isCheckableOre(blockState.getBlock())) {
            ci.cancel();
        }
    }

    // Fullbright hook on the vanilla render path (NeoForge without Sodium).
    // Injected right before BlockQuadOutput.put so it overrides any lighting/color
    // computed earlier in the chain, including BlockModelLighter and tint multiply.
    @Inject(method = "putQuadWithTint",
            at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
                    target = "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
    private void forceFullBrightPut(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level,
                                      BlockState state, BlockPos pos, BakedQuad quad, CallbackInfo ci) {
        if (Xray.INSTANCE.isEnabled() && Fullbright.INSTANCE.isGammaMode()) {
            this.quadInstance.setLightCoords(LightCoordsUtil.pack(15, 15));
            this.quadInstance.setColor(-1);
        }
    }

}
