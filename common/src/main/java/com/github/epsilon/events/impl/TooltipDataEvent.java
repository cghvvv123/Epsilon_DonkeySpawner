package com.github.epsilon.events.impl;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * 物品 Tooltip 图形数据事件，在 Item.getTooltipImage 时发布。
 * 监听者可设置 tooltipData 提供自定义的图形化 tooltip 组件（如容器预览）。
 */
public class TooltipDataEvent {

    private static final TooltipDataEvent INSTANCE = new TooltipDataEvent();

    public TooltipComponent tooltipData;
    public ItemStack itemStack;

    public static TooltipDataEvent get(ItemStack itemStack) {
        INSTANCE.tooltipData = null;
        INSTANCE.itemStack = itemStack;
        return INSTANCE;
    }

}
