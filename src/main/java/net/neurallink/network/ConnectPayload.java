package net.neurallink.network;

import java.util.UUID;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;
import net.neurallink.NeuralLink;

public record ConnectPayload(UUID chipId) implements CustomPayload {
	public static final Id<ConnectPayload> ID = new Id<>(NeuralLink.id("connect"));
	public static final PacketCodec<RegistryByteBuf, ConnectPayload> CODEC = PacketCodec.tuple(
			Uuids.PACKET_CODEC.cast(), ConnectPayload::chipId,
			ConnectPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
