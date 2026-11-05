package net.neurallink.network;

import java.util.UUID;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;
import net.neurallink.NeuralLink;

public record DisableChipPayload(UUID chipId) implements CustomPayload {
	public static final Id<DisableChipPayload> ID = new Id<>(NeuralLink.id("disable_chip"));
	public static final PacketCodec<RegistryByteBuf, DisableChipPayload> CODEC = PacketCodec.tuple(
			Uuids.PACKET_CODEC.cast(), DisableChipPayload::chipId,
			DisableChipPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
