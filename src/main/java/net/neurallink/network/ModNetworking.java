package net.neurallink.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.neurallink.chip.ChipCard;
import net.neurallink.chip.ChipLogic;
import net.neurallink.chip.ChipRegistry;
import net.neurallink.chip.ChipResult;
import net.neurallink.config.NeuralLinkConfig;
import net.neurallink.session.EndReason;
import net.neurallink.session.SessionManager;

public final class ModNetworking {
	private static final int CONNECT_INTERVAL_TICKS = 10;
	private static final int FAVORITE_INTERVAL_TICKS = 4;

	private static final Map<UUID, Integer> LAST_CONNECT = new HashMap<>();
	private static final Map<UUID, Integer> LAST_CHIPS = new HashMap<>();
	private static final Map<UUID, Map<UUID, Integer>> LAST_FAVORITE = new HashMap<>();
	private static final Set<UUID> LIST_DUE = new HashSet<>();

	private ModNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.configurationS2C().register(ProtocolPayload.ID, ProtocolPayload.CODEC);
		ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
			if (!ServerConfigurationNetworking.canSend(handler, ProtocolPayload.ID)) {
				handler.disconnect(Text.translatableWithFallback("message.neurallink.protocol", "This server needs another version of Neural Link"));
			}
		});
		PayloadTypeRegistry.playC2S().register(ConnectPayload.ID, ConnectPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ExitPayload.ID, ExitPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(InputPayload.ID, InputPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SlotInputPayload.ID, SlotInputPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RequestChipsPayload.ID, RequestChipsPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ForgetChipPayload.ID, ForgetChipPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DisableChipPayload.ID, DisableChipPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FavoriteChipPayload.ID, FavoriteChipPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SessionStatePayload.ID, SessionStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ChipListPayload.ID, ChipListPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HudStatePayload.ID, HudStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HostsPayload.ID, HostsPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(VisorEventPayload.ID, VisorEventPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ChipFeedbackPayload.ID, ChipFeedbackPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ConfigPayload.ID, ConfigPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SpawnZonePayload.ID, SpawnZonePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(BodySoundPayload.ID, BodySoundPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ConnectPayload.ID, (payload, context) ->
				limitedConnect(context.player(), payload.chipId()).notify(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(RequestChipsPayload.ID, (payload, context) -> sendChips(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(DisableChipPayload.ID, (payload, context) -> {
			ChipResult result = ChipLogic.disable(context.player(), payload.chipId());
			if (result != ChipResult.DISABLED) {
				result.notify(context.player());
			}
			sendChips(context.player());
		});
		ServerPlayNetworking.registerGlobalReceiver(ForgetChipPayload.ID, (payload, context) -> {
			ChipLogic.forget(context.player(), payload.chipId());
			sendChips(context.player());
		});
		ServerPlayNetworking.registerGlobalReceiver(FavoriteChipPayload.ID, (payload, context) -> {
			if (limitedFavorite(context.player(), payload.chipId(), payload.favorite())) {
				sendChips(context.player());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(ExitPayload.ID, (payload, context) -> SessionManager.exit(context.player(), EndReason.EXIT));
		ServerPlayNetworking.registerGlobalReceiver(InputPayload.ID, (payload, context) -> SessionManager.onInput(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(SlotInputPayload.ID, (payload, context) -> SessionManager.onSlotInput(context.player(), payload.slot(), payload.pressed()));
	}

	/**
	 * refused, not dropped, or the client's loading screen hangs till timeout. Answers like the cooldown: refusal sound, no
	 * toast.
	 */
	public static ChipResult limitedConnect(ServerPlayerEntity player, UUID chipId) {
		int now = player.getServer().getTicks();
		Integer last = LAST_CONNECT.get(player.getUuid());
		if (last != null && now - last < CONNECT_INTERVAL_TICKS) {
			return ChipResult.COOLDOWN;
		}
		LAST_CONNECT.put(player.getUuid(), now);
		return SessionManager.connect(player, chipId);
	}

	/** Per player and chip. A dropped packet still gets the list or the client keeps a star the server never set. */
	public static boolean limitedFavorite(ServerPlayerEntity player, UUID chipId, boolean favorite) {
		if (ChipLogic.pinnable(player, chipId).isEmpty()) {
			LIST_DUE.add(player.getUuid()); // checked first, a stamp per forged id would grow the map forever
			return false;
		}
		int now = player.getServer().getTicks();
		Map<UUID, Integer> chips = LAST_FAVORITE.computeIfAbsent(player.getUuid(), id -> new HashMap<>());
		Integer last = chips.get(chipId);
		if (last != null && now - last < FAVORITE_INTERVAL_TICKS) {
			LIST_DUE.add(player.getUuid());
			return false;
		}
		chips.put(chipId, now);
		return ChipLogic.setFavorite(player, chipId, favorite);
	}

	/** once per tick; a longer window could eat the menu-open request. */
	public static boolean chipsAllowed(ServerPlayerEntity player) {
		int now = player.getServer().getTicks();
		Integer last = LAST_CHIPS.put(player.getUuid(), now);
		return last == null || last != now;
	}

	/** Held-back lists go out next tick so no reply gets lost. */
	public static void flushChips(MinecraftServer server) {
		if (LIST_DUE.isEmpty()) {
			return;
		}
		for (UUID id : List.copyOf(LIST_DUE)) {
			LIST_DUE.remove(id);
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player != null) {
				sendChips(player);
			}
		}
	}

	public static void forget(UUID player) {
		LAST_CONNECT.remove(player);
		LAST_CHIPS.remove(player);
		LAST_FAVORITE.remove(player);
		LIST_DUE.remove(player);
	}

	public static void clear() {
		LAST_CONNECT.clear();
		LAST_CHIPS.clear();
		LAST_FAVORITE.clear();
		LIST_DUE.clear();
	}

	/** for tests: a list is held for next tick. */
	public static boolean listDue(UUID player) {
		return LIST_DUE.contains(player);
	}

	/** For tests: chips with a star stamp for this player. */
	public static int favoriteStamps(UUID player) {
		return LAST_FAVORITE.getOrDefault(player, Map.of()).size();
	}

	/** for tests: tick of the last list, -1 if none. */
	public static int lastListTick(UUID player) {
		return LAST_CHIPS.getOrDefault(player, -1);
	}

	public static void sendChips(ServerPlayerEntity player) {
		if (!chipsAllowed(player)) {
			LIST_DUE.add(player.getUuid());
			return;
		}
		ItemStack helmet = ChipLogic.wornHelmet(player);
		if (helmet.isEmpty()) {
			ChipResult.NEED_HELMET.notify(player);
			return;
		}
		int active = (int) ChipRegistry.forOperator(player).stream().filter(entry -> !entry.lost()).count(); // visor counter = live hosts in the menu
		ServerPlayNetworking.send(player, new ChipListPayload(ChipCard.forPlayer(player), active, NeuralLinkConfig.maxChipsPerHelmet));
	}
}
