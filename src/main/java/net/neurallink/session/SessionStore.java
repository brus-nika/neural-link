package net.neurallink.session;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import org.slf4j.Logger;

/** unfinished sessions; a record lives from connect until the player is back. */
public class SessionStore extends PersistentState {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String KEY = "neurallink_sessions";
	private static final Codec<List<SessionRecord>> LIST_CODEC = SessionRecord.CODEC.listOf();
	private static final Type<SessionStore> TYPE = new Type<>(SessionStore::new, SessionStore::fromNbt, null);

	private final Map<UUID, SessionRecord> records = new HashMap<>();

	public static SessionStore get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, KEY);
	}

	public static SessionStore fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		SessionStore store = new SessionStore();
		NbtElement list = nbt.get("sessions");
		if (list != null) {
			LIST_CODEC.parse(NbtOps.INSTANCE, list).resultOrPartial(LOGGER::error)
					.ifPresent(found -> found.forEach(record -> store.records.put(record.player(), record)));
		}
		return store;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		LIST_CODEC.encodeStart(NbtOps.INSTANCE, List.copyOf(records.values())).resultOrPartial(LOGGER::error)
				.ifPresent(list -> nbt.put("sessions", list));
		return nbt;
	}

	public Optional<SessionRecord> find(UUID player) {
		return Optional.ofNullable(records.get(player));
	}

	public void put(SessionRecord record) {
		records.put(record.player(), record);
		markDirty();
	}

	public void remove(UUID player) {
		if (records.remove(player) != null) {
			markDirty();
		}
	}
}
