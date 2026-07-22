package com.github.epsilon.neoforge.mixins;

import com.github.epsilon.neoforge.EpsilonNeoForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Unique
    private int epsilon$terrainRefreshTicks = -1;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(GameConfig gameConfig, CallbackInfo ci) {
        EpsilonNeoForge.init();
    }

    @Inject(method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V", at = @At("TAIL"))
    private void scheduleTerrainRefresh(ClientLevel level, boolean stopSound, CallbackInfo ci) {
        epsilon$terrainRefreshTicks = level == null ? -1 : 3;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void refreshTerrainAfterWorldLoad(CallbackInfo ci) {
        if (epsilon$terrainRefreshTicks < 0 || --epsilon$terrainRefreshTicks > 0) return;

        epsilon$terrainRefreshTicks = -1;
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.level != null) {
            minecraft.levelRenderer.resetSampler();
            minecraft.levelRenderer.allChanged();
        }
    }

}
