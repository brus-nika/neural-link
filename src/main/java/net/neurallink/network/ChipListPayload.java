package net.neurallink.network;

import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.neurallink.NeuralLink;
import net.neurallink.chip.ChipCard;

/** Hosts of the worn visor; active and limit feed the slot counter. */
public record ChipListPayload(List<ChipCard> cards, int active, int limit) implements CustomPayload {
	public static final Id<ChipListPayload> ID = new Id<>(NeuralLink.id("chip_list"));
	public static final PacketCodec<RegistryByteBuf, ChipListPayload> CODEC = PacketCodec.tuple(
			ChipCard.CODEC.collect(PacketCodecs.toList()), ChipListPayload::cards,
			PacketCodecs.VAR_INT, ChipListPayload::active,
			PacketCodecs.VAR_INT, ChipListPayload::limit,
			ChipListPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
