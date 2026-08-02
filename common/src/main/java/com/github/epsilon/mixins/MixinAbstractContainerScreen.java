package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.bettertooltips.BetterTooltips;
import com.github.epsilon.utils.client.KeybindUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreen<T extends AbstractContainerMenu> extends Screen implements MenuAccess<T> {
    @Shadow protected Slot hoveredSlot;
    @Shadow public abstract T getMenu();

    protected MixinAbstractContainerScreen(Component title) { super(title); }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void epsilon$openWithMouse(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        ItemStack item = hoveredItem();
        if (item.isEmpty() || !getMenu().getCarried().isEmpty()) return;
        int key = KeybindUtils.encodeMouseButton(event.button());
        if (BetterTooltips.INSTANCE.shouldOpenContents(key, true)
                && BetterTooltips.INSTANCE.openContent(item, getMenu(), hoveredSlot.index, this)) cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void epsilon$openWithKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        ItemStack item = hoveredItem();
        if (item.isEmpty() || !getMenu().getCarried().isEmpty()) return;
        if (BetterTooltips.INSTANCE.shouldOpenContents(event.key(), false)
                && BetterTooltips.INSTANCE.openContent(item, getMenu(), hoveredSlot.index, this)) cir.setReturnValue(true);
    }

    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void epsilon$keepMenuForPeek(CallbackInfo ci) {
        if (BetterTooltips.INSTANCE.isOpeningPeek()) ci.cancel();
    }

    @ModifyReturnValue(method = "showTooltipWithItemInHand", at = @At("RETURN"))
    private boolean epsilon$customTooltip(boolean original, ItemStack item) {
        if (item.getTooltipImage().orElse(null) instanceof ClientTooltipComponent component) return original || component.showTooltipWithItemInHand();
        return original;
    }

    private ItemStack hoveredItem() {
        return hoveredSlot == null ? ItemStack.EMPTY : hoveredSlot.getItem();
    }
}
