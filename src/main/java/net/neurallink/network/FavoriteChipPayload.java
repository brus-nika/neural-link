package net.neurallink.network;

import java.util.UUID;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;
import net.neurallink.NeuralLink;

/** Card star as a state, not a toggle, so repeats change nothing. */
public record FavoriteChipPayload(UUID chipId, boolean favorite) implements CustomPayload {
	public static final Id<FavoriteChipPayload> ID = new Id<>(NeuralLink.id("favorite_chip"));
	public static final PacketCodec<RegistryByteBuf, FavoriteChipPayload> CODEC = PacketCodec.tuple(
			Uuids.PACKET_CODEC.cast(), FavoriteChipPayload::chipId,
			PacketCodecs.BOOLEAN.cast(), FavoriteChipPayload::favorite,
			FavoriteChipPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
