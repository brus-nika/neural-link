package net.neurallink.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;

/** UI sounds only the operator hears, pitch spread so they don't get tiresome. */
public final class HudSounds {
	private static final Random RANDOM = Random.create();

	private HudSounds() {
	}

	/** returns the instance for sounds that may stop early. */
	public static SoundInstance play(SoundEvent sound, float volume) {
		MinecraftClient client = MinecraftClient.getInstance();
		float pitch = 0.97F + RANDOM.nextFloat() * 0.06F;
		SoundInstance instance = PositionedSoundInstance.master(sound, pitch, volume);
		client.getSoundManager().play(instance);
		return instance;
	}
}
