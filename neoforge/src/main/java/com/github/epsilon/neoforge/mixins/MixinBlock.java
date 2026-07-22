package com.github.epsilon.neoforge.mixins;

import com.github.epsilon.modules.impl.render.Xray;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class MixinBlock {

    @Inject(
            method = "shouldRenderFace(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void hookShouldRenderFace(BlockGetter level, BlockPos pos, BlockState state, BlockState neighborState,
                                             Direction direction, CallbackInfoReturnable<Boolean> cir) {
        Xray xray = Xray.INSTANCE;
        if (xray.isEnabled() && xray.wallHack.getValue()) {
            cir.setReturnValue(xray.isCheckableOre(state.getBlock()));
        }
    }

}
