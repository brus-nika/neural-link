package net.neurallink.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.neurallink.NeuralLink;

/** mobId since the mob may arrive after the camera packet. */
public record SessionStatePayload(boolean active, int mobId) implements CustomPayload {
	public static final Id<SessionStatePayload> ID = new Id<>(NeuralLink.id("session_state"));
	public static final PacketCodec<RegistryByteBuf, SessionStatePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOLEAN, SessionStatePayload::active,
			PacketCodecs.VAR_INT, SessionStatePayload::mobId,
			SessionStatePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
