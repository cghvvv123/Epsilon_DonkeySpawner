package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.movement.EntityControl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Strider.class)
public abstract class MixinStrider {

    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void overrideGetControllingPassenger(CallbackInfoReturnable<LivingEntity> cir) {
        EntityControl entityControl = EntityControl.INSTANCE;
        if (entityControl.isEnabled() && entityControl.spoofSaddle()
                && ((LivingEntity) (Object) this).getFirstPassenger() instanceof Player player) {
            cir.setReturnValue(player);
        }
    }
}
