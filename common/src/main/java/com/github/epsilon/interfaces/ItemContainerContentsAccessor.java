package com.github.epsilon.interfaces;

import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Optional;

public interface ItemContainerContentsAccessor {
    List<Optional<ItemStackTemplate>> epsilon$getItems();
}
