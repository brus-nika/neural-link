package net.neurallink.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.neurallink.NeuralLink;

/** Keys and rotation, sent on change. The server moves the mob. */
public record InputPayload(float forward, float sideways, boolean jump, boolean sneak, float yaw, float pitch) implements CustomPayload {
	public static final Id<InputPayload> ID = new Id<>(NeuralLink.id("input"));
	public static final PacketCodec<RegistryByteBuf, InputPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.FLOAT, InputPayload::forward,
			PacketCodecs.FLOAT, InputPayload::sideways,
			PacketCodecs.BOOLEAN, InputPayload::jump,
			PacketCodecs.BOOLEAN, InputPayload::sneak,
			PacketCodecs.FLOAT, InputPayload::yaw,
			PacketCodecs.FLOAT, InputPayload::pitch,
			InputPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
