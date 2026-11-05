package net.neurallink.session;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import java.util.Collection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.neurallink.advancement.Achievements;
import net.neurallink.chip.ChipCard;
import net.neurallink.chip.ChipEntry;
import net.neurallink.chip.ChipLogic;
import net.neurallink.chip.ChipRegistry;
import net.neurallink.chip.ChipResult;
import net.neurallink.chip.ChipTransfer;
import net.neurallink.config.NeuralLinkConfig;
import net.neurallink.control.Control;
import net.neurallink.entity.BodyEntity;
import net.neurallink.mixin.ServerPlayNetworkHandlerAccessor;
import net.neurallink.network.InputPayload;
import net.neurallink.network.VisorEventPayload;
import net.neurallink.registry.ModAttachments;
import net.neurallink.registry.ModComponents;
import org.slf4j.Logger;

/**
 * The player becomes a spectator with the camera on the mob, so vanilla handles invulnerability, collision, pickup,
 * visibility and chunks. A {@link BodyEntity} stands in his place. Every exit goes through {@link #end}. Helpers: {@link
 * PendingConnects}, {@link SessionPackets}, {@link HostPrep}, {@link SessionRestore}, {@link ChunkTickets}.
 */
public final class SessionManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int DEATH_NAUSEA_TICKS = 60;
	/** not Float.MAX_VALUE: armor math turns it into NaN. */
	private static final float LETHAL_DAMAGE = 1000.0F;
	private static final double DRAGON_LOOK_AHEAD = 64.0;
	private static final int LOOK_LOCK_TICKS = 40;

	private static final Map<UUID, ControlSession> BY_PLAYER = new HashMap<>();
	private static final Map<UUID, ControlSession> BY_MOB = new HashMap<>();
	/** Keyed by mob, not chip; a converted mob gets a new UUID and starts ready. */
	private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();
	/** last connect/switch tick per player, one cooldown for both, outlives the session. */
	private static final Map<UUID, Integer> LAST_LINK = new HashMap<>();

	private SessionManager() {
	}

	/** Live session for admin commands; ticks since connect. */
	public record SessionView(ServerPlayerEntity player, MobEntity host, BodyEntity body, int ticks) {
	}

	/** a connect or switch waiting for its host chunk. */
	public record PendingView(UUID player, ChipEntry entry) {
	}

	public static List<SessionView> sessions(MinecraftServer server) {
		return BY_PLAYER.values().stream().map(session -> new SessionView(session.player, session.mob, session.body, server.getTicks() - session.startTick)).toList();
	}

	public static List<PendingView> pending() {
		return PendingConnects.views();
	}

	static ControlSession byPlayer(UUID player) {
		return BY_PLAYER.get(player);
	}

	/** For the per-sound hook, no copies. */
	static Collection<ControlSession> live() {
		return BY_PLAYER.values();
	}

	public static boolean isInSession(ServerPlayerEntity player) {
		return BY_PLAYER.containsKey(player.getUuid());
	}

	public static Optional<ServerPlayerEntity> operatorOf(Entity mob) {
		return operatorOf(mob.getUuid());
	}

	/** by UUID since the host may be unloaded or gone. */
	public static Optional<ServerPlayerEntity> operatorOf(UUID mobId) {
		return Optional.ofNullable(BY_MOB.get(mobId)).map(session -> session.player);
	}

	public static boolean blocksPhasing(Entity mob) {
		ControlSession session = BY_MOB.get(mob.getUuid());
		return session != null && !session.state.phasing;
	}

	/** An ability holds the mob in place (hiding silverfish, frozen creaking). */
	public static boolean isFrozen(Entity mob) {
		ControlSession session = BY_MOB.get(mob.getUuid());
		return session != null && session.state.frozen;
	}

	/** frozen host: invulnerable this session only, nothing saved. */
	public static boolean isShielded(Entity mob) {
		ControlSession session = BY_MOB.get(mob.getUuid());
		return session != null && session.state.shielded;
	}

	public static Optional<Vec3d> lookTarget(Entity mob) {
		return Optional.ofNullable(BY_MOB.get(mob.getUuid())).map(session -> mob.getPos().add(Vec3d.fromPolar(session.state.pitch, session.state.yaw).multiply(DRAGON_LOOK_AHEAD)));
	}

	public static boolean isControlled(Entity mob) {
		return isControlling(mob.getUuid());
	}

	public static boolean isControlling(UUID mobId) {
		return BY_MOB.containsKey(mobId);
	}

	public static void cancelPending(UUID chipId) {
		PendingConnects.cancelChip(chipId);
	}

	public static Optional<GameMode> savedGameMode(UUID player) {
		return Optional.ofNullable(BY_PLAYER.get(player)).map(session -> session.record.gameMode());
	}

	public static Optional<UUID> hostOf(ServerPlayerEntity player) {
		return Optional.ofNullable(BY_PLAYER.get(player.getUuid())).map(session -> session.mob.getUuid());
	}

	public static Optional<BodyEntity> bodyOf(ServerPlayerEntity player) {
		return Optional.ofNullable(BY_PLAYER.get(player.getUuid())).map(session -> session.body);
	}

	public static boolean isBody(BodyEntity body) {
		return sessionOf(body) != null;
	}

	private static ControlSession sessionOf(BodyEntity body) {
		ControlSession session = body.getOwner().map(BY_PLAYER::get).orElse(null);
		return session != null && session.body == body ? session : null;
	}

	public static ChipResult connect(ServerPlayerEntity player, UUID chipId) {
		MinecraftServer server = player.getServer();
		Optional<ChipEntry> found = ChipRegistry.get(server).find(chipId);
		if (found.isEmpty()) {
			return ChipResult.UNKNOWN_CHIP;
		}
		ChipEntry entry = found.get();
		ControlSession session = BY_PLAYER.get(player.getUuid());
		ChipResult refusal = validate(player, entry, session);
		if (refusal != null) {
			return refusal;
		}
		if (PendingConnects.has(player.getUuid())) {
			return ChipResult.ALREADY_CONNECTED;
		}
		ServerWorld world = player.getServerWorld();
		if (world.getEntity(entry.mobId()) instanceof MobEntity mob) {
			if (session != null) {
				switchTo(session, mob);
			} else {
				start(player, mob);
			}
			return ChipResult.CONNECTED;
		}
		// Host chunk unloaded: load and wait. The current host stays controlled meanwhile.
		PendingConnects.start(player, entry);
		return ChipResult.CONNECTING;
	}

	/**
	 * why the player can't connect now, or null. Also rechecked while pending; for a switch the same rules apply so nothing
	 * is bypassed.
	 */
	static ChipResult validate(ServerPlayerEntity player, ChipEntry entry, ControlSession session) {
		if (!entry.owner().equals(player.getUuid())) {
			return ChipResult.NOT_OWNER;
		}
		if (entry.lost()) {
			return ChipResult.NOT_FOUND;
		}
		ItemStack helmet = ChipLogic.wornHelmet(player);
		if (helmet.isEmpty()) {
			return ChipResult.NEED_HELMET;
		}
		if (!entry.helmetId().equals(helmet.get(ModComponents.HELMET_ID))) {
			return ChipResult.WRONG_HELMET;
		}
		if (session == null) {
			if (player.isSpectator() || !player.isAlive() || player.notInAnyWorld) {
				return ChipResult.UNAVAILABLE;
			}
		} else {
			if (entry.mobId().equals(session.mob.getUuid())) {
				return ChipResult.ALREADY_CONNECTED;
			}
			if (ChipTransfer.isSplitting(session.mob)) {
				return ChipResult.UNAVAILABLE;
			}
		}
		Integer lastLink = LAST_LINK.get(player.getUuid());
		if (lastLink != null && player.getServer().getTicks() - lastLink < NeuralLinkConfig.connectCooldownSeconds * 20) {
			return ChipResult.COOLDOWN;
		}
		if (BY_MOB.containsKey(entry.mobId())) {
			return ChipResult.BUSY;
		}
		// Signal is measured from the body, not the player at the host.
		if (!entry.world().equals(session != null ? session.body.getWorld().getRegistryKey() : player.getWorld().getRegistryKey())) {
			return ChipResult.NO_SIGNAL;
		}
		if (ChipLogic.isForbiddenDimension(entry.world())) {
			return ChipResult.FORBIDDEN_DIMENSION;
		}
		EntityType<?> type = Registries.ENTITY_TYPE.get(entry.entityType()); // from the entry, host may be unloaded
		if (ChipLogic.isDisabledBoss(type)) {
			return ChipResult.BOSSES_DISABLED;
		}
		if (ChipLogic.isBlacklisted(type)) {
			return ChipResult.FORBIDDEN_HOST; // refused up front, else the session just ends next tick
		}
		if (player.getServerWorld().getEntity(entry.mobId()) instanceof MobEntity mob && (mob.isAiDisabled() || !mob.isAlive() || ChipTransfer.isSplitting(mob))) {
			return ChipResult.UNAVAILABLE; // NoAI never ticks Control; dying dragons and splitting slimes still have entries
		}
		return null;
	}

	static void start(ServerPlayerEntity player, MobEntity mob) {
		ServerWorld world = player.getServerWorld();
		SessionRecord record = new SessionRecord(player.getUuid(), mob.getUuid(), world.getRegistryKey(),
				player.getPos(), player.getYaw(), player.getPitch(), player.interactionManager.getGameMode(), player.getAbilities().flying);
		BodyEntity body = BodyEntity.of(player);
		world.spawnEntity(body);
		SessionStore.get(player.getServer()).put(record);

		player.stopRiding();
		player.closeHandledScreen();
		player.clearActiveItem(); // no release check in session so a drawn bow fires on exit
		HostPrep.prepare(mob);

		ControlSession session = new ControlSession(player, mob, body, record);
		link(player);
		BY_PLAYER.put(player.getUuid(), session);
		attachHost(session);
		double distance = Math.sqrt(player.squaredDistanceTo(mob));
		BodyAggro.attract(session); // while still a target, mobs drop spectators at once
		player.changeGameMode(GameMode.SPECTATOR);
		ChunkTickets.keepLoaded(world, body.getBlockPos()); // the camera moves the player's ticket off the body at once
		player.setCameraEntity(mob);
		SessionPackets.sendState(player, true, mob.getId());
		Achievements.onConnected(player, mob, distance, ChipCard.signal((int) distance, mob.getWorld(), mob.getY()));
	}

	private static void attachHost(ControlSession session) {
		MobEntity mob = session.mob;
		mob.setAttached(ModAttachments.ORIGINAL_NO_GRAVITY, session.originalNoGravity);
		long[] cooldowns = COOLDOWNS.get(mob.getUuid());
		if (cooldowns != null) {
			session.state.restoreCooldowns(cooldowns);
		}
		BY_MOB.put(mob.getUuid(), session);
	}

	/** Moves the session to another host; the old one is released as on exit and keeps its cooldowns. */
	static void switchTo(ControlSession session, MobEntity next) {
		ServerPlayerEntity player = session.player;
		MobEntity previous = session.mob;
		BY_MOB.remove(previous.getUuid());
		saveCooldowns(session);
		try {
			Control.sessionEnded(player, previous, session.state);
		} catch (RuntimeException exception) {
			LOGGER.error("An ability of {} failed to end on a host switch", previous.getType(), exception); // the switch must still finish
		}
		HostPrep.release(session);

		HostPrep.prepare(next);
		session.attach(next);
		session.state.yaw = next.getHeadYaw(); // head look for the camera teleport, not body turn
		session.state.pitch = next.getPitch();
		attachHost(session);
		int now = link(player);
		session.lookLockUntil = now + LOOK_LOCK_TICKS;
		session.record = session.record.withMob(next.getUuid());
		SessionStore.get(player.getServer()).put(session.record);
		player.setYaw(next.getHeadYaw()); // camera teleport carries the player's own look
		player.setPitch(next.getPitch());
		player.setCameraEntity(next);
		ChunkTickets.keepLoaded((ServerWorld) next.getWorld(), next.getBlockPos());
		SessionPackets.sendState(player, true, next.getId());
		SessionPackets.sendEvent(player, VisorEventPayload.Kind.HOST_SWITCH);
		double distance = Math.sqrt(session.body.squaredDistanceTo(next));
		Achievements.onConnected(player, next, distance, ChipCard.signal((int) distance, next.getWorld(), next.getY()));
	}

	/** starts the shared cooldown. Survives a leave so rejoining can't skip it. */
	private static int link(ServerPlayerEntity player) {
		int now = player.getServer().getTicks();
		LAST_LINK.values().removeIf(tick -> now - tick >= NeuralLinkConfig.MAX_CONNECT_COOLDOWN * 20);
		LAST_LINK.put(player.getUuid(), now);
		return now;
	}

	public static void tick(MinecraftServer server) {
		PendingConnects.tick(server);
		for (ControlSession session : new ArrayList<>(BY_PLAYER.values())) {
			session.state.tickScheduled();
			if (BY_PLAYER.get(session.player.getUuid()) == session) {
				tickSession(session, server.getTicks());
			}
			if (BY_PLAYER.get(session.player.getUuid()) == session) {
				SessionPackets.sendHud(session, server.getTicks());
				SessionPackets.sendZone(session, server.getTicks());
				Achievements.tickSession(session.player, session.mob, server.getTicks());
			}
		}
	}

	private static void tickSession(ControlSession session, int ticks) {
		ServerPlayerEntity player = session.player;
		if (ChipTransfer.isSplitting(session.mob)) {
			return; // the slime still lies dead, offspring spawn on removal
		}
		if (session.mob.isRemoved() || !session.mob.isAlive()) {
			end(session, session.mob.isDead() ? EndReason.MOB_DIED : EndReason.MOB_LOST);
			return;
		}
		if (session.body.isRemoved()) {
			end(session, EndReason.ABORTED);
			return;
		}
		if (ChipLogic.wornHelmet(player).isEmpty()) {
			end(session, EndReason.HELMET_REMOVED);
			return;
		}
		ChipResult forbidden = ChipLogic.forbiddenHost(session.mob);
		if (forbidden != null) { // config changed mid-session, new rule applies at once
			end(session, EndReason.ABORTED);
			forbidden.notify(player);
			return;
		}
		if (!player.isSpectator()) {
			end(session, EndReason.ABORTED); // game mode changed by command
			return;
		}
		if (player.getCameraEntity() != session.mob) {
			player.setCameraEntity(session.mob); // a forged packet can request another camera
		}
		if (player.getWorld() != session.mob.getWorld()) {
			end(session, EndReason.ABORTED); // an earlier pearl moves the player away
			return;
		}
		// A host inside the zone gets pushed out; the border holds it after that.
		Optional<SpawnZone.Box> zone = SessionPackets.zoneOf(session, ticks);
		SpawnZone.Push push = zone.map(box -> SpawnZone.push(session.mob, box)).orElse(SpawnZone.Push.OUTSIDE);
		if (push == SpawnZone.Push.STUCK) {
			end(session, EndReason.ABORTED); // walled in rock: better let go than leave it in the zone
			ChipResult.SPAWN_PROTECTED.notify(player);
			return;
		}
		if (push == SpawnZone.Push.MOVED) {
			// the spectator follows the camera, big jumps without a teleport are "moved too quickly".
			player.requestTeleport(session.mob.getX(), session.mob.getY(), session.mob.getZ());
		}
		if (session.mob instanceof AbstractHorseEntity horse && horse.isEatingGrass()) {
			horse.setEatingGrass(false); // set without AI, and a grazing horse skips the Control tick
		}
		if (session.mob.hasPassengers()) {
			for (Entity passenger : session.mob.getPassengerList()) {
				if (passenger instanceof PlayerEntity) {
					passenger.stopRiding(); // a rider would steer the host
				}
			}
		}
		if (ticks % 20 == 0) {
			ChunkTickets.keepLoaded((ServerWorld) session.mob.getWorld(), session.mob.getBlockPos());
			ChunkTickets.keepLoaded((ServerWorld) session.body.getWorld(), session.body.getBlockPos());
			BodyAggro.attract(session);
			if (!session.body.getPos().equals(session.record.pos())) {
				session.record = session.record.withPos(session.body.getPos()); // the body moves, crash rollback must find it
				SessionStore.get(player.getServer()).put(session.record);
			}
		}
	}

	/** Hit on host or body; the operator feels it at once. */
	public static void onDamaged(LivingEntity entity, DamageSource source) {
		ControlSession host = BY_MOB.get(entity.getUuid());
		if (host != null) {
			SessionPackets.sendEvent(host.player, VisorEventPayload.Kind.MOB_HIT);
		} else if (entity instanceof BodyEntity body) {
			ControlSession session = sessionOf(body);
			if (session != null) {
				SessionPackets.sendEvent(session.player, VisorEventPayload.Kind.BODY_HIT);
				if (source.getAttacker() == session.mob) {
					Achievements.grant(session.player, "fight_club");
				}
			}
		}
	}

	public static void onInput(ServerPlayerEntity player, InputPayload input) {
		ControlSession session = BY_PLAYER.get(player.getUuid());
		if (session == null || !Float.isFinite(input.forward()) || !Float.isFinite(input.sideways())
				|| !Float.isFinite(input.yaw()) || !Float.isFinite(input.pitch())) {
			return;
		}
		float yaw = input.yaw();
		float pitch = input.pitch();
		// after a switch the client sends the old look until the camera teleport arrives.
		if (player.getServer().getTicks() < session.lookLockUntil && ((ServerPlayNetworkHandlerAccessor) player.networkHandler).neurallink$getRequestedTeleportPos() != null) {
			yaw = session.state.yaw;
			pitch = session.state.pitch;
		}
		if (session.state.readInput(input.forward(), input.sideways(), input.jump(), input.sneak(), yaw, pitch)) {
			player.updateLastActionTime(); // only a changed input proves someone's there
		}
	}

	public static void onSlotInput(ServerPlayerEntity player, int slot, boolean pressed) {
		ControlSession session = BY_PLAYER.get(player.getUuid());
		if (session == null) {
			return;
		}
		player.updateLastActionTime();
		if (session.mob.isAlive() && !ChipTransfer.isSplitting(session.mob)) {
			Control.onSlotInput(player, session.mob, session.state, slot, pressed);
		}
	}

	/** Replaces the AI in the mob's tick. Normal travel so gravity, collisions and water work. */
	public static boolean applyControl(MobEntity mob) {
		ControlSession session = BY_MOB.get(mob.getUuid());
		if (session == null || session.mob != mob) {
			return false;
		}
		Control.tick(mob, session.state);
		return true;
	}

	public static void exit(ServerPlayerEntity player, EndReason reason) {
		ControlSession session = BY_PLAYER.get(player.getUuid());
		if (session != null) {
			end(session, reason);
		}
	}

	/** admin kick: back to the body, pending connect dropped. False if neither. */
	public static boolean kick(ServerPlayerEntity player) {
		boolean waiting = PendingConnects.remove(player.getUuid());
		ControlSession session = BY_PLAYER.get(player.getUuid());
		if (session == null && !waiting) {
			return false;
		}
		if (session != null) {
			end(session, EndReason.KICKED);
		}
		ChipResult.ADMIN_KICKED.notify(player); // also closes a waiting connect's search screen
		return true;
	}

	public static void onMobDied(LivingEntity mob) {
		ControlSession session = BY_MOB.get(mob.getUuid());
		if (session != null) {
			end(session, EndReason.MOB_DIED);
		}
	}

	/**
	 * Host removed without dying: end now, or vanilla hands the camera back first and the client shows the body before the
	 * signal loss.
	 */
	public static void onHostRemoved(MobEntity mob) {
		ControlSession session = BY_MOB.get(mob.getUuid());
		if (session != null && session.mob == mob) {
			end(session, EndReason.MOB_LOST);
		}
	}

	/** conversion or split makes a new entity, the session follows. */
	public static void onMobReplaced(MobEntity previous, MobEntity next) {
		ControlSession session = BY_MOB.remove(previous.getUuid());
		if (session == null) {
			return;
		}
		BY_MOB.put(next.getUuid(), session);
		session.mob = next;
		session.originalNoGravity = next.hasNoGravity();
		next.setAttached(ModAttachments.ORIGINAL_NO_GRAVITY, session.originalNoGravity);
		session.state.resetFor(next);
		session.record = session.record.withMob(next.getUuid());
		SessionStore.get(session.player.getServer()).put(session.record);
		session.player.setCameraEntity(next);
		SessionPackets.sendState(session.player, true, next.getId());
		SessionPackets.sendEvent(session.player, VisorEventPayload.Kind.HOST_SWITCH);
	}

	/** A portal copy keeps UUID and flags, so release happens on the copy. */
	public static void onMobMoved(Entity original, Entity moved) {
		ControlSession session = BY_MOB.get(original.getUuid());
		if (session != null && session.mob == original && moved instanceof MobEntity mob) {
			session.mob = mob;
			end(session, EndReason.MOB_LOST);
		}
	}

	public static void onBodyDied(BodyEntity body, DamageSource source) {
		ControlSession session = sessionOf(body);
		if (session != null) {
			endKilled(session, source);
		}
	}

	/** before vanilla onDeath so the player can be restored and die at the body. */
	public static boolean onOperatorDying(ServerPlayerEntity player, DamageSource source) {
		ControlSession session = BY_PLAYER.get(player.getUuid());
		if (session == null) {
			return false;
		}
		player.timeUntilRegen = 0; // /kill left lastDamageTaken at Float.MAX_VALUE, which would swallow the lethal hit
		endKilled(session, source);
		return true;
	}

	/** Body died. The lethal hit goes to the restored player so it comes after end. */
	private static void endKilled(ControlSession session, DamageSource killer) {
		end(session, EndReason.BODY_DIED);
		ServerPlayerEntity player = session.player;
		player.setHealth(1.0F); // hit lands on the player so totem, message and drops work
		if (!player.damage(player.getServerWorld(), killer, LETHAL_DAMAGE)) {
			// Real source refused (pvp, gamerule, Peaceful). Not genericKill, it skips the totem.
			player.damage(player.getServerWorld(), player.getDamageSources().generic(), LETHAL_DAMAGE);
		}
	}

	private static void end(ControlSession session, EndReason reason) {
		PendingConnects.remove(session.player.getUuid()); // a pending switch must not turn into a connect from the body
		BY_PLAYER.remove(session.player.getUuid());
		BY_MOB.remove(session.mob.getUuid());
		ServerPlayerEntity player = session.player;
		saveCooldowns(session);
		if (reason == EndReason.MOB_DIED || reason == EndReason.MOB_LOST) {
			// before camera/state packets so the loss shows the host's last frame.
			SessionPackets.sendEvent(player, VisorEventPayload.Kind.SIGNAL_LOST);
		}

		try {
			Control.sessionEnded(session.player, session.mob, session.state);
			HostPrep.release(session);
			Achievements.onSessionEnd(player);
			if (reason == EndReason.MOB_DIED || reason == EndReason.MOB_LOST) {
				Achievements.onSignalLost(player);
			} else if (reason == EndReason.BODY_DIED) {
				Achievements.grant(player, "behind_you");
			}
		} finally { // a throwing ability must not leave him a spectator; record stays till he's back
			player.getHungerManager().readNbt(session.hunger);
			if (!session.body.isDead()) { // not isAlive, a removed body still has wounds
				player.setHealth(session.body.getHealth());
				player.setAbsorptionAmount(session.body.getAbsorptionAmount());
				player.setAir(session.body.getAir());
				player.setFrozenTicks(session.body.getFrozenTicks());
			}
			SessionRestore.restore(player, SessionRestore.returnPoint(session));
			if (!session.body.isDead()) {
				player.setFireTicks(session.body.getFireTicks()); // after restore, spectator fire is clamped to one tick
				player.fallDistance = session.body.fallDistance; // the body may be falling, landing still counts
			}
			SessionStore.get(player.getServer()).remove(player.getUuid());
		}
		if (reason != EndReason.BODY_DIED && reason != EndReason.DISCONNECT && reason != EndReason.SERVER_STOP) {
			BodyAggro.release(session);
		}
		session.body.discard();

		if (reason == EndReason.MOB_DIED) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, DEATH_NAUSEA_TICKS));
		}
	}

	private static void saveCooldowns(ControlSession session) {
		int now = session.player.getServer().getTicks();
		COOLDOWNS.put(session.mob.getUuid(), session.state.cooldowns());
		COOLDOWNS.values().removeIf(saved -> Arrays.stream(saved).allMatch(readyAt -> readyAt <= now));
	}

	public static void onDisconnect(ServerPlayerEntity player) {
		PendingConnects.remove(player.getUuid());
		exit(player, EndReason.DISCONNECT);
		Achievements.forget(player.getUuid());
	}

	/** Rolls back a session that outlived a crash from the saved record. */
	public static void onJoin(ServerPlayerEntity player) {
		ControlSession live = BY_PLAYER.get(player.getUuid());
		if (live != null) {
			if (live.player != player) { // reconfiguration swapped the player object mid-session
				SessionRecord record = SessionRestore.returnPoint(live);
				end(live, EndReason.DISCONNECT);
				SessionRestore.restore(player, record);
			}
			return;
		}
		SessionRestore.rollBack(player);
	}

	/** a hard crash saved the host with session flags; its mark undoes them on load. */
	public static void onEntityLoaded(Entity entity, ServerWorld world) {
		SessionRestore.onEntityLoaded(entity);
	}

	public static void onServerStopping(MinecraftServer server) {
		for (ControlSession session : new ArrayList<>(BY_PLAYER.values())) {
			try {
				end(session, EndReason.SERVER_STOP);
			} catch (RuntimeException exception) {
				LOGGER.error("Could not end the session of {}", session.player.getName().getString(), exception); // others still go home
			}
		}
	}

	public static void clear() {
		BY_PLAYER.clear();
		BY_MOB.clear();
		PendingConnects.clear();
		COOLDOWNS.clear();
		LAST_LINK.clear();
		ChipTransfer.clearSplits();
		Achievements.clear();
	}
}
