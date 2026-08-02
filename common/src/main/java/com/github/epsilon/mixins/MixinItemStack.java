package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.ItemStackTooltipEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import java.util.List;

@Mixin(ItemStack.class)
public abstract class MixinItemStack {
    @ModifyReturnValue(method = "getTooltipLines", at = @At("RETURN"))
    private List<Component> epsilon$tooltipLines(List<Component> original) {
        return EventBus.INSTANCE.post(new ItemStackTooltipEvent((ItemStack) (Object) this, original)).list();
    }
}
