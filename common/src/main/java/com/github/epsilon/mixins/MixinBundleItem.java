package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.TooltipDataEvent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(BundleItem.class)
public abstract class MixinBundleItem {
    @Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
    private void epsilon$tooltipData(ItemStack itemStack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        TooltipDataEvent event = EventBus.INSTANCE.post(TooltipDataEvent.get(itemStack));
        if (event.tooltipData != null) cir.setReturnValue(Optional.of(event.tooltipData));
    }
}
