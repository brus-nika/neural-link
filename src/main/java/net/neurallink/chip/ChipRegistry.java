package net.neurallink.chip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.neurallink.item.ChipBinding;
import net.neurallink.registry.ModAttachments;
import net.neurallink.registry.ModComponents;
import org.slf4j.Logger;

/** All chips in the world, so the menu covers unloaded chunks. */
public class ChipRegistry extends PersistentState {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String KEY = "neurallink_chips";
	private static final Codec<List<ChipEntry>> LIST_CODEC = ChipEntry.CODEC.listOf();
	private static final Codec<Map<UUID, UUID>> OWNERS_CODEC = Codec.unboundedMap(Uuids.STRING_CODEC, Uuids.STRING_CODEC);
	private static final Codec<List<UUID>> IDS_CODEC = Uuids.STRING_CODEC.listOf();
	private static final Type<ChipRegistry> TYPE = new Type<>(ChipRegistry::new, ChipRegistry::fromNbt, null);

	private final Map<UUID, ChipEntry> entries = new LinkedHashMap<>();
	private final Map<UUID, UUID> helmetOwners = new HashMap<>();
	private final Set<UUID> releasedHelmets = new HashSet<>();
	private final Map<UUID, Absence> absences = new HashMap<>();

	/** the server never sees the creative cursor, so a moved helmet looks gone. */
	public static final int CREATIVE_GRACE_TICKS = 100;

	private record Absence(int since, int seen) {
	}

	public static ChipRegistry get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, KEY);
	}

	public static ChipRegistry fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		ChipRegistry registry = new ChipRegistry();
		NbtElement list = nbt.get("chips");
		if (list != null) {
			LIST_CODEC.parse(NbtOps.INSTANCE, list).resultOrPartial(LOGGER::error)
					.ifPresent(found -> found.forEach(entry -> registry.entries.put(entry.chipId(), entry)));
		}
		NbtElement owners = nbt.get("helmet_owners");
		if (owners != null) {
			OWNERS_CODEC.parse(NbtOps.INSTANCE, owners).resultOrPartial(LOGGER::error).ifPresent(registry.helmetOwners::putAll);
		}
		NbtElement released = nbt.get("released_helmets");
		if (released != null) {
			IDS_CODEC.parse(NbtOps.INSTANCE, released).resultOrPartial(LOGGER::error).ifPresent(registry.releasedHelmets::addAll);
		}
		return registry;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		LIST_CODEC.encodeStart(NbtOps.INSTANCE, List.copyOf(entries.values())).resultOrPartial(LOGGER::error)
				.ifPresent(list -> nbt.put("chips", list));
		OWNERS_CODEC.encodeStart(NbtOps.INSTANCE, helmetOwners).resultOrPartial(LOGGER::error)
				.ifPresent(owners -> nbt.put("helmet_owners", owners));
		IDS_CODEC.encodeStart(NbtOps.INSTANCE, List.copyOf(releasedHelmets)).resultOrPartial(LOGGER::error)
				.ifPresent(released -> nbt.put("released_helmets", released));
		return nbt;
	}

	public Optional<ChipEntry> find(UUID chipId) {
		return Optional.ofNullable(entries.get(chipId));
	}

	public List<ChipEntry> all() {
		return List.copyOf(entries.values());
	}

	public List<ChipEntry> forHelmet(UUID helmetId) {
		return entries.values().stream().filter(entry -> entry.helmetId().equals(helmetId)).toList();
	}

	public static List<ChipEntry> forOperator(ServerPlayerEntity player) {
		UUID helmetId = ChipLogic.wornHelmet(player).get(ModComponents.HELMET_ID);
		if (helmetId == null) {
			return List.of();
		}
		return get(player.getServer()).forHelmet(helmetId).stream().filter(entry -> entry.owner().equals(player.getUuid())).toList();
	}

	/** Lost implants take no visor slot. */
	public int activeCount(UUID helmetId) {
		return (int) forHelmet(helmetId).stream().filter(entry -> !entry.lost()).count();
	}

	/** across all the player's helmets: the per-player limit. */
	public int activeCountOfOwner(UUID owner) {
		return (int) entries.values().stream().filter(entry -> !entry.lost() && entry.owner().equals(owner)).count();
	}

	public void put(ChipEntry entry) {
		entries.put(entry.chipId(), entry);
		markDirty();
	}

	public void remove(UUID chipId) {
		if (entries.remove(chipId) != null) {
			markDirty();
		}
	}

	/** implants follow the helmet to whoever wears it, but not while the owner still has a copy. */
	public void passToWearers(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			UUID helmetId = ChipLogic.wornHelmet(player).get(ModComponents.HELMET_ID);
			if (helmetId == null) {
				continue;
			}
			UUID owner = helmetOwners.get(helmetId);
			if (!player.getUuid().equals(owner) && !keeps(server, owner, helmetId)) {
				helmetOwners.put(helmetId, player.getUuid());
				releasedHelmets.remove(helmetId);
				absences.remove(helmetId);
				markDirty();
				forHelmet(helmetId).forEach(entry -> put(entry.withOwner(player.getUuid())));
				ChipLogic.checkNetwork(player, this, helmetId);
			}
		}
	}

	/** Offline only the leave note tells; no note (crash) means the owner keeps them. */
	private boolean keeps(MinecraftServer server, UUID owner, UUID helmetId) {
		if (owner == null) {
			return false;
		}
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(owner);
		if (online == null) {
			return !releasedHelmets.contains(helmetId);
		}
		if (ChipLogic.holdsHelmet(online, helmetId)) {
			absences.remove(helmetId);
			return true;
		}
		if (!online.isCreative()) {
			return false;
		}
		int now = server.getTicks();
		Absence last = absences.get(helmetId);
		Absence absence = last == null || last.seen() < now - 1 ? new Absence(now, now) : new Absence(last.since(), now);
		absences.put(helmetId, absence);
		return now - absence.since() < CREATIVE_GRACE_TICKS;
	}

	/** offline inventories can't be checked, so leaving notes which helmets stayed behind. */
	public void onOwnerLeft(ServerPlayerEntity player) {
		helmetOwners.forEach((helmetId, owner) -> {
			if (owner.equals(player.getUuid()) && (ChipLogic.keepsHelmet(player, helmetId) ? releasedHelmets.remove(helmetId) : releasedHelmets.add(helmetId))) {
				markDirty();
			}
		});
	}

	/** Online his inventory decides; an old note could outlive a crash. */
	public void onOwnerJoined(ServerPlayerEntity player) {
		helmetOwners.forEach((helmetId, owner) -> {
			if (owner.equals(player.getUuid()) && releasedHelmets.remove(helmetId)) {
				markDirty();
			}
		});
	}

	/** the shutdown save can come before players are removed. */
	public void onServerStopping(MinecraftServer server) {
		server.getPlayerManager().getPlayerList().forEach(this::onOwnerLeft);
	}

	/** An unowned helmet goes to whoever binds the first chip. */
	public void claimHelmet(UUID helmetId, UUID player) {
		if (helmetOwners.putIfAbsent(helmetId, player) == null) {
			markDirty();
		}
	}

	/** spare chips have no entry, so their owner comes from here. */
	public UUID helmetOwner(UUID helmetId, UUID fallback) {
		return helmetOwners.getOrDefault(helmetId, fallback);
	}

	public void refresh(MinecraftServer server) {
		for (ChipEntry entry : new ArrayList<>(entries.values())) {
			ServerWorld world = server.getWorld(entry.world());
			if (world != null && world.getEntity(entry.mobId()) instanceof MobEntity mob && mob.isAlive()) {
				update(entry, mob, world);
			}
		}
	}

	/** Keeps the mob's last seen point or connecting loads the wrong chunk. */
	public void onMobChangedWorld(Entity mob, ServerWorld world) {
		UUID chipId = mob.getAttached(ModAttachments.CHIP);
		if (chipId != null) {
			updateLastSeen(mob, chipId, world);
		}
	}

	/** removal without death marks the host lost; a later conversion reassigns it. */
	public void onMobUnloaded(Entity mob, UUID chipId, ServerWorld world) {
		if (mob.getRemovalReason() != null && mob.getRemovalReason().shouldDestroy()) {
			find(chipId).ifPresent(entry -> put(entry.asLost()));
		} else {
			updateLastSeen(mob, chipId, world);
		}
	}

	private void updateLastSeen(Entity mob, UUID chipId, ServerWorld world) {
		if (mob instanceof LivingEntity living) {
			find(chipId).ifPresent(entry -> update(entry, living, world));
		}
	}

	private void update(ChipEntry entry, LivingEntity mob, ServerWorld world) {
		ChipEntry updated = withMob(entry, mob, world);
		if (!updated.equals(entry)) {
			put(updated);
		}
	}

	/** Rebuilt from the mob, so menu choices are copied by hand. */
	public static ChipEntry withMob(ChipEntry entry, LivingEntity mob, ServerWorld world) {
		return ChipEntry.of(new ChipBinding(entry.chipId(), entry.helmetId(), entry.owner()), mob, world).withFavorite(entry.favorite());
	}
}
