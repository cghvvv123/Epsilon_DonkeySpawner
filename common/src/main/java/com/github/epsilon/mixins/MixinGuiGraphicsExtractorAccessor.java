package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.GuiGraphicsExtractorAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiGraphicsExtractor.class)
public interface MixinGuiGraphicsExtractorAccessor extends GuiGraphicsExtractorAccessor {
    @Override
    @Accessor("guiRenderState")
    GuiRenderState epsilon$getGuiRenderState();

}
