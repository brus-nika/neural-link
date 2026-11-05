package net.neurallink.client.config;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.neurallink.client.gui.VisorTheme;
import net.neurallink.client.hud.HudSounds;
import net.neurallink.registry.ModSounds;

/** blacklist id field: suggests known ids, adds on Enter or +. */
final class BlacklistInput {
	private static final int FIELD_HEIGHT = 16;

	private final String key;
	private final Supplier<List<String>> list;
	private final Supplier<Stream<Identifier>> known;
	private final Function<String, Text> names;
	private final boolean serverEditable;
	private final Consumer<String> added;
	private TextFieldWidget idField;
	private boolean idRejected;

	/** key is the row's translation key tail; known gives accepted ids. */
	BlacklistInput(String key, Supplier<List<String>> list, Supplier<Stream<Identifier>> known, Function<String, Text> names, boolean serverEditable, Consumer<String> added) {
		this.key = key;
		this.list = list;
		this.known = known;
		this.names = names;
		this.serverEditable = serverEditable;
		this.added = added;
	}

	static BlacklistInput mobs(ConfigDraft draft, boolean serverEditable, Consumer<String> added) {
		return new BlacklistInput("blacklist", () -> draft.blacklist, () -> Registries.ENTITY_TYPE.getIds().stream(), BlacklistInput::mobName, serverEditable, added);
	}

	static BlacklistInput dimensions(ConfigDraft draft, boolean serverEditable, Consumer<String> added) {
		return new BlacklistInput("dimensions", () -> draft.dimensions, BlacklistInput::knownDimensions, BlacklistInput::dimensionName, serverEditable, added);
	}

	/** vanilla dimensions by name, datapack ones by path (full id is shown anyway). */
	private static Text dimensionName(String id) {
		Identifier parsed = Identifier.tryParse(id);
		if (parsed == null) {
			return Text.literal(id);
		}
		String path = parsed.getPath().replace('_', ' ');
		String fallback = path.isEmpty() ? id : Character.toUpperCase(path.charAt(0)) + path.substring(1);
		return Text.translatableWithFallback("config.neurallink.dimension." + parsed.getNamespace() + "." + parsed.getPath(), fallback);
	}

	/** The world knows its datapack dimensions; the main menu only vanilla ones. */
	private static Stream<Identifier> knownDimensions() {
		MinecraftClient client = MinecraftClient.getInstance();
		IntegratedServer server = client.getServer();
		if (server != null) {
			return server.getWorldRegistryKeys().stream().map(RegistryKey::getValue);
		}
		if (client.getNetworkHandler() != null) {
			return client.getNetworkHandler().getWorldKeys().stream().map(RegistryKey::getValue);
		}
		return Stream.of(World.OVERWORLD, World.NETHER, World.END).map(RegistryKey::getValue);
	}

	String key() {
		return key;
	}

	List<String> list() {
		return list.get();
	}

	Text name(String id) {
		return names.apply(id);
	}

	TextFieldWidget idField(TextRenderer textRenderer) {
		if (idField == null) { // init reruns on resize, keep the typed id
			idField = new TextFieldWidget(textRenderer, 0, 0, 100, FIELD_HEIGHT, Text.translatable("config.neurallink." + key + ".label"));
			idField.setDrawsBackground(false);
			idField.setMaxLength(128);
			idField.setEditable(serverEditable);
			idField.setEditableColor(VisorTheme.TEXT);
			idField.setChangedListener(this::suggestId);
		}
		return idField;
	}

	TextFieldWidget idField() {
		return idField;
	}

	boolean idRejected() {
		return idRejected;
	}

	private void suggestId(String text) {
		idRejected = false;
		idField.setSuggestion(matchId(text).map(id -> (id.startsWith(text) ? id : Identifier.of(id).getPath()).substring(text.length())).orElse(null));
	}

	/** first known id with this prefix; minecraft: optional. */
	private Optional<String> matchId(String text) {
		if (text.isEmpty()) {
			return Optional.empty();
		}
		return known.get().sorted().filter(id -> id.toString().startsWith(text) || id.getPath().startsWith(text)).map(Identifier::toString).findFirst();
	}

	void addId() {
		String text = idField.getText().trim();
		String full = text.contains(":") ? text : "minecraft:" + text;
		Identifier exact = Identifier.tryParse(full);
		Optional<String> id = exact != null && known.get().anyMatch(exact::equals) ? Optional.of(full) : matchId(text);
		if (id.isPresent() && !list().contains(id.get())) {
			list().add(id.get());
			idField.setText("");
			added.accept(id.get());
			HudSounds.play(ModSounds.CLICK, ConfigRow.SOUND_VOLUME);
		} else {
			idRejected = true;
			HudSounds.play(ModSounds.REFUSE, ConfigRow.SOUND_VOLUME);
		}
	}

	static Text mobName(String id) {
		Identifier parsed = Identifier.tryParse(id);
		Optional<EntityType<?>> type = parsed == null ? Optional.empty() : Registries.ENTITY_TYPE.getOptionalValue(parsed);
		return type.<Text>map(EntityType::getName).orElse(Text.literal(id));
	}
}
