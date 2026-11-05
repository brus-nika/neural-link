package net.neurallink.network;

import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.neurallink.NeuralLink;

/** Everything the HUD shows, computed server-side. Vertical: Space/Shift moving the host right now. */
public record HudStatePayload(float health, float maxHealth, int signal, List<Slot> slots, boolean vertical) implements CustomPayload {
	public static final Id<HudStatePayload> ID = new Id<>(NeuralLink.id("hud_state"));

	/** icon is an item or entity id; entities draw as 3D models. */
	public record Slot(int index, Identifier icon, int cooldownLeft, int cooldownTotal, boolean active) {
		private static final PacketCodec<RegistryByteBuf, Slot> CODEC = PacketCodec.tuple(
				PacketCodecs.VAR_INT, Slot::index,
				Identifier.PACKET_CODEC.cast(), Slot::icon,
				PacketCodecs.VAR_INT, Slot::cooldownLeft,
				PacketCodecs.VAR_INT, Slot::cooldownTotal,
				PacketCodecs.BOOLEAN, Slot::active,
				Slot::new);
	}

	public static final PacketCodec<RegistryByteBuf, HudStatePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT, HudStatePayload::health,
			PacketCodecs.FLOAT, HudStatePayload::maxHealth,
			PacketCodecs.VAR_INT, HudStatePayload::signal,
			Slot.CODEC.collect(PacketCodecs.toList()), HudStatePayload::slots,
			PacketCodecs.BOOLEAN, HudStatePayload::vertical,
			HudStatePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
