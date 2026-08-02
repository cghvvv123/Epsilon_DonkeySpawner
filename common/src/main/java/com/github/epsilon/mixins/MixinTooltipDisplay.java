package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.bettertooltips.BetterTooltips;
import net.minecraft.world.item.component.TooltipDisplay;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Mixin(TooltipDisplay.class)
public abstract class MixinTooltipDisplay {
    @ModifyExpressionValue(method = "shows", at = @At(value = "FIELD", target = "Lnet/minecraft/world/item/component/TooltipDisplay;hideTooltip:Z", opcode = Opcodes.GETFIELD))
    private boolean epsilon$showHiddenTooltip(boolean original) {
        return original && (!BetterTooltips.INSTANCE.isEnabled() || !BetterTooltips.INSTANCE.tooltip.getValue());
    }

    @ModifyExpressionValue(method = "shows", at = @At(value = "INVOKE", target = "Ljava/util/SequencedSet;contains(Ljava/lang/Object;)Z"))
    private boolean epsilon$showHiddenComponents(boolean original) {
        return original && (!BetterTooltips.INSTANCE.isEnabled() || !BetterTooltips.INSTANCE.additional.getValue());
    }
}
