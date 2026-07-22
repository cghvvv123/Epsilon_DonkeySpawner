package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.Xray;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public class MixinFluidRenderer {

    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
    private void hookTesselate(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output,
                               BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        Xray xray = Xray.INSTANCE;
        if (xray.isEnabled() && xray.wallHack.getValue() && !xray.isCheckableOre(blockState.getBlock())) {
            ci.cancel();
        }
    }

}
