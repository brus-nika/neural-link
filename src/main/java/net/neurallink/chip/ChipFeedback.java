package net.neurallink.chip;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.neurallink.network.ChipFeedbackPayload;
import net.neurallink.registry.ModParticles;
import net.neurallink.registry.ModSounds;

/** world effects go to everyone nearby, refusal sound and toast only to the operator. */
public final class ChipFeedback {
	private ChipFeedback() {
	}

	public static void send(PlayerEntity player, ChipResult result, Entity target) {
		if (!(player instanceof ServerPlayerEntity operator)) {
			return;
		}
		if (ServerPlayNetworking.canSend(operator, ChipFeedbackPayload.ID)) {
			ServerPlayNetworking.send(operator, new ChipFeedbackPayload(result));
		}
		switch (result) {
			case BOUND, REBOUND -> burst(operator, ModParticles.LINK, 8, ModSounds.PAIR, 0.15); // at the feet, at eye level the rings block the view
			case INSERTED -> burst(target != null ? target : operator, ModParticles.SPARK, 16, ModSounds.INSERT, 0.5);
			case EXTRACTED -> burst(target != null ? target : operator, ModParticles.SPARK, 10, ModSounds.EXTRACT, 0.5);
			case DISABLED -> {
				if (target != null) {
					burst(target, ModParticles.SPARK, 12, ModSounds.LOST, 0.5);
				}
			}
			default -> {
				if (target != null) {
					ServerWorld world = operator.getServerWorld();
					world.spawnParticles(ModParticles.REFUSE, true, false, target.getX(), target.getBoundingBox().maxY + 0.6, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				}
			}
		}
	}

	private static void burst(Entity at, ParticleEffect particle, int count, SoundEvent sound, double height) {
		if (at.getWorld() instanceof ServerWorld world) {
			world.spawnParticles(particle, at.getX(), at.getBodyY(height), at.getZ(), count, at.getWidth() * 0.4, at.getHeight() * 0.4, at.getWidth() * 0.4, 0.04);
			float pitch = 0.95F + world.random.nextFloat() * 0.1F;
			world.playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundCategory.PLAYERS, 0.8F, pitch);
		}
	}
}
