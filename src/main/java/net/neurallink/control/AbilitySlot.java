package net.neurallink.control;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** icon is an item or entity id, cooldown in ticks. */
public record AbilitySlot(Ability ability, Identifier icon, int cooldown) {
	public AbilitySlot(Ability ability, Item icon, int cooldown) {
		this(ability, Registries.ITEM.getId(icon), cooldown);
	}
}
