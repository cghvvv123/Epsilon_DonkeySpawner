package com.github.epsilon.mixins;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.MobBucketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobBucketItem.class)
public interface MixinMobBucketItem {
    @Accessor("type")
    EntityType<?> epsilon$getType();
}
