package net.neurallink.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.neurallink.network.ConfigPayload;
import org.slf4j.Logger;

/** Server config, config/neurallink.json. Sent to clients on join and overrides their file. */
public final class NeuralLinkConfig {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final int DEFAULT_MAX_CHIPS = 8;
	public static final int DEFAULT_NOISE_START = 256;
	public static final int DEFAULT_NOISE_MAX = 4096;
	public static final int DEFAULT_CONNECT_COOLDOWN = 2;
	public static final int MAX_CONNECT_COOLDOWN = 60;

	public static boolean allowBosses = false;
	public static int maxChipsPerHelmet = DEFAULT_MAX_CHIPS;
	public static List<String> entityBlacklist = List.of();
	public static int noiseStartDistance = DEFAULT_NOISE_START;
	public static int noiseMaxDistance = DEFAULT_NOISE_MAX;
	/** the host's height counts: weaker deep, stronger high. */
	public static boolean depthNoise = true;
	/** One cooldown for connecting and switching. */
	public static int connectCooldownSeconds = DEFAULT_CONNECT_COOLDOWN;
	/** across all a player's helmets, 0 is no limit. */
	public static int maxChipsPerPlayer = 0;
	/** Spawn protection keeps hosts out like it blocks building. */
	public static boolean spawnProtection = true;
	/** implants do nothing in these dimensions. */
	public static List<String> dimensionBlacklist = List.of();
	/** The body shows the operator is away: head down, visor glowing. */
	public static boolean bodyTrance = false;

	/** file contents with defaults; separate since Gson skips static fields. */
	private static final class Values {
		boolean allowBosses = false;
		int maxChipsPerHelmet = DEFAULT_MAX_CHIPS;
		int noiseStartDistance = DEFAULT_NOISE_START;
		int noiseMaxDistance = DEFAULT_NOISE_MAX;
		List<String> entityBlacklist = List.of();
		boolean depthNoise = true;
		int connectCooldownSeconds = DEFAULT_CONNECT_COOLDOWN;
		int maxChipsPerPlayer = 0;
		boolean spawnProtection = true;
		List<String> dimensionBlacklist = List.of();
		boolean bodyTrance = false;
	}

	private NeuralLinkConfig() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("neurallink.json");
	}

	/** Old files get new keys with defaults, existing values kept. */
	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try {
				if (read(path, false) != null) {
					parse(new Values());
				}
			} catch (IOException | JsonParseException | IllegalStateException e) {
				LOGGER.error("Could not read {}, using defaults", path, e);
				parse(new Values());
			}
			return;
		}
		Values defaults = new Values();
		parse(defaults);
		write(defaults);
	}

	/**
	 * /neurallink reload. A bad file keeps the current values, not defaults, so a typo can't flip settings on a live server.
	 * Defaults return only on restart.
	 */
	public static Optional<Text> reload() {
		Path path = path();
		if (!Files.exists(path)) {
			LOGGER.warn("No {}, keeping the current values", path);
			return Optional.of(Text.translatable("commands.neurallink.reload.no_file", "config/" + path.getFileName()));
		}
		try {
			return Optional.ofNullable(read(path, true));
		} catch (IOException | JsonParseException | IllegalStateException e) {
			LOGGER.warn("Could not read {}, keeping the current values: {}", path, e.getMessage());
			return Optional.of(Text.literal(String.valueOf(e.getMessage())));
		}
	}

	/**
	 * applies the file and returns null, or the reason with nothing applied. The lenient parser reads {@code tru} as a
	 * string Gson treats as false, so such keys are refused when {@code strict}. Misspelt keys are refused too.
	 */
	private static Text read(Path path, boolean strict) throws IOException {
		JsonElement json;
		try (Reader reader = Files.newBufferedReader(path)) {
			json = JsonParser.parseReader(reader);
		}
		if (!json.isJsonObject()) {
			return Text.translatable("commands.neurallink.reload.not_object");
		}
		JsonObject object = json.getAsJsonObject();
		List<String> unknown = unknownKeys(object);
		List<String> wrong = wrongTypes(object);
		if (strict && !unknown.isEmpty()) {
			return Text.translatable("commands.neurallink.reload.unknown_key", String.join(", ", unknown));
		}
		if (strict && !wrong.isEmpty()) {
			return Text.translatable("commands.neurallink.reload.wrong_type", String.join(", ", wrong));
		}
		if (!unknown.isEmpty()) {
			LOGGER.warn("{}: unknown keys {}, a typo in the name? They do nothing", path, unknown);
		}
		if (!wrong.isEmpty()) {
			LOGGER.warn("{}: {} not of the right type, using the defaults for them", path, wrong);
		}
		JsonObject known = object.deepCopy();
		wrong.forEach(known::remove);
		parse(GSON.fromJson(known, Values.class));
		if (missesKeys(object)) {
			GSON.toJsonTree(new Values()).getAsJsonObject().entrySet().stream()
					.filter(key -> !object.has(key.getKey()))
					.forEach(key -> object.add(key.getKey(), key.getValue()));
			write(object);
		}
		return null;
	}

	/** Unknown keys, most likely typos. */
	private static List<String> unknownKeys(JsonObject json) {
		List<String> unknown = new ArrayList<>(json.keySet());
		for (Field field : Values.class.getDeclaredFields()) {
			unknown.remove(field.getName());
		}
		return unknown;
	}

	/** keys whose value has the wrong JSON type. */
	private static List<String> wrongTypes(JsonObject json) {
		List<String> wrong = new ArrayList<>();
		for (Field field : Values.class.getDeclaredFields()) {
			JsonElement value = json.get(field.getName());
			if (Modifier.isStatic(field.getModifiers()) || value == null) {
				continue;
			}
			boolean fits;
			if (field.getType() == boolean.class) {
				fits = value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
			} else if (field.getType() == int.class) {
				fits = value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
			} else { // plain values only, nesting would fail the parse
				fits = value.isJsonNull() || value.isJsonArray() && value.getAsJsonArray().asList().stream().allMatch(item -> item.isJsonPrimitive() || item.isJsonNull());
			}
			if (!fits) {
				wrong.add(field.getName());
			}
		}
		return wrong;
	}

	private static boolean missesKeys(JsonObject json) {
		for (Field field : Values.class.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers()) && !json.has(field.getName())) {
				return true;
			}
		}
		return false;
	}

	private static void write(Values values) {
		write(GSON.toJsonTree(values));
	}

	private static void write(JsonElement json) {
		try (Writer writer = Files.newBufferedWriter(path())) {
			GSON.toJson(json, writer);
		} catch (IOException | JsonIOException e) {
			LOGGER.error("Could not write {}", path(), e);
		}
	}

	/** From the settings screen, same bounds as the file. */
	public static void update(ConfigPayload config) {
		write(values(parse(values(config))));
	}

	private static Values values(ConfigPayload config) {
		Values values = new Values();
		values.allowBosses = config.allowBosses();
		values.maxChipsPerHelmet = config.maxChipsPerHelmet();
		values.noiseStartDistance = config.noiseStartDistance();
		values.noiseMaxDistance = config.noiseMaxDistance();
		values.entityBlacklist = config.entityBlacklist();
		values.depthNoise = config.depthNoise();
		values.connectCooldownSeconds = config.connectCooldownSeconds();
		values.maxChipsPerPlayer = config.maxChipsPerPlayer();
		values.spawnProtection = config.spawnProtection();
		values.dimensionBlacklist = config.dimensionBlacklist();
		values.bodyTrance = config.bodyTrance();
		return values;
	}

	/** for tests: file format from a string. */
	public static void loadFromJson(String json) {
		parse(GSON.fromJson(json, Values.class));
	}

	private static ConfigPayload parse(Values values) {
		if (values == null) {
			return null;
		}
		ConfigPayload config = new ConfigPayload(values.allowBosses, Math.max(1, values.maxChipsPerHelmet), values.noiseStartDistance,
				Math.max(values.noiseStartDistance + 1, values.noiseMaxDistance), ids(values.entityBlacklist), values.depthNoise,
				MathHelper.clamp(values.connectCooldownSeconds, 0, MAX_CONNECT_COOLDOWN), Math.max(0, values.maxChipsPerPlayer), values.spawnProtection,
				ids(values.dimensionBlacklist), values.bodyTrance);
		apply(config);
		return config;
	}

	/** a hand-edited file is normalized like the screens write it. */
	private static List<String> ids(List<String> ids) {
		return ids == null ? List.of() : List.copyOf(normalizeIds(ids.stream().filter(Objects::nonNull).toList()));
	}

	/** Trimmed, deduped, minecraft: added if no namespace. */
	public static List<String> normalizeIds(List<String> ids) {
		LinkedHashSet<String> normal = new LinkedHashSet<>();
		for (String id : ids) {
			String text = id.trim();
			if (!text.isEmpty()) {
				normal.add(text.contains(":") ? text : "minecraft:" + text);
			}
		}
		return new ArrayList<>(normal);
	}

	public static void apply(ConfigPayload config) {
		allowBosses = config.allowBosses();
		maxChipsPerHelmet = config.maxChipsPerHelmet();
		noiseStartDistance = config.noiseStartDistance();
		noiseMaxDistance = config.noiseMaxDistance();
		entityBlacklist = config.entityBlacklist();
		depthNoise = config.depthNoise();
		connectCooldownSeconds = config.connectCooldownSeconds();
		maxChipsPerPlayer = config.maxChipsPerPlayer();
		spawnProtection = config.spawnProtection();
		dimensionBlacklist = config.dimensionBlacklist();
		bodyTrance = config.bodyTrance();
	}

	/** what the server runs with, as sent to the client. */
	public static ConfigPayload current() {
		return new ConfigPayload(allowBosses, maxChipsPerHelmet, noiseStartDistance, noiseMaxDistance, entityBlacklist, depthNoise,
				connectCooldownSeconds, maxChipsPerPlayer, spawnProtection, dimensionBlacklist, bodyTrance);
	}

	public static void sync(ServerPlayerEntity player) {
		if (ServerPlayNetworking.canSend(player, ConfigPayload.ID)) {
			ServerPlayNetworking.send(player, current());
		}
	}
}
