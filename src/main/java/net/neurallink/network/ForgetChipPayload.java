package net.neurallink.network;

import java.util.UUID;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;
import net.neurallink.NeuralLink;

public record ForgetChipPayload(UUID chipId) implements CustomPayload {
	public static final Id<ForgetChipPayload> ID = new Id<>(NeuralLink.id("forget_chip"));
	public static final PacketCodec<RegistryByteBuf, ForgetChipPayload> CODEC = PacketCodec.tuple(
			Uuids.PACKET_CODEC.cast(), ForgetChipPayload::chipId,
			ForgetChipPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
