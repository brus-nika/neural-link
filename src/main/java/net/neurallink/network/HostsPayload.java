package net.neurallink.network;

import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;
import net.neurallink.NeuralLink;

/**
 * an operator's own loaded hosts for frames and arrows, plus the slot counter. Markers use block positions since the client
 * doesn't track far entities, and whole blocks keep it from resending on every shuffle.
 */
public record HostsPayload(List<Integer> ids, int active, int limit, List<Marker> markers) implements CustomPayload {
	public static final Id<HostsPayload> ID = new Id<>(NeuralLink.id("hosts"));

	public record Marker(int id, BlockPos pos, boolean body) {
		private static final PacketCodec<RegistryByteBuf, Marker> CODEC = PacketCodec.tuple(
				PacketCodecs.VAR_INT, Marker::id,
				BlockPos.PACKET_CODEC, Marker::pos,
				PacketCodecs.BOOLEAN, Marker::body,
				Marker::new);
	}

	public static final PacketCodec<RegistryByteBuf, HostsPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT.collect(PacketCodecs.toList()), HostsPayload::ids,
			PacketCodecs.VAR_INT, HostsPayload::active,
			PacketCodecs.VAR_INT, HostsPayload::limit,
			Marker.CODEC.collect(PacketCodecs.toList()), HostsPayload::markers,
			HostsPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
