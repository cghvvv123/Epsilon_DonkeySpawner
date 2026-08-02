package com.github.epsilon.modules.impl.render.bettertooltips;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 纯文本 tooltip 图形组件（用于末影箱内容未知等提示）。
 */
public class TextTooltipComponent extends ClientTextTooltip implements EpsilonTooltipData {

    public TextTooltipComponent(FormattedCharSequence text) {
        super(text);
    }

    public TextTooltipComponent(Component text) {
        this(text.getVisualOrderText());
    }

    @Override
    public ClientTextTooltip getComponent() {
        return this;
    }

}
