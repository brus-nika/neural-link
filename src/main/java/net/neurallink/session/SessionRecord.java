package net.neurallink.session;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

/** everything to bring the player back to the body, even after a crash. */
public record SessionRecord(UUID player, UUID mob, RegistryKey<World> world, Vec3d pos, float yaw, float pitch, GameMode gameMode, boolean flying) {
	public static final Codec<SessionRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Uuids.CODEC.fieldOf("player").forGetter(SessionRecord::player),
			Uuids.CODEC.fieldOf("mob").forGetter(SessionRecord::mob),
			World.CODEC.fieldOf("world").forGetter(SessionRecord::world),
			Vec3d.CODEC.fieldOf("pos").forGetter(SessionRecord::pos),
			Codec.FLOAT.fieldOf("yaw").forGetter(SessionRecord::yaw),
			Codec.FLOAT.fieldOf("pitch").forGetter(SessionRecord::pitch),
			GameMode.CODEC.fieldOf("game_mode").forGetter(SessionRecord::gameMode),
			Codec.BOOL.optionalFieldOf("flying", false).forGetter(SessionRecord::flying)
	).apply(instance, SessionRecord::new));

	public SessionRecord(UUID player, UUID mob, RegistryKey<World> world, Vec3d pos, float yaw, float pitch, GameMode gameMode) {
		this(player, mob, world, pos, yaw, pitch, gameMode, false);
	}

	SessionRecord withPos(Vec3d newPos) {
		return new SessionRecord(player, mob, world, newPos, yaw, pitch, gameMode, flying);
	}

	SessionRecord withMob(UUID newMob) {
		return new SessionRecord(player, newMob, world, pos, yaw, pitch, gameMode, flying);
	}
}
