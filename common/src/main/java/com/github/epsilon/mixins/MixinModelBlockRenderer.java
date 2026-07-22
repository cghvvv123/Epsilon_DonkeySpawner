package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.Xray;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public class MixinModelBlockRenderer {

    @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
    private void hookTesselateBlock(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level,
                                    BlockPos pos, BlockState blockState, BlockStateModel model, long seed,
                                    CallbackInfo ci) {
        Xray xray = Xray.INSTANCE;
        if (xray.isEnabled() && xray.wallHack.getValue() && !xray.isCheckableOre(blockState.getBlock())) {
            ci.cancel();
        }
    }

}
