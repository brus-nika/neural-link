package net.neurallink.chip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.server.world.ServerWorld;
import net.neurallink.advancement.Achievements;
import net.neurallink.registry.ModAttachments;
import net.neurallink.session.SessionManager;

/** Chip fate along with its mob: death, load, conversion, slime split. */
public final class ChipTransfer {
	/** slimes that died above size 1; offspring appear 20 ticks after death. */
	private static final Map<UUID, PendingSplit> SPLITTING = new HashMap<>();

	private record PendingSplit(SlimeEntity slime, List<MobEntity> children) {
	}

	private ChipTransfer() {
	}

	/** An exploded creeper counts as a death. */
	public static void onEntityUnloaded(Entity entity, ServerWorld world) {
		UUID chipId = entity.getAttached(ModAttachments.CHIP);
		if (chipId == null) {
			return;
		}
		if (entity instanceof CreeperEntity creeper && (creeper.isIgnited() || creeper.getFuseSpeed() > 0) && entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()) {
			SessionManager.operatorOf(creeper).ifPresent(operator -> Achievements.onCreeperBlown(operator, creeper));
			destroy(creeper, chipId);
		} else if (entity instanceof EnderDragonEntity dragon && entity.getRemovalReason() == Entity.RemovalReason.KILLED) {
			destroy(dragon, chipId); // the dragon is removed as KILLED and never reaches onDeath
		} else {
			if (entity instanceof MobEntity mob && entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy() && !isSplitting(mob)) {
				SessionManager.onHostRemoved(mob);
			}
			ChipRegistry.get(world.getServer()).onMobUnloaded(entity, chipId, world);
		}
	}

	/** a mark with no entry: disabled while unloaded. */
	public static void onEntityLoaded(Entity entity, ServerWorld world) {
		UUID chipId = entity.getAttached(ModAttachments.CHIP);
		if (chipId == null) {
			return;
		}
		ChipRegistry registry = ChipRegistry.get(world.getServer());
		Optional<ChipEntry> entry = registry.find(chipId);
		if (entry.isEmpty()) {
			entity.removeAttached(ModAttachments.CHIP);
		} else if (entry.get().lost() && entity instanceof MobEntity mob) {
			registry.put(ChipRegistry.withMob(entry.get(), mob, world)); // a bee back from a hive: same chip, new UUID
		}
	}

	public static void onMobDied(LivingEntity entity) {
		UUID chipId = entity.getAttached(ModAttachments.CHIP);
		if (chipId == null) {
			return;
		}
		if (entity instanceof SlimeEntity slime && slime.getSize() > 1) {
			SPLITTING.put(slime.getUuid(), new PendingSplit(slime, new ArrayList<>()));
			return;
		}
		destroy(entity, chipId);
	}

	private static void destroy(LivingEntity mob, UUID chipId) {
		SessionManager.onMobDied(mob);
		ChipRegistry.get(mob.getServer()).remove(chipId);
	}

	public static boolean isSplitting(MobEntity mob) {
		return SPLITTING.containsKey(mob.getUuid());
	}

	/** A conversion or slime offspring; a random offspring is picked once all appear. */
	public static void onMobConverted(MobEntity previous, MobEntity converted) {
		PendingSplit split = SPLITTING.get(previous.getUuid());
		if (split != null) {
			split.children().add(converted);
		} else {
			SessionManager.operatorOf(previous).ifPresent(operator -> Achievements.grant(operator, "evolution"));
			transfer(previous, converted);
		}
	}

	public static void tickSplits() {
		for (Iterator<PendingSplit> it = SPLITTING.values().iterator(); it.hasNext();) {
			PendingSplit split = it.next();
			if (!split.slime().isRemoved()) {
				continue;
			}
			it.remove();
			if (!split.slime().getRemovalReason().shouldDestroy()) {
				continue; // unloaded mid-death: the corpse splits on reload via onMobConverted
			}
			List<MobEntity> children = split.children();
			if (children.isEmpty()) {
				destroy(split.slime(), split.slime().getAttached(ModAttachments.CHIP));
			} else {
				MobEntity child = children.get(split.slime().getRandom().nextInt(children.size()));
				SessionManager.operatorOf(split.slime()).filter(operator -> child instanceof SlimeEntity slime && slime.getSize() == 1)
						.ifPresent(operator -> Achievements.grant(operator, "last_drop"));
				transfer(split.slime(), child);
			}
		}
	}

	public static void clearSplits() {
		SPLITTING.clear();
	}

	private static void transfer(MobEntity previous, MobEntity next) {
		UUID chipId = previous.getAttached(ModAttachments.CHIP);
		if (chipId == null) {
			return;
		}
		if (ChipLogic.isBlacklisted(next.getType()) || ChipLogic.isDisabledBoss(next.getType())) {
			destroy(previous, chipId);
			return;
		}
		previous.removeAttached(ModAttachments.CHIP);
		next.setAttached(ModAttachments.CHIP, chipId);
		ChipRegistry registry = ChipRegistry.get(next.getServer());
		registry.find(chipId).ifPresent(entry -> registry.put(ChipRegistry.withMob(entry, next, (ServerWorld) next.getWorld())));
		SessionManager.onMobReplaced(previous, next);
	}
}
