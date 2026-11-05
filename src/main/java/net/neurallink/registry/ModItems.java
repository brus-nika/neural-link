package net.neurallink.registry;

import java.util.function.Function;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Rarity;
import net.neurallink.NeuralLink;
import net.neurallink.item.ChipItem;

public final class ModItems {
	private static final RegistryKey<EquipmentAsset> HELMET_ASSET = RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, NeuralLink.id("neural_helmet"));

	public static final Item MICROCHIP = register("microchip", ChipItem::new, new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON));

	/** leather helmet armor (1). No durability, no enchanting. */
	public static final Item NEURAL_HELMET = register("neural_helmet", Item::new, new Item.Settings()
			.maxCount(1)
			.rarity(Rarity.RARE)
			.component(DataComponentTypes.EQUIPPABLE, EquippableComponent.builder(EquipmentSlot.HEAD)
					.equipSound(SoundEvents.ITEM_ARMOR_EQUIP_IRON)
					.model(HELMET_ASSET)
					.build())
			.attributeModifiers(AttributeModifiersComponent.builder()
					.add(EntityAttributes.ARMOR,
							new EntityAttributeModifier(NeuralLink.id("neural_helmet_armor"), 1.0, EntityAttributeModifier.Operation.ADD_VALUE),
							AttributeModifierSlot.HEAD)
					.build()));

	private ModItems() {
	}

	private static Item register(String path, Function<Item.Settings, Item> factory, Item.Settings settings) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, NeuralLink.id(path));
		return Registry.register(Registries.ITEM, key, factory.apply(settings.registryKey(key)));
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
			entries.add(NEURAL_HELMET);
			entries.add(MICROCHIP);
		});
	}
}
