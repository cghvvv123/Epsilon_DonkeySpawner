package com.github.epsilon.mixins;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface MixinEntityWaterState {
    @Accessor("wasTouchingWater")
    void epsilon$setInWater(boolean touchingWater);
}
