package net.neurallink.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public final class ClientConfig {
	public static final int DEFAULT_OVERLAY_PERCENT = 12;

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("neurallink-client.json");
	private static ClientConfig instance = new ClientConfig();

	public float overlayStrength = DEFAULT_OVERLAY_PERCENT / 100.0F;
	public int exitHintUses;
	public int verticalHintUses;
	public int sliderHintUses;
	/** menu order as a {@link net.neurallink.client.gui.MenuSort} name; typos fall back to default. */
	public String menuSort = "STATUS";
	/** Edge arrows to off-screen hosts and body. */
	public boolean offscreenArrows = true;
	/** vanilla creeper/spider/enderman view through such a host. */
	public boolean mobVision = true;
	/** Dinnerbone and jeb_ hosts. */
	public boolean easterEggs = true;
	/** body sounds in a session as a share of their volume, 0 is none. */
	public int bodySoundPercent = BodySoundPlayer.DEFAULT_PERCENT;

	public static ClientConfig get() {
		return instance;
	}

	public static void load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				ClientConfig loaded = GSON.fromJson(reader, ClientConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (IOException | JsonParseException e) {
				LOGGER.error("Could not read {}", PATH, e);
			}
		}
	}

	public static void save() {
		try (Writer writer = Files.newBufferedWriter(PATH)) {
			GSON.toJson(instance, writer);
		} catch (IOException e) {
			LOGGER.error("Could not write {}", PATH, e);
		}
	}
}
