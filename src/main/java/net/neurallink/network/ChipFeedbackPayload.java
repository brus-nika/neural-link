package net.neurallink.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.neurallink.NeuralLink;
import net.neurallink.chip.ChipResult;

public record ChipFeedbackPayload(ChipResult result) implements CustomPayload {
	public static final Id<ChipFeedbackPayload> ID = new Id<>(NeuralLink.id("chip_feedback"));
	public static final PacketCodec<RegistryByteBuf, ChipFeedbackPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT.xmap(index -> ChipResult.values()[index], ChipResult::ordinal).cast(), ChipFeedbackPayload::result,
			ChipFeedbackPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
