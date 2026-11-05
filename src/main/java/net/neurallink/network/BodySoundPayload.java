package net.neurallink.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.neurallink.NeuralLink;

/** a sound near the operator's distant body; played muffled, without direction. */
public record BodySoundPayload(RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch, long seed) implements CustomPayload {
	public static final Id<BodySoundPayload> ID = new Id<>(NeuralLink.id("body_sound"));
	private static final PacketCodec<io.netty.buffer.ByteBuf, SoundCategory> CATEGORY = PacketCodecs.VAR_INT.xmap(index -> SoundCategory.values()[index], SoundCategory::ordinal);
	public static final PacketCodec<RegistryByteBuf, BodySoundPayload> CODEC = PacketCodec.tuple(
			SoundEvent.ENTRY_PACKET_CODEC, BodySoundPayload::sound,
			CATEGORY, BodySoundPayload::category,
			PacketCodecs.FLOAT, BodySoundPayload::volume,
			PacketCodecs.FLOAT, BodySoundPayload::pitch,
			PacketCodecs.VAR_LONG, BodySoundPayload::seed,
			BodySoundPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
