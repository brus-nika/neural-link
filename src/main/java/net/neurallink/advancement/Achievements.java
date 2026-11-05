package net.neurallink.advancement;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.AxolotlEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.neurallink.NeuralLink;
import net.neurallink.registry.ModEntities;
import net.neurallink.session.SessionManager;

public final class Achievements {
	public static final EventCriterion EVENT = Criteria.register(NeuralLink.id("event").toString(), new EventCriterion());

	private static final double LONG_SIGNAL_DISTANCE = 4096.0;
	private static final int ALL_TYPES = 30;
	private static final long LOST_WINDOW_TICKS = 200;
	private static final long HOUR_TICKS = 72000;
	private static final int MAJOR_TOM_Y = 300;
	private static final int SMOKER_RANGE = 2;
	private static final int SLOW_CHECK_TICKS = 20;

	private static final Map<UUID, Integer> LOST_AT = new HashMap<>();

	private Achievements() {
	}

	public static void register() {
	}

	public static void grant(ServerPlayerEntity player, String event) {
		EVENT.trigger(player, event);
	}

	public static void onConnected(ServerPlayerEntity player, MobEntity mob, double distance, int signal) {
		grant(player, "connected");
		EntityType<?> type = mob.getType();
		if (OperatorStats.get(player.getServer()).addType(player.getUuid(), Registries.ENTITY_TYPE.getId(type)) >= ALL_TYPES) {
			grant(player, "catch_em_all");
		}
		if (distance >= LONG_SIGNAL_DISTANCE) {
			grant(player, "long_signal");
		}
		if (signal == 0) {
			grant(player, "can_you_hear_me");
		}
		if (type.isIn(ModEntities.BOSSES)) {
			grant(player, "overlord");
		}
		Integer lostAt = LOST_AT.remove(player.getUuid());
		if (lostAt != null && player.getServer().getTicks() - lostAt <= LOST_WINDOW_TICKS) {
			grant(player, "ill_be_back");
		}
		if (mob instanceof RabbitEntity rabbit && rabbit.getVariant() == RabbitEntity.RabbitType.WHITE) {
			grant(player, "white_rabbit");
		}
		if (type == EntityType.SHULKER) {
			grant(player, "ghost_shell");
		}
		if (type == EntityType.SHEEP && mob.getWorld().isNight()) {
			grant(player, "electric_sheep");
		}
		if (type == EntityType.CHICKEN && mob.getPassengerList().stream().anyMatch(rider -> rider instanceof ZombieEntity zombie && zombie.isBaby())) {
			grant(player, "chicken_jockey");
		}
		if (mob instanceof AxolotlEntity axolotl && axolotl.getVariant() == AxolotlEntity.Variant.BLUE) {
			grant(player, "i_see_you");
		}
		if (type == EntityType.PIG && mob.hasCustomName()) {
			String name = mob.getCustomName().getString();
			if (name.equals("Gertrude") || name.equals("Гертруда")) {
				grant(player, "gertrude");
			}
		}
	}

	public static void onSignalLost(ServerPlayerEntity player) {
		LOST_AT.put(player.getUuid(), player.getServer().getTicks());
	}

	public static void forget(UUID player) {
		LOST_AT.remove(player);
	}

	public static void onCreeperBlown(ServerPlayerEntity player, Entity creeper) {
		grant(player, "kamikaze");
		if (creeper.getY() < 0.0) {
			grant(player, "aw_man");
		}
	}

	public static void onDeath(LivingEntity victim, DamageSource source) {
		if (source.getSource() instanceof FireballEntity && source.getAttacker() instanceof GhastEntity ghast) {
			SessionManager.operatorOf(ghast).ifPresent(operator -> grant(operator, "return_address"));
		}
	}

	public static void onSessionEnd(ServerPlayerEntity player) {
		OperatorStats.get(player.getServer()).resetSession(player.getUuid());
	}

	public static void tickSession(ServerPlayerEntity player, MobEntity mob, int ticks) {
		if (ticks % SLOW_CHECK_TICKS != 0) {
			return;
		}
		if (OperatorStats.get(player.getServer()).addSessionTicks(player.getUuid(), SLOW_CHECK_TICKS) >= HOUR_TICKS) {
			grant(player, "touch_grass");
		}
		if (mob.getType() == EntityType.PHANTOM && mob.getY() > MAJOR_TOM_Y) {
			grant(player, "major_tom");
		}
		if (mob.getType() == EntityType.SILVERFISH && nearSmoker(mob)) {
			grant(player, "ratatouille");
		}
		if (mob instanceof Leashable leashable && leashable.getLeashHolder() instanceof PlayerEntity holder && holder != player) {
			grant(player, "stockholm");
		}
	}

	private static boolean nearSmoker(MobEntity mob) {
		BlockPos center = mob.getBlockPos();
		for (BlockPos pos : BlockPos.iterate(center.add(-SMOKER_RANGE, -SMOKER_RANGE, -SMOKER_RANGE), center.add(SMOKER_RANGE, SMOKER_RANGE, SMOKER_RANGE))) {
			if (mob.getWorld().getBlockState(pos).isOf(Blocks.SMOKER)) {
				return true;
			}
		}
		return false;
	}

	public static void clear() {
		LOST_AT.clear();
	}
}
