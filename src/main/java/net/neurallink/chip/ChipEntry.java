package net.neurallink.chip;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.neurallink.item.ChipBinding;

/** An inserted chip, including hosts in unloaded chunks. name is the name tag only; favorite pins the card on top. */
public record ChipEntry(UUID chipId, UUID helmetId, UUID owner, UUID mobId, Identifier entityType, String name,
		float health, float maxHealth, RegistryKey<World> world, BlockPos pos, boolean lost, Identifier held, boolean favorite) {
	public static final Identifier NOTHING = Identifier.ofVanilla("air");
	public static final Codec<ChipEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Uuids.CODEC.fieldOf("chip").forGetter(ChipEntry::chipId),
			Uuids.CODEC.fieldOf("helmet").forGetter(ChipEntry::helmetId),
			Uuids.CODEC.fieldOf("owner").forGetter(ChipEntry::owner),
			Uuids.CODEC.fieldOf("mob").forGetter(ChipEntry::mobId),
			Identifier.CODEC.fieldOf("type").forGetter(ChipEntry::entityType),
			Codec.STRING.fieldOf("name").forGetter(ChipEntry::name),
			Codec.FLOAT.fieldOf("health").forGetter(ChipEntry::health),
			Codec.FLOAT.fieldOf("max_health").forGetter(ChipEntry::maxHealth),
			World.CODEC.fieldOf("world").forGetter(ChipEntry::world),
			BlockPos.CODEC.fieldOf("pos").forGetter(ChipEntry::pos),
			Codec.BOOL.optionalFieldOf("lost", false).forGetter(ChipEntry::lost),
			Identifier.CODEC.optionalFieldOf("held", NOTHING).forGetter(ChipEntry::held),
			Codec.BOOL.optionalFieldOf("favorite", false).forGetter(ChipEntry::favorite)
	).apply(instance, ChipEntry::new));

	static ChipEntry of(ChipBinding binding, LivingEntity mob, ServerWorld world) {
		return new ChipEntry(binding.chipId(), binding.helmetId(), binding.owner(), mob.getUuid(), Registries.ENTITY_TYPE.getId(mob.getType()),
				mob.hasCustomName() ? mob.getCustomName().getString() : "", mob.getHealth(), mob.getMaxHealth(), world.getRegistryKey(), mob.getBlockPos(), false,
				Registries.ITEM.getId(mob.getMainHandStack().getItem()), false);
	}

	ChipEntry asLost() {
		return new ChipEntry(chipId, helmetId, owner, mobId, entityType, name, health, maxHealth, world, pos, true, held, favorite);
	}

	ChipEntry withOwner(UUID newOwner) {
		return new ChipEntry(chipId, helmetId, newOwner, mobId, entityType, name, health, maxHealth, world, pos, lost, held, favorite);
	}

	ChipEntry withFavorite(boolean pinned) {
		return new ChipEntry(chipId, helmetId, owner, mobId, entityType, name, health, maxHealth, world, pos, lost, held, pinned);
	}
}
