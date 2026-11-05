package net.neurallink.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.neurallink.client.ClientConfig;
import net.neurallink.client.ClientSession;

/**
 * Vanilla name jokes from inside: Dinnerbone or Grumm flips the operator's view, jeb_ makes the overlay rainbow.
 * Client-only, session-only, toggled by a setting.
 */
public final class EasterEggs {
	private static final float FLIP_FLASH = 0.8F;

	private static boolean wasFlipped;

	private EasterEggs() {
	}

	private static Entity host(MinecraftClient client) {
		if (!ClientSession.isActive() || !ClientConfig.get().easterEggs) {
			return null;
		}
		Entity camera = client.getCameraEntity();
		return camera != null && camera != client.player && camera.hasCustomName() ? camera : null;
	}

	/** same check as LivingEntityRenderer.shouldFlipUpsideDown for named mobs. */
	public static boolean flipped(MinecraftClient client) {
		Entity host = host(client);
		if (host == null) {
			return false;
		}
		String name = Formatting.strip(host.getName().getString());
		return "Dinnerbone".equals(name) || "Grumm".equals(name);
	}

	/** Same as the sheep wool renderer. */
	public static boolean rainbow(MinecraftClient client) {
		Entity host = host(client);
		return host != null && "jeb_".equals(host.getName().getString());
	}

	/** the flip happens in one frame under a noise burst. Called on camera update. */
	public static void onFrame(boolean flipped) {
		if (flipped != wasFlipped && ClientSession.isActive()) {
			VisorEffects.pulse(Util.getMeasuringTimeMs(), FLIP_FLASH);
		}
		wasFlipped = flipped;
	}
}
