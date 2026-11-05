package net.neurallink.chip;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.neurallink.config.NeuralLinkConfig;
import net.neurallink.entity.BodyEntity;
import net.neurallink.session.SessionManager;

/** host card in the visor menu. The server computes status and signal, the client only sorts. */
public record ChipCard(UUID chipId, Identifier entityType, String name, float health, float maxHealth, BlockPos pos, int distance, int signal, Status status, Identifier held,
		boolean favorite, boolean current) {
	public enum Status {
		ONLINE, OUT_OF_RANGE, NO_SIGNAL, LOST;

		public boolean connectable() {
			return this == ONLINE || this == OUT_OF_RANGE;
		}

		static final PacketCodec<ByteBuf, Status> CODEC = PacketCodecs.VAR_INT.xmap(index -> values()[index], Status::ordinal);
	}

	public static final PacketCodec<RegistryByteBuf, ChipCard> CODEC = PacketCodec.of(ChipCard::write, ChipCard::read);

	private void write(RegistryByteBuf buf) {
		Uuids.PACKET_CODEC.encode(buf, chipId);
		Identifier.PACKET_CODEC.encode(buf, entityType);
		PacketCodecs.STRING.encode(buf, name);
		PacketCodecs.FLOAT.encode(buf, health);
		PacketCodecs.FLOAT.encode(buf, maxHealth);
		BlockPos.PACKET_CODEC.encode(buf, pos);
		PacketCodecs.VAR_INT.encode(buf, distance);
		PacketCodecs.VAR_INT.encode(buf, signal);
		Status.CODEC.encode(buf, status);
		Identifier.PACKET_CODEC.encode(buf, held);
		PacketCodecs.BOOLEAN.encode(buf, favorite);
		PacketCodecs.BOOLEAN.encode(buf, current);
	}

	private static ChipCard read(RegistryByteBuf buf) {
		return new ChipCard(Uuids.PACKET_CODEC.decode(buf), Identifier.PACKET_CODEC.decode(buf), PacketCodecs.STRING.decode(buf),
				PacketCodecs.FLOAT.decode(buf), PacketCodecs.FLOAT.decode(buf), BlockPos.PACKET_CODEC.decode(buf),
				PacketCodecs.VAR_INT.decode(buf), PacketCodecs.VAR_INT.decode(buf), Status.CODEC.decode(buf), Identifier.PACKET_CODEC.decode(buf),
				PacketCodecs.BOOLEAN.decode(buf), PacketCodecs.BOOLEAN.decode(buf));
	}

	/** In a session the signal is measured from the body, as in the HUD. */
	public static List<ChipCard> forPlayer(ServerPlayerEntity player) {
		Optional<BodyEntity> body = SessionManager.bodyOf(player);
		Vec3d origin = body.map(BodyEntity::getPos).orElse(player.getPos());
		RegistryKey<World> world = body.map(found -> found.getWorld().getRegistryKey()).orElse(player.getWorld().getRegistryKey());
		UUID host = SessionManager.hostOf(player).orElse(null);
		return ChipRegistry.forOperator(player).stream()
				.map(entry -> of(entry, player, origin, world, entry.mobId().equals(host)))
				.toList();
	}

	private static ChipCard of(ChipEntry entry, ServerPlayerEntity player, Vec3d origin, RegistryKey<World> world, boolean current) {
		boolean sameWorld = entry.world().equals(world);
		int distance = sameWorld ? (int) Math.sqrt(entry.pos().getSquaredDistance(origin)) : 0;
		ServerWorld hostWorld = player.getServer().getWorld(entry.world());
		int signal = 0;
		if (sameWorld && !entry.lost()) {
			Entity mob = hostWorld == null ? null : hostWorld.getEntity(entry.mobId());
			// loaded host uses its own height; an unloaded one uses the stored block.
			signal = signal(distance, hostWorld, mob != null ? mob.getY() : entry.pos().getY());
		}
		return new ChipCard(entry.chipId(), entry.entityType(), entry.name(), entry.health(), entry.maxHealth(), entry.pos(),
				distance, signal, status(entry, player, sameWorld), entry.held(), entry.favorite() && !entry.lost(), current);
	}

	private static Status status(ChipEntry entry, ServerPlayerEntity player, boolean sameWorld) {
		if (entry.lost()) {
			return Status.LOST;
		}
		if (!sameWorld) {
			return Status.NO_SIGNAL;
		}
		ServerWorld world = player.getServerWorld();
		return world.getEntity(entry.mobId()) != null ? Status.ONLINE : Status.OUT_OF_RANGE;
	}

	/** 100 up to noise start, 0 at full noise. */
	public static int signal(int distance) {
		int start = NeuralLinkConfig.noiseStartDistance;
		int max = NeuralLinkConfig.noiseMaxDistance;
		return (int) MathHelper.clamp(100L - ((long) distance - start) * 100L / Math.max(1, max - start), 0L, 100L);
	}

	/**
	 * Distance signal with host height: below sea it loses up to DEPTH_PENALTY, above it gains up to ALTITUDE_BONUS. Nether,
	 * End and depthNoise off are left alone.
	 */
	public static int signal(int distance, World world, double y) {
		if (!NeuralLinkConfig.depthNoise || world == null || world.getRegistryKey() == World.NETHER || world.getRegistryKey() == World.END) {
			return signal(distance);
		}
		return signal(distance, world.getSeaLevel(), world.getBottomY(), y);
	}

	/** the arithmetic alone, for a given sea level and bottom. */
	public static int signal(int distance, int sea, int bottom, double y) {
		int base = signal(distance);
		double penalty = 0.0;
		double bonus = 0.0;
		if (y < sea) { // below bottom counts as bottom; no room under the sea means full penalty
			penalty = DEPTH_PENALTY * MathHelper.clamp((sea - y) / Math.max(1, sea - bottom), 0.0, 1.0);
		} else {
			bonus = ALTITUDE_BONUS * MathHelper.clamp((y - sea) / ALTITUDE_SPAN, 0.0, 1.0);
		}
		return (int) MathHelper.clamp(Math.round(base - penalty + bonus), 0L, 100L);
	}

	private static final int DEPTH_PENALTY = 50;
	private static final int ALTITUDE_BONUS = 25;
	private static final double ALTITUDE_SPAN = 192.0;
}
