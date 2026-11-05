package net.neurallink.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.neurallink.chip.ChipEntry;
import net.neurallink.chip.ChipLogic;
import net.neurallink.chip.ChipRegistry;
import net.neurallink.config.NeuralLinkConfig;
import net.neurallink.network.ModNetworking;
import net.neurallink.registry.ModAttachments;
import net.neurallink.session.SessionManager;

/** /neurallink for admins: level 2 to look, 3 to act. Dev commands merge into the same root. */
public final class AdminCommand {
	public static final int LOOK = 2;
	public static final int ACT = 3;

	/** palette tokens: text, textDim, highlight. */
	private static final int TEXT = 0xF5D9D0;
	private static final int TEXT_DIM = 0xB58A86;
	private static final int HIGHLIGHT = 0xE5383B;

	private static final DynamicCommandExceptionType UNKNOWN_TARGET = new DynamicCommandExceptionType(target -> Text.translatable("commands.neurallink.unknown_target", target));
	private static final DynamicCommandExceptionType UNKNOWN_CHIP = new DynamicCommandExceptionType(chip -> Text.translatable("commands.neurallink.unknown_chip", chip));
	private static final DynamicCommandExceptionType UNKNOWN_PLAYER = new DynamicCommandExceptionType(player -> Text.translatable("commands.neurallink.unknown_player", player));
	private static final DynamicCommandExceptionType NOT_IN_SESSION = new DynamicCommandExceptionType(player -> Text.translatable("commands.neurallink.kick.none", player));
	private static final SimpleCommandExceptionType NOBODY_IN_SESSION = new SimpleCommandExceptionType(Text.translatable("commands.neurallink.kick.nobody"));
	private static final DynamicCommandExceptionType RELOAD_FAILED = new DynamicCommandExceptionType(reason -> Text.translatable("commands.neurallink.reload.failed", reason));

	/** a player by id and name; uncached owners show as UUID. */
	private record Who(UUID id, String name) {
	}

	private AdminCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(literal("neurallink")
				.requires(source -> source.hasPermissionLevel(LOOK))
				.then(literal("list")
						.executes(context -> list(context.getSource(), null))
						.then(argument("player", StringArgumentType.string()).suggests(AdminCommand::suggestOwners)
								.executes(context -> list(context.getSource(), player(context.getSource().getServer(), StringArgumentType.getString(context, "player"))))))
				.then(literal("inspect")
						.then(argument("target", StringArgumentType.string()).suggests(AdminCommand::suggestTargets)
								.executes(context -> inspect(context.getSource(), StringArgumentType.getString(context, "target")))))
				.then(literal("sessions").executes(context -> sessions(context.getSource())))
				.then(literal("kick").requires(source -> source.hasPermissionLevel(ACT))
						.then(argument("players", EntityArgumentType.players())
								.executes(context -> kick(context.getSource(), EntityArgumentType.getPlayers(context, "players")))))
				.then(literal("disable").requires(source -> source.hasPermissionLevel(ACT))
						.then(argument("chip", UuidArgumentType.uuid()).suggests(AdminCommand::suggestChips)
								.executes(context -> disable(context.getSource(), UuidArgumentType.getUuid(context, "chip")))))
				.then(literal("reload").requires(source -> source.hasPermissionLevel(ACT))
						.executes(context -> reload(context.getSource())))));
	}

	private static int list(ServerCommandSource source, Who owner) {
		MinecraftServer server = source.getServer();
		List<ChipEntry> chips = ChipRegistry.get(server).all().stream()
				.filter(entry -> owner == null || entry.owner().equals(owner.id()))
				.sorted(Comparator.comparing((ChipEntry entry) -> name(server, entry.owner())).thenComparing(entry -> entry.entityType().toString()))
				.toList();
		source.sendFeedback(() -> owner == null
				? Text.translatable("commands.neurallink.list.all", value(chips.size())).withColor(TEXT_DIM)
				: Text.translatable("commands.neurallink.list.player", value(owner.name()), value(chips.size())).withColor(TEXT_DIM), false);
		for (ChipEntry entry : chips) {
			MutableText line = Text.literal("• ").withColor(TEXT_DIM).append(host(entry));
			if (owner == null) {
				line.append(dot()).append(Text.literal(name(server, entry.owner())).withColor(TEXT));
			}
			line.append(dot()).append(coordinates(entry.world(), Vec3d.ofBottomCenter(entry.pos()))).append(dot()).append(status(server, entry));
			source.sendFeedback(() -> line, false);
		}
		return chips.size();
	}

	/** An implant by UUID, else a player by name or UUID. */
	private static int inspect(ServerCommandSource source, String target) throws CommandSyntaxException {
		MinecraftServer server = source.getServer();
		Optional<ChipEntry> chip = uuid(target).flatMap(ChipRegistry.get(server)::find);
		if (chip.isPresent()) {
			inspectChip(source, chip.get());
			return 1;
		}
		Who who = find(server, target).orElseThrow(() -> UNKNOWN_TARGET.create(target));
		inspectPlayer(source, who);
		return 1;
	}

	private static void inspectChip(ServerCommandSource source, ChipEntry entry) {
		MinecraftServer server = source.getServer();
		send(source, Text.translatable("commands.neurallink.inspect.chip", copyable(entry.chipId().toString())));
		send(source, Text.translatable("commands.neurallink.inspect.host", host(entry), value(Math.round(entry.health())), value(Math.round(entry.maxHealth()))));
		send(source, Text.translatable("commands.neurallink.inspect.owner", suggesting(Text.literal(name(server, entry.owner())).withColor(TEXT),
				"/neurallink inspect " + quoted(name(server, entry.owner())))));
		send(source, Text.translatable("commands.neurallink.inspect.helmet", copyable(entry.helmetId().toString())));
		send(source, Text.translatable("commands.neurallink.inspect.place", coordinates(entry.world(), Vec3d.ofBottomCenter(entry.pos())), value(entry.world().getValue().toString())));
		MutableText status = Text.translatable("commands.neurallink.inspect.status", status(server, entry)).withColor(TEXT_DIM);
		if (source.hasPermissionLevel(ACT)) {
			status.append(" ").append(action("commands.neurallink.action.disable", "/neurallink disable " + entry.chipId()));
		}
		source.sendFeedback(() -> status, false);
	}

	private static void inspectPlayer(ServerCommandSource source, Who who) {
		MinecraftServer server = source.getServer();
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(who.id());
		MutableText header = Text.translatable("commands.neurallink.inspect.player", value(who.name())).withColor(TEXT_DIM);
		if (online == null) {
			header.append(dot()).append(Text.translatable("commands.neurallink.inspect.offline").withColor(TEXT_DIM));
		}
		source.sendFeedback(() -> header, false);
		List<ChipEntry> chips = ChipRegistry.get(server).all().stream().filter(entry -> entry.owner().equals(who.id())).toList();
		long lost = chips.stream().filter(ChipEntry::lost).count();
		MutableText count = Text.translatable("commands.neurallink.inspect.chips", value(chips.size()), value(lost)).withColor(TEXT_DIM);
		if (!chips.isEmpty()) {
			count.append(" ").append(action("commands.neurallink.action.list", "/neurallink list " + quoted(who.name())));
		}
		source.sendFeedback(() -> count, false);

		Optional<SessionManager.SessionView> session = SessionManager.sessions(server).stream().filter(view -> view.player().getUuid().equals(who.id())).findFirst();
		Optional<SessionManager.PendingView> pending = SessionManager.pending().stream().filter(view -> view.player().equals(who.id())).findFirst();
		if (session.isPresent()) {
			SessionManager.SessionView view = session.get();
			MutableText line = Text.translatable("commands.neurallink.inspect.session", host(server, view.host()), coordinates(view.host()),
					coordinates(view.body()), value(duration(view.ticks()))).withColor(TEXT_DIM);
			if (source.hasPermissionLevel(ACT)) {
				line.append(" ").append(action("commands.neurallink.action.kick", kickCommand(who.name())));
			}
			source.sendFeedback(() -> line, false);
		}
		if (pending.isPresent()) {
			ChipEntry entry = pending.get().entry();
			send(source, Text.translatable("commands.neurallink.inspect.pending", host(entry), coordinates(entry.world(), Vec3d.ofBottomCenter(entry.pos()))));
		}
		if (session.isEmpty() && pending.isEmpty()) {
			send(source, Text.translatable("commands.neurallink.inspect.no_session"));
		}
	}

	private static int sessions(ServerCommandSource source) {
		MinecraftServer server = source.getServer();
		List<SessionManager.SessionView> sessions = SessionManager.sessions(server).stream()
				.sorted(Comparator.comparing(view -> view.player().getGameProfile().getName())).toList();
		List<SessionManager.PendingView> pending = SessionManager.pending();
		source.sendFeedback(() -> Text.translatable("commands.neurallink.sessions", value(sessions.size())).withColor(TEXT_DIM), false);
		for (SessionManager.SessionView view : sessions) {
			String name = view.player().getGameProfile().getName();
			MutableText line = Text.literal("• ").withColor(TEXT_DIM).append(Text.translatable("commands.neurallink.sessions.entry", value(name),
					host(server, view.host()), coordinates(view.host()), coordinates(view.body()), value(duration(view.ticks()))).withColor(TEXT_DIM));
			if (source.hasPermissionLevel(ACT)) {
				line.append(" ").append(action("commands.neurallink.action.kick", kickCommand(name)));
			}
			source.sendFeedback(() -> line, false);
		}
		for (SessionManager.PendingView view : pending) {
			ChipEntry entry = view.entry();
			source.sendFeedback(() -> Text.literal("• ").withColor(TEXT_DIM).append(Text.translatable("commands.neurallink.sessions.pending", value(name(server, view.player())),
					host(entry), coordinates(entry.world(), Vec3d.ofBottomCenter(entry.pos()))).withColor(TEXT_DIM)), false);
		}
		return sessions.size();
	}

	/** Several players at once like vanilla /kick. Fails only if none were connected. */
	private static int kick(ServerCommandSource source, Collection<ServerPlayerEntity> players) throws CommandSyntaxException {
		int kicked = 0;
		for (ServerPlayerEntity player : players) {
			if (SessionManager.kick(player)) {
				kicked++;
				source.sendFeedback(() -> Text.translatable("commands.neurallink.kick.success", value(player.getDisplayName())).withColor(TEXT_DIM), true);
			}
		}
		if (kicked == 0) {
			throw players.size() == 1 ? NOT_IN_SESSION.create(players.iterator().next().getDisplayName()) : NOBODY_IN_SESSION.create();
		}
		return kicked;
	}

	private static int disable(ServerCommandSource source, UUID chipId) throws CommandSyntaxException {
		MinecraftServer server = source.getServer();
		ChipEntry entry = ChipRegistry.get(server).find(chipId).orElseThrow(() -> UNKNOWN_CHIP.create(chipId.toString()));
		ChipLogic.disableByAdmin(server, entry);
		ServerPlayerEntity owner = server.getPlayerManager().getPlayer(entry.owner());
		if (owner != null && !ChipLogic.wornHelmet(owner).isEmpty()) {
			ModNetworking.sendChips(owner); // an open menu drops the card at once
		}
		source.sendFeedback(() -> Text.translatable("commands.neurallink.disable.success", value(typeName(entry)), value(name(server, entry.owner()))).withColor(TEXT_DIM), true);
		return 1;
	}

	private static int reload(ServerCommandSource source) throws CommandSyntaxException {
		Optional<Text> failure = NeuralLinkConfig.reload();
		if (failure.isPresent()) {
			throw RELOAD_FAILED.create(failure.get());
		}
		source.getServer().getPlayerManager().getPlayerList().forEach(NeuralLinkConfig::sync);
		source.sendFeedback(() -> Text.translatable("commands.neurallink.reload.success").withColor(TEXT_DIM), true);
		return 1;
	}

	private static Who player(MinecraftServer server, String text) throws CommandSyntaxException {
		return find(server, text).orElseThrow(() -> UNKNOWN_PLAYER.create(text));
	}

	/**
	 * online player or implant owner by name or UUID. Not findByName: it asks Mojang on the server thread and in offline
	 * mode invents profiles, so typos never miss.
	 */
	private static Optional<Who> find(MinecraftServer server, String text) {
		Optional<UUID> id = uuid(text);
		ServerPlayerEntity online = id.isPresent() ? server.getPlayerManager().getPlayer(id.get()) : server.getPlayerManager().getPlayer(text);
		if (online != null) {
			return Optional.of(new Who(online.getUuid(), online.getGameProfile().getName()));
		}
		return owners(server).filter(who -> id.isPresent() ? who.id().equals(id.get()) : who.name().equalsIgnoreCase(text)).findFirst();
	}

	private static Stream<Who> owners(MinecraftServer server) {
		return ChipRegistry.get(server).all().stream().map(ChipEntry::owner).distinct().map(owner -> new Who(owner, name(server, owner)));
	}

	/** The name, or UUID if never cached. */
	private static String name(MinecraftServer server, UUID player) {
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(player);
		if (online != null) {
			return online.getGameProfile().getName();
		}
		return Optional.ofNullable(server.getUserCache()).flatMap(cache -> cache.getByUuid(player)).map(GameProfile::getName).orElse(player.toString());
	}

	private static Optional<UUID> uuid(String text) {
		try {
			return Optional.of(UUID.fromString(text));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private static String duration(int ticks) {
		int seconds = ticks / 20;
		return seconds / 60 + ":" + String.format("%02d", seconds % 60);
	}

	private static void send(ServerCommandSource source, MutableText text) {
		source.sendFeedback(() -> text.withColor(TEXT_DIM), false);
	}

	/** type and name tag, click opens inspect. */
	private static MutableText host(ChipEntry entry) {
		return suggesting(typeName(entry).copy().withColor(TEXT), "/neurallink inspect " + entry.chipId());
	}

	/** A controlled host as its implant shows it, else the bare type. */
	private static MutableText host(MinecraftServer server, MobEntity mob) {
		return Optional.ofNullable(mob.getAttached(ModAttachments.CHIP)).flatMap(ChipRegistry.get(server)::find).map(AdminCommand::host)
				.orElseGet(() -> value(mob.getType().getName()));
	}

	private static Text typeName(ChipEntry entry) {
		Text type = Registries.ENTITY_TYPE.get(entry.entityType()).getName();
		return entry.name().isEmpty() ? type : Text.translatable("commands.neurallink.host_named", type, entry.name());
	}

	/** a name as a command argument; offline names that won't parse bare get quotes. */
	private static String quoted(String name) {
		return StringArgumentType.escapeIfRequired(name);
	}

	/** Kick takes a selector, so UUIDs and names like @a go via the name option. */
	private static String kickCommand(String name) {
		return "/neurallink kick " + (name.matches("[A-Za-z0-9_]+") ? name : "@a[name=" + quoted(name) + "]");
	}

	private static MutableText status(MinecraftServer server, ChipEntry entry) {
		if (entry.lost()) {
			return Text.translatable("commands.neurallink.status.lost").withColor(TEXT_DIM);
		}
		Optional<ServerPlayerEntity> operator = SessionManager.operatorOf(entry.mobId());
		if (operator.isPresent()) {
			return Text.translatable("commands.neurallink.status.controlled", operator.get().getGameProfile().getName()).withColor(HIGHLIGHT);
		}
		ServerWorld world = server.getWorld(entry.world());
		return world != null && world.getEntity(entry.mobId()) != null
				? Text.translatable("commands.neurallink.status.loaded").withColor(TEXT)
				: Text.translatable("commands.neurallink.status.unloaded").withColor(TEXT_DIM);
	}

	private static MutableText coordinates(Entity entity) {
		return coordinates(entity.getWorld().getRegistryKey(), entity.getPos());
	}

	/** like vanilla /locate: a click suggests a teleport in the right dimension. */
	private static MutableText coordinates(RegistryKey<World> world, Vec3d pos) {
		BlockPos block = BlockPos.ofFloored(pos);
		String teleport = "/execute in " + world.getValue() + " run tp @s " + block.getX() + " " + block.getY() + " " + block.getZ();
		return Texts.bracketed(Text.translatable("chat.coordinates", block.getX(), block.getY(), block.getZ())).styled(style -> style.withColor(HIGHLIGHT)
				.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, teleport))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("chat.coordinates.tooltip").append("\n" + world.getValue()))));
	}

	private static MutableText copyable(String text) {
		return Text.literal(text).styled(style -> style.withColor(TEXT)
				.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("chat.copy.click"))));
	}

	private static MutableText action(String key, String command) {
		return suggesting(Texts.bracketed(Text.translatable(key)).withColor(HIGHLIGHT), command);
	}

	/** A click only suggests the command, so stray clicks do nothing. */
	private static MutableText suggesting(MutableText text, String command) {
		return text.styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(command))));
	}

	private static MutableText value(Object value) {
		return (value instanceof Text text ? text.copy() : Text.literal(String.valueOf(value))).withColor(TEXT);
	}

	private static MutableText dot() {
		return Text.literal(" · ").withColor(TEXT_DIM);
	}

	private static CompletableFuture<Suggestions> suggestChips(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
		return CommandSource.suggestMatching(ChipRegistry.get(context.getSource().getServer()).all().stream().map(entry -> entry.chipId().toString()), builder);
	}

	private static CompletableFuture<Suggestions> suggestTargets(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
		MinecraftServer server = context.getSource().getServer();
		return CommandSource.suggestMatching(Stream.concat(players(server),
				ChipRegistry.get(server).all().stream().map(entry -> entry.chipId().toString())), builder);
	}

	private static CompletableFuture<Suggestions> suggestOwners(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
		return CommandSource.suggestMatching(players(context.getSource().getServer()), builder);
	}

	/** online players and implant owners, as {@link #find} resolves. */
	private static Stream<String> players(MinecraftServer server) {
		return Stream.concat(Stream.of(server.getPlayerNames()), owners(server).map(Who::name)).distinct().map(AdminCommand::quoted);
	}
}
