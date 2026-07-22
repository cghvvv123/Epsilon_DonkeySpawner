package com.github.epsilon.fabric.mixins;

import com.github.epsilon.modules.impl.render.Xray;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl")
public class MixinIndigoAltModelBlockRenderer {

    @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
    private void hookTesselateBlock(QuadEmitter output, float x, float y, float z, BlockAndTintGetter level,
                                    BlockPos pos, BlockState blockState, BlockStateModel model, long seed,
                                    CallbackInfo ci) {
        Xray xray = Xray.INSTANCE;
        if (xray.isEnabled() && xray.wallHack.getValue() && !xray.isCheckableOre(blockState.getBlock())) {
            ci.cancel();
        }
    }

}
