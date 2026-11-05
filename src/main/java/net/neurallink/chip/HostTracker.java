package net.neurallink.chip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.neurallink.config.NeuralLinkConfig;
import net.neurallink.network.HostsPayload;
import net.neurallink.session.SessionManager;

/** tells a visor operator which hosts are loaded, only on change. */
public final class HostTracker {
	private static final HostsPayload EMPTY = new HostsPayload(List.of(), 0, 0, List.of());
	private static final Map<UUID, HostsPayload> LAST = new HashMap<>();

	private HostTracker() {
	}

	public static void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			HostsPayload hosts = hostsFor(player);
			if (!hosts.equals(LAST.getOrDefault(player.getUuid(), EMPTY))) {
				LAST.put(player.getUuid(), hosts);
				if (ServerPlayNetworking.canSend(player, HostsPayload.ID)) {
					ServerPlayNetworking.send(player, hosts);
				}
			}
		}
	}

	public static HostsPayload hostsFor(ServerPlayerEntity player) {
		ItemStack helmet = ChipLogic.wornHelmet(player);
		if (helmet.isEmpty()) {
			return EMPTY;
		}
		ServerWorld world = player.getServerWorld();
		List<Integer> ids = new ArrayList<>();
		List<HostsPayload.Marker> markers = new ArrayList<>();
		int active = 0;
		for (ChipEntry entry : ChipRegistry.forOperator(player)) {
			if (!entry.lost()) {
				active++;
			}
			Entity mob = entry.world().equals(world.getRegistryKey()) && !entry.lost() ? world.getEntity(entry.mobId()) : null;
			if (mob != null) {
				ids.add(mob.getId());
				markers.add(new HostsPayload.Marker(mob.getId(), mob.getBlockPos(), false));
			}
		}
		SessionManager.bodyOf(player).filter(body -> body.getWorld() == world).ifPresent(body -> {
			ids.add(body.getId());
			markers.add(new HostsPayload.Marker(body.getId(), body.getBlockPos(), true));
		});
		return new HostsPayload(ids, active, NeuralLinkConfig.maxChipsPerHelmet, markers);
	}

	/** For tests: a packet went out and wasn't forgotten. */
	public static boolean remembers(UUID player) {
		return LAST.containsKey(player);
	}

	/** on leaving or world change the client drops old frames, so resend even an equal packet. */
	public static void forget(UUID player) {
		LAST.remove(player);
	}

	public static void clear() {
		LAST.clear();
	}
}
