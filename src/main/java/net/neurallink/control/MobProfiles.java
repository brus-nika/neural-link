package net.neurallink.control;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** One entry per mob. Unlisted mobs walk and hit. */
public final class MobProfiles {
	private static final Map<EntityType<?>, MobProfile> PROFILES = new HashMap<>();
	private static final AbilitySlot MELEE = new AbilitySlot(MeleeAbilities.MELEE, Items.IRON_SWORD, 20);
	private static final AbilitySlot BOW = new AbilitySlot(RangedAbilities.BOW, Items.BOW, 20);
	private static final MobProfile WALKER = MobProfile.of(Movement.WALK);
	private static final MobProfile FIGHTER = MobProfile.of(Movement.WALK, MELEE);
	/** like vanilla only a mob holding the weapon shoots. */
	private static final Map<EntityType<?>, Item> HELD_WEAPONS = Map.of(
			EntityType.DROWNED, Items.TRIDENT,
			EntityType.PILLAGER, Items.CROSSBOW,
			EntityType.PIGLIN, Items.CROSSBOW);

	static {
		put(Movement.WALK, new EntityType<?>[] {EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED}, BOW);
		put(Movement.WALK, new EntityType<?>[] {EntityType.PILLAGER, EntityType.PIGLIN}, slot(RangedAbilities.CROSSBOW, Items.CROSSBOW, 10));
		put(Movement.WALK, new EntityType<?>[] {EntityType.DROWNED}, slot(RangedAbilities.TRIDENT, Items.TRIDENT, 40));
		put(Movement.WALK, new EntityType<?>[] {EntityType.SNOW_GOLEM}, slot(RangedAbilities.SNOWBALL, Items.SNOWBALL, 10));
		put(Movement.WALK, new EntityType<?>[] {EntityType.LLAMA, EntityType.TRADER_LLAMA}, slot(RangedAbilities.LLAMA_SPIT, Items.GHAST_TEAR, 40));

		put(Movement.HOVER, new EntityType<?>[] {EntityType.GHAST}, slot(RangedAbilities.GHAST_FIREBALL, Items.FIRE_CHARGE, 60));
		put(Movement.BLAZE, new EntityType<?>[] {EntityType.BLAZE}, slot(RangedAbilities.BLAZE_VOLLEY, Items.FIRE_CHARGE, 60), slot(RangedAbilities.BLAZE_FLIGHT, Items.BLAZE_POWDER, 10));
		put(Movement.BREEZE, new EntityType<?>[] {EntityType.BREEZE}, slot(RangedAbilities.WIND_CHARGE, Items.WIND_CHARGE, 40), slot(RangedAbilities.HIGH_JUMP, Items.RABBIT_FOOT, 40));
		put(Movement.SWIM, new EntityType<?>[] {EntityType.GUARDIAN}, slot(RangedAbilities.GUARDIAN_LASER, Items.PRISMARINE_SHARD, 80), slot(RangedAbilities.GUARDIAN_SPIKES, Items.PRISMARINE_CRYSTALS, 10));
		put(Movement.SWIM, new EntityType<?>[] {EntityType.ELDER_GUARDIAN}, slot(RangedAbilities.GUARDIAN_LASER, Items.PRISMARINE_SHARD, 80),
				slot(RangedAbilities.ELDER_FATIGUE, Items.GOLDEN_PICKAXE, 600), slot(RangedAbilities.GUARDIAN_SPIKES, Items.PRISMARINE_CRYSTALS, 10));

		put(Movement.WALK, new EntityType<?>[] {EntityType.WITCH}, slot(CasterAbilities.WITCH_POTION, Items.SPLASH_POTION, 60), slot(CasterAbilities.WITCH_DRINK, Items.POTION, 200));
		put(Movement.CASTER, new EntityType<?>[] {EntityType.EVOKER}, slot(CasterAbilities.EVOKER_FANGS, Items.BONE, 60), slot(CasterAbilities.EVOKER_VEXES, Items.TOTEM_OF_UNDYING, 340));
		put(Movement.CASTER, new EntityType<?>[] {EntityType.ILLUSIONER}, BOW, slot(CasterAbilities.ILLUSIONER_BLINDNESS, Items.INK_SAC, 200), slot(CasterAbilities.ILLUSIONER_MIRROR, Items.GLASS_PANE, 300));
		put(Movement.IMMOBILE, new EntityType<?>[] {EntityType.SHULKER},
				slot(CasterAbilities.SHULKER_BULLET, Registries.ENTITY_TYPE.getId(EntityType.SHULKER_BULLET), 40),
				slot(CasterAbilities.SHULKER_CLOSE, Items.SHULKER_SHELL, 10),
				slot(CasterAbilities.SHULKER_TELEPORT, Items.CHORUS_FRUIT, 100));
		put(Movement.WALK, new EntityType<?>[] {EntityType.ENDERMAN}, MELEE, slot(CasterAbilities.ENDERMAN_TELEPORT, Items.ENDER_PEARL, 60), slot(CasterAbilities.ENDERMAN_BLOCK, Items.GRASS_BLOCK, 10));
		put(Movement.FLUTTER, new EntityType<?>[] {EntityType.VEX}, MELEE, slot(CasterAbilities.VEX_PHASE, Items.PHANTOM_MEMBRANE, 10));
		put(Movement.WALK, new EntityType<?>[] {EntityType.SILVERFISH}, MELEE, slot(CasterAbilities.SILVERFISH_CALL, Items.COBBLESTONE, 100), slot(CasterAbilities.SILVERFISH_HIDE, Items.INFESTED_STONE, 20));

		put(Movement.WALK, new EntityType<?>[] {EntityType.WARDEN}, slot(MeleeAbilities.MELEE, Items.IRON_SWORD, 18),
				slot(BossAbilities.WARDEN_BOOM, Items.ECHO_SHARD, 100), slot(BossAbilities.WARDEN_BURROW, Items.SCULK, 140));
		put(Movement.WITHER, new EntityType<?>[] {EntityType.WITHER}, slot(BossAbilities.WITHER_BLACK_SKULL, Items.WITHER_SKELETON_SKULL, 15),
				slot(BossAbilities.WITHER_BLUE_SKULL, Items.WITHER_SKELETON_SKULL, 60), slot(BossAbilities.WITHER_RUSH, Items.NETHER_STAR, 80));
		put(Movement.DRAGON, new EntityType<?>[] {EntityType.ENDER_DRAGON}, slot(BossAbilities.DRAGON_FIREBALL, Items.DRAGON_BREATH, 40),
				slot(BossAbilities.DRAGON_RAM, Items.DRAGON_HEAD, 120), slot(BossAbilities.DRAGON_WINGS, Items.ELYTRA, 100));

		put(Movement.WALK, new EntityType<?>[] {EntityType.CREEPER}, null, slot(MeleeAbilities.CREEPER_FUSE, Items.TNT, 0));
		put(Movement.CLIMB, new EntityType<?>[] {EntityType.SPIDER, EntityType.CAVE_SPIDER}, MELEE);
		put(Movement.FLUTTER, new EntityType<?>[] {EntityType.BEE}, slot(MeleeAbilities.BEE_STING, Items.IRON_SWORD, 20));
		put(Movement.WALK, new EntityType<?>[] {EntityType.GOAT}, MELEE, slot(MeleeAbilities.GOAT_RAM, Items.GOAT_HORN, 100), slot(MeleeAbilities.GOAT_JUMP, Items.RABBIT_FOOT, 40));
		put(Movement.WALK, new EntityType<?>[] {EntityType.RAVAGER}, MELEE, slot(MeleeAbilities.RAVAGER_ROAR, Items.BELL, 100));
		put(Movement.WALK, new EntityType<?>[] {EntityType.POLAR_BEAR}, MELEE, slot(MeleeAbilities.POLAR_STAND, Items.SHIELD, 10));
		put(Movement.GLIDE, new EntityType<?>[] {EntityType.PHANTOM}, slot(MeleeAbilities.PHANTOM_DIVE, Items.PHANTOM_MEMBRANE, 40));
		put(Movement.HOP, new EntityType<?>[] {EntityType.SLIME}, slot(MeleeAbilities.SLIME_STRIKE, Items.SLIME_BALL, 30));
		put(Movement.HOP, new EntityType<?>[] {EntityType.MAGMA_CUBE}, slot(MeleeAbilities.SLIME_STRIKE, Items.MAGMA_CREAM, 30));
		put(Movement.WALK, new EntityType<?>[] {EntityType.CREAKING}, MELEE, slot(MeleeAbilities.CREAKING_FREEZE, Items.BEDROCK, 10));

		put(Movement.ARMADILLO, new EntityType<?>[] {EntityType.ARMADILLO}, null, slot(PeacefulAbilities.ARMADILLO_ROLL, Items.ARMADILLO_SCUTE, 20));
		put(Movement.FISH, new EntityType<?>[] {EntityType.COD, EntityType.SALMON, EntityType.TROPICAL_FISH, EntityType.TADPOLE});
		put(Movement.FISH, new EntityType<?>[] {EntityType.PUFFERFISH}, slot(PeacefulAbilities.PUFF, Items.PUFFERFISH, 100));
		put(Movement.SQUID, new EntityType<?>[] {EntityType.SQUID}, null, slot(PeacefulAbilities.SQUID_JET, Items.INK_SAC, 60));
		put(Movement.SQUID, new EntityType<?>[] {EntityType.GLOW_SQUID}, null, slot(PeacefulAbilities.SQUID_JET, Items.GLOW_INK_SAC, 60));
		put(Movement.SWIM, new EntityType<?>[] {EntityType.AXOLOTL}, MELEE, slot(PeacefulAbilities.AXOLOTL_PLAY_DEAD, Items.BONE, 10));
		put(Movement.DOLPHIN, new EntityType<?>[] {EntityType.DOLPHIN}, MELEE, slot(PeacefulAbilities.SWIM_DASH, Items.FEATHER, 60), slot(PeacefulAbilities.DOLPHIN_GRACE, Items.HEART_OF_THE_SEA, 200));
		put(Movement.WALK, new EntityType<?>[] {EntityType.PANDA}, MELEE, slot(PeacefulAbilities.PANDA_SNEEZE, Items.SLIME_BALL, 80), slot(PeacefulAbilities.PANDA_ROLL, Items.BAMBOO, 80));
		put(Movement.WALK, new EntityType<?>[] {EntityType.FOX}, MELEE, slot(PeacefulAbilities.FOX_POUNCE, Items.RABBIT_FOOT, 40), slot(PeacefulAbilities.FOX_ITEM, Items.SWEET_BERRIES, 10));
		put(Movement.SKIP, new EntityType<?>[] {EntityType.RABBIT});
		put(Movement.FROG, new EntityType<?>[] {EntityType.FROG}, slot(PeacefulAbilities.FROG_TONGUE, Items.OCHRE_FROGLIGHT, 30), slot(PeacefulAbilities.FROG_LONG_JUMP, Items.LILY_PAD, 40));
		put(Movement.WALK, new EntityType<?>[] {EntityType.CAMEL}, null, slot(PeacefulAbilities.CAMEL_DASH, Items.CACTUS, 60), slot(PeacefulAbilities.CAMEL_SIT, Items.SADDLE, 20));
		put(Movement.WALK, new EntityType<?>[] {EntityType.SNIFFER}, null, slot(PeacefulAbilities.SNIFFER_DIG, Items.TORCHFLOWER_SEEDS, 200));
		put(Movement.FLUTTER, new EntityType<?>[] {EntityType.PARROT});
		put(Movement.FLUTTER, new EntityType<?>[] {EntityType.ALLAY}, null, slot(PeacefulAbilities.ALLAY_COLLECT, Items.AMETHYST_SHARD, 10));
		put(Movement.BAT, new EntityType<?>[] {EntityType.BAT});
		put(Movement.TURTLE, new EntityType<?>[] {EntityType.TURTLE});
		put(Movement.CALM, new EntityType<?>[] {EntityType.VILLAGER, EntityType.WANDERING_TRADER});
		put(Movement.LAVA, new EntityType<?>[] {EntityType.STRIDER});
	}

	private static AbilitySlot slot(Ability ability, Item icon, int cooldown) {
		return new AbilitySlot(ability, icon, cooldown);
	}

	private static AbilitySlot slot(Ability ability, Identifier icon, int cooldown) {
		return new AbilitySlot(ability, icon, cooldown);
	}

	private MobProfiles() {
	}

	private static void put(Movement movement, EntityType<?>[] types, AbilitySlot... slots) {
		for (EntityType<?> type : types) {
			PROFILES.put(type, MobProfile.of(movement, slots));
		}
	}

	public static MobProfile of(MobEntity mob) {
		Item weapon = HELD_WEAPONS.get(mob.getType());
		if (weapon != null && !mob.getMainHandStack().isOf(weapon)) {
			return FIGHTER;
		}
		MobProfile profile = PROFILES.get(mob.getType());
		if (profile != null) {
			return profile;
		}
		return mob.getAttributes().hasAttribute(EntityAttributes.ATTACK_DAMAGE) ? FIGHTER : WALKER;
	}
}
