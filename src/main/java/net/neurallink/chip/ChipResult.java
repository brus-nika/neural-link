package net.neurallink.chip;

import java.util.Locale;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/** outcome of a chip action or connect; toast only when the reason needs words. */
public enum ChipResult {
	BOUND(false), REBOUND(false), INSERTED(false), EXTRACTED(false), DISABLED(false), CONNECTED(false), CONNECTING(false),
	NEED_HELMET(true), ALREADY_BOUND(false), NOT_BOUND(true), NOT_OWNER(false), LIMIT(false), INVALID_TARGET(false), BOSSES_DISABLED(true), NOT_PET_OWNER(false), ALREADY_CHIPPED(false), CHIP_IN_USE(false),
	UNKNOWN_CHIP(true), WRONG_HELMET(true), ALREADY_CONNECTED(true), BUSY(true), NO_SIGNAL(true), NOT_FOUND(true), UNAVAILABLE(true), FOREIGN_LIMIT(true),
	/** Shared connect/switch cooldown: sound only. */
	COOLDOWN(false), FORBIDDEN_DIMENSION(true), FORBIDDEN_HOST(true), SPAWN_PROTECTED(true),
	/** admin commands: the player is told who did it. */
	ADMIN_KICKED(true), ADMIN_DISABLED(true);

	private final boolean toast;

	ChipResult(boolean toast) {
		this.toast = toast;
	}

	public boolean isToast() {
		return toast;
	}

	public String translationKey() {
		return "message.neurallink." + name().toLowerCase(Locale.ROOT);
	}

	public void notify(PlayerEntity player) {
		notify(player, null);
	}

	public void notify(PlayerEntity player, Entity target) {
		ChipFeedback.send(player, this, target);
	}
}
