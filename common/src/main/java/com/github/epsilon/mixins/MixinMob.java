package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.movement.EntityControl;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Mob.class, priority = 1001)
public abstract class MixinMob {

    @ModifyReturnValue(method = "isSaddled", at = @At("RETURN"))
    private boolean overrideSaddleCheck(boolean original) {
        EntityControl entityControl = EntityControl.INSTANCE;
        if (entityControl.isEnabled() && entityControl.spoofSaddle()) {
            return true;
        }
        return original;
    }
}
