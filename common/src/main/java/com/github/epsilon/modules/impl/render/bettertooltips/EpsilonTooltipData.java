package com.github.epsilon.modules.impl.render.bettertooltips;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Epsilon 自定义 tooltip 图形组件标记接口。
 * 同时实现 TooltipComponent（数据层）与 ClientTooltipComponent（渲染层）的组件通过
 * MixinClientTooltipComponent 将数据组件转换为对应的客户端渲染组件。
 */
public interface EpsilonTooltipData extends TooltipComponent {

    ClientTooltipComponent getComponent();

}
