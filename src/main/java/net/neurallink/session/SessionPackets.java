package net.neurallink.session;

import java.util.Optional;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neurallink.chip.ChipCard;
import net.neurallink.control.Control;
import net.neurallink.network.HudStatePayload;
import net.neurallink.network.SessionStatePayload;
import net.neurallink.network.SpawnZonePayload;
import net.neurallink.network.VisorEventPayload;

/** Server-to-operator updates: state, visor events, HUD, spawn zone. */
final class SessionPackets {
	private SessionPackets() {
	}

	static void sendState(ServerPlayerEntity player, boolean active, int mobId) {
		if (ServerPlayNetworking.canSend(player, SessionStatePayload.ID)) {
			ServerPlayNetworking.send(player, new SessionStatePayload(active, mobId));
		}
	}

	static void sendEvent(ServerPlayerEntity player, VisorEventPayload.Kind kind) {
		if (ServerPlayNetworking.canSend(player, VisorEventPayload.ID)) {
			ServerPlayNetworking.send(player, new VisorEventPayload(kind, -1));
		}
	}

	static void sendHud(ControlSession session, int ticks) {
		MobEntity mob = session.mob;
		int distance = (int) Math.sqrt(session.body.squaredDistanceTo(mob));
		HudStatePayload hud = new HudStatePayload(mob.getHealth(), mob.getMaxHealth(), ChipCard.signal(distance, mob.getWorld(), mob.getY()), Control.slotViews(mob, session.state, ticks),
				Control.vertical(mob, session.state));
		if (!hud.equals(session.lastHud) && ServerPlayNetworking.canSend(session.player, HudStatePayload.ID)) {
			session.lastHud = hud;
			ServerPlayNetworking.send(session.player, hud);
		}
	}

	/** asked once per tick, both the session tick and payload need it. */
	static Optional<SpawnZone.Box> zoneOf(ControlSession session, int ticks) {
		if (session.zoneTick != ticks) {
			session.zone = SpawnZone.forOperator(session.player, session.mob.getWorld());
			session.zoneTick = ticks;
		}
		return session.zone;
	}

	/** Only on change; config, ops or world can move the zone anytime. */
	static void sendZone(ControlSession session, int ticks) {
		SpawnZonePayload zone = zoneOf(session, ticks)
				.map(box -> new SpawnZonePayload(true, box.minX(), box.minZ(), box.maxX(), box.maxZ()))
				.orElse(SpawnZonePayload.NONE);
		if (!zone.equals(session.lastZone) && ServerPlayNetworking.canSend(session.player, SpawnZonePayload.ID)) {
			session.lastZone = zone;
			ServerPlayNetworking.send(session.player, zone);
		}
	}
}
