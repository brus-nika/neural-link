package net.neurallink.session;

import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.neurallink.advancement.Achievements;
import net.neurallink.registry.ModAttachments;

/** Brings the player back at session end or, after a crash, from the record. Host flags revert via its mark on load. */
final class SessionRestore {
	private SessionRestore() {
	}

	/** the body's current spot, or the last stored one if it's gone. */
	static SessionRecord returnPoint(ControlSession session) {
		return session.body.isRemoved() ? session.record : session.record.withPos(session.body.getPos());
	}

	static void restore(ServerPlayerEntity player, SessionRecord record) {
		player.setCameraEntity(player);
		player.changeGameMode(record.gameMode());
		player.getAbilities().flying = record.flying() && player.getAbilities().allowFlying; // spectator turned it on, creative won't turn it off
		player.sendAbilitiesUpdate();
		ServerWorld world = player.getServer().getWorld(record.world());
		if (world == null) {
			world = player.getServer().getOverworld();
		}
		Vec3d pos = record.pos();
		player.teleport(world, pos.x, pos.y, pos.z, Set.of(), record.yaw(), record.pitch(), false);
		player.setVelocity(Vec3d.ZERO);
		player.fallDistance = 0.0F;
		SessionPackets.sendState(player, false, -1);
	}

	/** rolls back a crash-surviving session from the saved record. */
	static void rollBack(ServerPlayerEntity player) {
		SessionStore store = SessionStore.get(player.getServer());
		store.find(player.getUuid()).ifPresent(record -> {
			store.remove(player.getUuid());
			if (player.isSpectator()) { // an autosave can outlive the session, then the record is stale
				restore(player, record);
			}
			Achievements.onSessionEnd(player);
		});
	}

	/** A crash saved the host with session flags; its mark undoes them on load, join order aside. */
	static void onEntityLoaded(Entity entity) {
		Boolean noGravity = entity.getAttached(ModAttachments.ORIGINAL_NO_GRAVITY);
		if (noGravity != null && !SessionManager.isControlled(entity)) {
			entity.setNoGravity(noGravity);
			entity.removeAttached(ModAttachments.ORIGINAL_NO_GRAVITY);
			if (entity instanceof EnderDragonEntity dragon && dragon.getFight() != null && dragon.getPhaseManager().getCurrent().getType() == PhaseType.HOVER) {
				dragon.getPhaseManager().setPhase(PhaseType.HOLDING_PATTERN); // saved mid-session, a fight dragon would hang forever
			}
		}
	}
}
