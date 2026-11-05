package net.neurallink.session;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.neurallink.chip.ChipEntry;
import net.neurallink.chip.ChipResult;

/**
 * Connects and switches waiting for the host chunk. Rules are rechecked every tick; the current host stays controlled until
 * the new one appears.
 */
final class PendingConnects {
	private static final int TIMEOUT_TICKS = 100;
	private static final Map<UUID, Waiting> PENDING = new HashMap<>();

	private record Waiting(ChipEntry entry, int startTick) {
	}

	private PendingConnects() {
	}

	static boolean has(UUID player) {
		return PENDING.containsKey(player);
	}

	static void start(ServerPlayerEntity player, ChipEntry entry) {
		PENDING.put(player.getUuid(), new Waiting(entry, player.getServer().getTicks()));
		ChunkTickets.keepLoaded(player.getServerWorld(), entry.pos());
	}

	/** true if one was waiting. */
	static boolean remove(UUID player) {
		return PENDING.remove(player) != null;
	}

	static void cancelChip(UUID chipId) {
		PENDING.values().removeIf(waiting -> waiting.entry().chipId().equals(chipId));
	}

	static List<SessionManager.PendingView> views() {
		return PENDING.entrySet().stream().map(waiting -> new SessionManager.PendingView(waiting.getKey(), waiting.getValue().entry())).toList();
	}

	static void clear() {
		PENDING.clear();
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Waiting>> it = PENDING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Waiting> waiting = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(waiting.getKey());
			ChipEntry entry = waiting.getValue().entry();
			if (player == null) {
				it.remove();
				continue;
			}
			ControlSession session = SessionManager.byPlayer(player.getUuid());
			ChipResult refusal = SessionManager.validate(player, entry, session);
			if (refusal != null) {
				it.remove();
				refusal.notify(player);
				continue;
			}
			ServerWorld world = player.getServerWorld();
			if (world.getEntity(entry.mobId()) instanceof MobEntity mob) { // validate already refused a dead one
				it.remove();
				if (session != null) {
					SessionManager.switchTo(session, mob);
				} else {
					SessionManager.start(player, mob);
				}
				ChipResult.CONNECTED.notify(player);
			} else if (server.getTicks() - waiting.getValue().startTick() > TIMEOUT_TICKS) {
				it.remove();
				ChipResult.NOT_FOUND.notify(player);
			} else if (server.getTicks() % 20 == 0) {
				ChunkTickets.keepLoaded(world, entry.pos());
			}
		}
	}
}
