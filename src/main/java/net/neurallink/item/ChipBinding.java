package net.neurallink.item;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Uuids;

public record ChipBinding(UUID chipId, UUID helmetId, UUID owner) {
	public static final Codec<ChipBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Uuids.CODEC.fieldOf("chip").forGetter(ChipBinding::chipId),
			Uuids.CODEC.fieldOf("helmet").forGetter(ChipBinding::helmetId),
			Uuids.CODEC.fieldOf("owner").forGetter(ChipBinding::owner)
	).apply(instance, ChipBinding::new));

	public static final PacketCodec<ByteBuf, ChipBinding> PACKET_CODEC = PacketCodec.tuple(
			Uuids.PACKET_CODEC, ChipBinding::chipId,
			Uuids.PACKET_CODEC, ChipBinding::helmetId,
			Uuids.PACKET_CODEC, ChipBinding::owner,
			ChipBinding::new);
}
