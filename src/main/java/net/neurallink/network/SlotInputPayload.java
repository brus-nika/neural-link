package net.neurallink.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.neurallink.NeuralLink;

/** Slot: 0 left mouse, 1 R, 2 F. */
public record SlotInputPayload(int slot, boolean pressed) implements CustomPayload {
	public static final Id<SlotInputPayload> ID = new Id<>(NeuralLink.id("slot_input"));
	public static final PacketCodec<RegistryByteBuf, SlotInputPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, SlotInputPayload::slot,
			PacketCodecs.BOOLEAN, SlotInputPayload::pressed,
			SlotInputPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
