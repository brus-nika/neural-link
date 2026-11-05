package net.neurallink.network;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.neurallink.NeuralLink;

/** server config for the settings screen (read-only on a foreign server) and the body trance. */
public record ConfigPayload(boolean allowBosses, int maxChipsPerHelmet, int noiseStartDistance, int noiseMaxDistance, List<String> entityBlacklist,
		boolean depthNoise, int connectCooldownSeconds, int maxChipsPerPlayer, boolean spawnProtection, List<String> dimensionBlacklist,
		boolean bodyTrance) implements CustomPayload {
	public static final Id<ConfigPayload> ID = new Id<>(NeuralLink.id("config"));
	private static final PacketCodec<ByteBuf, List<String>> IDS = PacketCodecs.STRING.collect(PacketCodecs.toList());
	// too many fields for PacketCodec.tuple.
	public static final PacketCodec<RegistryByteBuf, ConfigPayload> CODEC = PacketCodec.of(ConfigPayload::write, ConfigPayload::read);

	private void write(RegistryByteBuf buf) {
		buf.writeBoolean(allowBosses);
		buf.writeVarInt(maxChipsPerHelmet);
		buf.writeVarInt(noiseStartDistance);
		buf.writeVarInt(noiseMaxDistance);
		IDS.encode(buf, entityBlacklist);
		buf.writeBoolean(depthNoise);
		buf.writeVarInt(connectCooldownSeconds);
		buf.writeVarInt(maxChipsPerPlayer);
		buf.writeBoolean(spawnProtection);
		IDS.encode(buf, dimensionBlacklist);
		buf.writeBoolean(bodyTrance);
	}

	private static ConfigPayload read(RegistryByteBuf buf) {
		return new ConfigPayload(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), IDS.decode(buf),
				buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), IDS.decode(buf), buf.readBoolean());
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
