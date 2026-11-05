package net.neurallink.advancement;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import org.slf4j.Logger;

/** Per-player advancement progress: mob types connected and current session length. */
public class OperatorStats extends PersistentState {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String KEY = "neurallink_operators";
	private static final Type<OperatorStats> TYPE = new Type<>(OperatorStats::new, OperatorStats::fromNbt, null);

	private record Saved(UUID player, List<Identifier> types, long sessionTicks) {
		private static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Uuids.CODEC.fieldOf("player").forGetter(Saved::player),
				Identifier.CODEC.listOf().fieldOf("types").forGetter(Saved::types),
				Codec.LONG.fieldOf("session_ticks").forGetter(Saved::sessionTicks)
		).apply(instance, Saved::new));
		private static final Codec<List<Saved>> LIST_CODEC = CODEC.listOf();
	}

	private final Map<UUID, Set<Identifier>> types = new HashMap<>();
	private final Map<UUID, Long> sessionTicks = new HashMap<>();

	public static OperatorStats get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, KEY);
	}

	private static OperatorStats fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		OperatorStats stats = new OperatorStats();
		NbtElement list = nbt.get("operators");
		if (list != null) {
			Saved.LIST_CODEC.parse(NbtOps.INSTANCE, list).resultOrPartial(LOGGER::error).ifPresent(found -> found.forEach(saved -> {
				stats.types.put(saved.player(), new HashSet<>(saved.types()));
				stats.sessionTicks.put(saved.player(), saved.sessionTicks());
			}));
		}
		return stats;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		List<Saved> saved = types.keySet().stream()
				.map(player -> new Saved(player, List.copyOf(types.get(player)), sessionTicks.getOrDefault(player, 0L)))
				.toList();
		Saved.LIST_CODEC.encodeStart(NbtOps.INSTANCE, saved).resultOrPartial(LOGGER::error).ifPresent(list -> nbt.put("operators", list));
		return nbt;
	}

	public int addType(UUID player, Identifier type) {
		Set<Identifier> seen = types.computeIfAbsent(player, key -> new HashSet<>());
		if (seen.add(type)) {
			markDirty();
		}
		return seen.size();
	}

	public long addSessionTicks(UUID player, long ticks) {
		long total = sessionTicks.merge(player, ticks, Long::sum);
		markDirty();
		return total;
	}

	public void resetSession(UUID player) {
		if (sessionTicks.remove(player) != null) {
			markDirty();
		}
	}
}
