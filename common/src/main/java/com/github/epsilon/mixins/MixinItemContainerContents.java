package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.bettertooltips.BetterTooltips;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Mixin(ItemContainerContents.class)
public abstract class MixinItemContainerContents {
    @Shadow @Final private List<Optional<ItemStackTemplate>> items;

    @Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
    private void epsilon$containerTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components, CallbackInfo ci) {
        BetterTooltips module = BetterTooltips.INSTANCE;
        if (!module.isEnabled()) return;
        if (module.previewShulkers()) ci.cancel();
        else if (module.shulkerCompactTooltip()) {
            ci.cancel();
            module.applyCompactShulkerTooltip(items, consumer);
        }
    }
}
