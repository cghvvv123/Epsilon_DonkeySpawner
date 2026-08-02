package com.github.epsilon.events.impl;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 物品 Tooltip 文本行事件，在 ItemStack.getTooltipLines 返回前发布。
 * 监听者可通过 appendStart/appendEnd 等方法修改 tooltip 文本行。
 */
public class ItemStackTooltipEvent {

    private final ItemStack itemStack;
    private List<Component> list;

    public ItemStackTooltipEvent(ItemStack itemStack, List<Component> list) {
        this.itemStack = itemStack;
        this.list = list;
    }

    public List<Component> list() {
        return list;
    }

    public ItemStack itemStack() {
        return itemStack;
    }

    public void appendStart(Component text) {
        copyIfImmutable();
        int index = list.isEmpty() ? 0 : 1;
        list.add(index, text);
    }

    public void appendEnd(Component text) {
        copyIfImmutable();
        list.add(text);
    }

    public void append(int index, Component text) {
        copyIfImmutable();
        list.add(index, text);
    }

    public void set(int index, Component text) {
        copyIfImmutable();
        list.set(index, text);
    }

    private void copyIfImmutable() {
        // ItemStack#getTooltipLines 有时返回 List.of() 这类不可变列表，直接修改会崩溃，需要先复制
        if (List.of().getClass().getSuperclass().isInstance(list)) {
            list = new ObjectArrayList<>(list);
        }
    }

}
