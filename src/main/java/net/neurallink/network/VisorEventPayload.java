package net.neurallink.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.neurallink.NeuralLink;

/** slot is only for SLOT_* kinds. */
public record VisorEventPayload(Kind kind, int slot) implements CustomPayload {
	public enum Kind {
		MOB_HIT, BODY_HIT, HOST_SWITCH, SIGNAL_LOST, SLOT_USED, SLOT_REJECTED
	}

	public static final Id<VisorEventPayload> ID = new Id<>(NeuralLink.id("visor_event"));
	public static final PacketCodec<RegistryByteBuf, VisorEventPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT.xmap(index -> Kind.values()[index], Kind::ordinal).cast(), VisorEventPayload::kind,
			PacketCodecs.VAR_INT, VisorEventPayload::slot,
			VisorEventPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
