package net.neurallink.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.neurallink.NeuralLink;

/** Never sent; listening on it proves the client's protocol. Rename the channel on any payload change. */
public record ProtocolPayload() implements CustomPayload {
	public static final Id<ProtocolPayload> ID = new Id<>(NeuralLink.id("protocol_8"));
	public static final PacketCodec<PacketByteBuf, ProtocolPayload> CODEC = PacketCodec.unit(new ProtocolPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
