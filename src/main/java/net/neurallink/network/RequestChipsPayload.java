package net.neurallink.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.neurallink.NeuralLink;

/** sent on menu open, then every second while open. */
public record RequestChipsPayload() implements CustomPayload {
	public static final Id<RequestChipsPayload> ID = new Id<>(NeuralLink.id("request_chips"));
	public static final PacketCodec<RegistryByteBuf, RequestChipsPayload> CODEC = PacketCodec.unit(new RequestChipsPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
