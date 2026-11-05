package net.neurallink.session;

import java.util.Comparator;

import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/** keeps host, body and waiting-host chunks loaded while the player is elsewhere. */
final class ChunkTickets {
	/** Self-expiring so a forgotten ticket can't pin a chunk forever. */
	private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("neurallink", Comparator.comparingLong(ChunkPos::toLong), 60);
	private static final int RADIUS = 3;

	private ChunkTickets() {
	}

	static void keepLoaded(ServerWorld world, BlockPos pos) {
		ChunkPos chunk = new ChunkPos(pos);
		world.getChunkManager().addTicket(TICKET, chunk, RADIUS, chunk);
	}
}
