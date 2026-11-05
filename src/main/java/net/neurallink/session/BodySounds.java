package net.neurallink.session;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldEvents;
import net.neurallink.network.BodySoundPayload;

/**
 * Sounds near the body reach the operator at the host. Vanilla sends sounds only to players near the source, so we send him
 * a copy, played quieter, lower and without direction.
 */
public final class BodySounds {
	/** how near the body a sound must be. */
	public static final double RADIUS = 8.0;
	/** Vanilla's explosion packet range; the client makes the sound from it. */
	public static final double EXPLOSION_RANGE = 64.0;
	/** vanilla's world event range, the client makes the sound from it. */
	public static final double WORLD_EVENT_RANGE = 64.0;

	private BodySounds() {
	}

	/**
	 * Operators with the body within {@link #RADIUS} but outside {@code audible}; anyone inside already heard it. The source
	 * hears it too.
	 */
	public static List<ServerPlayerEntity> listeners(ServerWorld world, PlayerEntity source, Vec3d pos, double audible) {
		List<ServerPlayerEntity> listeners = List.of();
		for (ControlSession session : SessionManager.live()) {
			ServerPlayerEntity operator = session.player;
			if (operator != source && session.body.getWorld() == world && session.body.squaredDistanceTo(pos) <= RADIUS * RADIUS
					&& (operator.getWorld() != world || operator.squaredDistanceTo(pos) > audible * audible)) {
				if (listeners.isEmpty()) {
					listeners = new ArrayList<>(1);
				}
				listeners.add(operator);
			}
		}
		return listeners;
	}

	/** runs for every server sound; no session means no one to copy to. */
	public static boolean anyone() {
		return !SessionManager.live().isEmpty();
	}

	/** A world event's client sound; {@link #audible} is capped by vanilla's send range. */
	public record WorldEventSound(SoundEvent sound, SoundCategory category, float volume, float pitch) {
		public double audible() {
			return Math.min(WORLD_EVENT_RANGE, sound.getDistanceToTravel(volume));
		}
	}

	/** threatening world events with vanilla volume and pitch, null for the rest. */
	public static WorldEventSound worldEventSound(int event, int data) {
		return switch (event) {
			case WorldEvents.GHAST_WARNS -> new WorldEventSound(SoundEvents.ENTITY_GHAST_WARN, SoundCategory.HOSTILE, 10.0F, 1.0F);
			case WorldEvents.GHAST_SHOOTS -> new WorldEventSound(SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.HOSTILE, 10.0F, 1.0F);
			case WorldEvents.BLAZE_SHOOTS -> new WorldEventSound(SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 2.0F, 1.0F);
			case WorldEvents.ZOMBIE_ATTACKS_WOODEN_DOOR -> new WorldEventSound(SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.HOSTILE, 2.0F, 1.0F);
			case WorldEvents.ZOMBIE_ATTACKS_IRON_DOOR -> new WorldEventSound(SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.HOSTILE, 2.0F, 1.0F);
			case WorldEvents.ZOMBIE_BREAKS_WOODEN_DOOR -> new WorldEventSound(SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.HOSTILE, 2.0F, 1.0F);
			case WorldEvents.BLOCK_BROKEN -> {
				BlockState state = Block.getStateFromRawId(data);
				if (state.isAir()) {
					yield null;
				}
				BlockSoundGroup group = state.getSoundGroup();
				yield new WorldEventSound(group.getBreakSound(), SoundCategory.BLOCKS, (group.getVolume() + 1.0F) / 2.0F, group.getPitch() * 0.8F);
			}
			default -> null;
		};
	}

	public static void forwardWorldEvent(ServerWorld world, PlayerEntity source, int event, BlockPos pos, int data) {
		WorldEventSound sound = anyone() ? worldEventSound(event, data) : null;
		if (sound != null) {
			forward(world, source, Vec3d.ofCenter(pos), Registries.SOUND_EVENT.getEntry(sound.sound()), sound.category(), sound.volume(), sound.pitch(),
					null, sound.audible());
		}
	}

	/** A null seed is drawn from the world random, only if someone gets the copy. */
	public static void forward(ServerWorld world, PlayerEntity source, Vec3d pos, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume,
			float pitch, Long seed, double audible) {
		if (!anyone()) {
			return;
		}
		List<ServerPlayerEntity> listeners = listeners(world, source, pos, audible);
		if (listeners.isEmpty()) {
			return;
		}
		long drawn = seed != null ? seed : world.random.nextLong();
		for (ServerPlayerEntity operator : listeners) {
			if (ServerPlayNetworking.canSend(operator, BodySoundPayload.ID)) {
				ServerPlayNetworking.send(operator, new BodySoundPayload(sound, category, volume, pitch, drawn));
			}
		}
	}
}
