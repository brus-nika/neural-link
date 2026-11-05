package net.neurallink.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.math.random.Random;
import net.neurallink.network.BodySoundPayload;

/** body sound in the host's head: no direction, quieter and lower so it reads as muffled and far. */
public final class BodySoundPlayer {
	public static final int DEFAULT_PERCENT = 40;
	/** lower as if through the skull. */
	private static final float PITCH = 0.8F;

	/** What we asked the sound manager for; instance volume and pitch need the sound loaded. */
	public record Played(SoundInstance sound, float volume, float pitch) {
	}

	/** for tests: last one played, null since reset. */
	private static Played last;
	/** For tests: copies received, played or not. */
	private static int received;

	private BodySoundPlayer() {
	}

	public static void play(BodySoundPayload payload) {
		received++;
		int percent = ClientConfig.get().bodySoundPercent;
		if (!ClientSession.isActive() || percent <= 0) {
			return; // session ended meanwhile, player hears it from the body himself
		}
		// above 1 volume is range, not loudness: an explosion's 4 would stay at full strength
		float volume = Math.min(payload.volume(), 1.0F) * percent / 100.0F;
		float pitch = payload.pitch() * PITCH;
		SoundInstance sound = new PositionedSoundInstance(payload.sound().value().id(), payload.category(), volume, pitch, Random.create(payload.seed()), false, 0,
				SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true);
		last = new Played(sound, volume, pitch);
		MinecraftClient.getInstance().getSoundManager().play(sound);
	}

	public static Played last() {
		return last;
	}

	public static int received() {
		return received;
	}

	public static void resetLast() {
		last = null;
	}
}
