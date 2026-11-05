package net.neurallink.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.neurallink.NeuralLink;
import net.neurallink.entity.BodyEntity;

public final class ModEntities {
	private static final RegistryKey<EntityType<?>> BODY_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, NeuralLink.id("body"));

	public static final EntityType<BodyEntity> BODY = Registry.register(Registries.ENTITY_TYPE, BODY_KEY,
			EntityType.Builder.create(BodyEntity::new, SpawnGroup.MISC)
					.dimensions(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.maxTrackingRange(32)
					.build(BODY_KEY));

	public static final TagKey<EntityType<?>> BOSSES = TagKey.of(RegistryKeys.ENTITY_TYPE, NeuralLink.id("bosses"));

	private ModEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(BODY, MobEntity.createMobAttributes());
	}
}
