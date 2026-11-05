package net.neurallink.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.neurallink.NeuralLink;

/** the operator's spawn zone, blocks inclusive, for the visor wall. Sent on change. */
public record SpawnZonePayload(boolean present, int minX, int minZ, int maxX, int maxZ) implements CustomPayload {
	public static final Id<SpawnZonePayload> ID = new Id<>(NeuralLink.id("spawn_zone"));
	public static final SpawnZonePayload NONE = new SpawnZonePayload(false, 0, 0, 0, 0);
	public static final PacketCodec<RegistryByteBuf, SpawnZonePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOLEAN, SpawnZonePayload::present,
			PacketCodecs.VAR_INT, SpawnZonePayload::minX,
			PacketCodecs.VAR_INT, SpawnZonePayload::minZ,
			PacketCodecs.VAR_INT, SpawnZonePayload::maxX,
			PacketCodecs.VAR_INT, SpawnZonePayload::maxZ,
			SpawnZonePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
