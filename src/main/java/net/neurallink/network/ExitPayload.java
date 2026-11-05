package net.neurallink.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.neurallink.NeuralLink;

public record ExitPayload() implements CustomPayload {
	public static final Id<ExitPayload> ID = new Id<>(NeuralLink.id("exit"));
	public static final PacketCodec<RegistryByteBuf, ExitPayload> CODEC = PacketCodec.unit(new ExitPayload());

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
