package net.neurallink.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import net.neurallink.mixin.PlayerListS2CPacketAccessor;

/** Tab list shows the old mode, real spectator would reveal the player is in a mob. */
public final class TabMask {
	private TabMask() {
	}

	public static PlayerListS2CPacket forRecipient(PlayerListS2CPacket packet, ServerPlayerEntity recipient) {
		if (!packet.getActions().contains(PlayerListS2CPacket.Action.UPDATE_GAME_MODE)) {
			return packet;
		}
		boolean changed = false;
		List<PlayerListS2CPacket.Entry> entries = new ArrayList<>(packet.getEntries().size());
		for (PlayerListS2CPacket.Entry entry : packet.getEntries()) {
			Optional<GameMode> mode = entry.profileId().equals(recipient.getUuid()) ? Optional.empty() : SessionManager.savedGameMode(entry.profileId());
			if (mode.isPresent()) {
				changed = true;
				entries.add(new PlayerListS2CPacket.Entry(entry.profileId(), entry.profile(), entry.listed(), entry.latency(), mode.get(),
						entry.displayName(), entry.showHat(), entry.listOrder(), entry.chatSession()));
			} else {
				entries.add(entry);
			}
		}
		if (!changed) {
			return packet;
		}
		PlayerListS2CPacket masked = new PlayerListS2CPacket(packet.getActions(), List.of());
		((PlayerListS2CPacketAccessor) masked).neurallink$setEntries(entries);
		return masked;
	}
}
