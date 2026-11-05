package net.neurallink.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.ExplosionBehavior;
import net.neurallink.session.BodySounds;

/** Every path a server sound takes to clients: point, entity, world event, explosion packet. */
@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
	/** as vanilla's client explosion sound: 4x volume, low pitch. */
	private static final float EXPLOSION_VOLUME = 4.0F;
	private static final float EXPLOSION_PITCH = 0.7F;

	@Inject(method = "playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;FFJ)V", at = @At("TAIL"))
	private void neurallink$bodySound(PlayerEntity source, double x, double y, double z, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch,
			long seed, CallbackInfo ci) {
		if (BodySounds.anyone()) {
			BodySounds.forward((ServerWorld) (Object) this, source, new Vec3d(x, y, z), sound, category, volume, pitch, seed, sound.value().getDistanceToTravel(volume));
		}
	}

	@Inject(method = "playSoundFromEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/sound/SoundCategory;FFJ)V", at = @At("TAIL"))
	private void neurallink$bodySoundFromEntity(PlayerEntity source, Entity entity, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch, long seed,
			CallbackInfo ci) {
		if (BodySounds.anyone()) {
			BodySounds.forward((ServerWorld) (Object) this, source, entity.getPos(), sound, category, volume, pitch, seed, sound.value().getDistanceToTravel(volume));
		}
	}

	@Inject(method = "syncWorldEvent(Lnet/minecraft/entity/player/PlayerEntity;ILnet/minecraft/util/math/BlockPos;I)V", at = @At("TAIL"))
	private void neurallink$bodyWorldEvent(PlayerEntity source, int event, BlockPos pos, int data, CallbackInfo ci) {
		BodySounds.forwardWorldEvent((ServerWorld) (Object) this, source, event, pos, data);
	}

	@Inject(method = "createExplosion(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/damage/DamageSource;Lnet/minecraft/world/explosion/ExplosionBehavior;DDDFZLnet/minecraft/world/World$ExplosionSourceType;Lnet/minecraft/particle/ParticleEffect;Lnet/minecraft/particle/ParticleEffect;Lnet/minecraft/registry/entry/RegistryEntry;)V", at = @At("TAIL"))
	private void neurallink$bodyExplosion(Entity entity, DamageSource damageSource, ExplosionBehavior behavior, double x, double y, double z, float power, boolean createFire,
			World.ExplosionSourceType sourceType, ParticleEffect smallParticle, ParticleEffect largeParticle, RegistryEntry<SoundEvent> sound, CallbackInfo ci) {
		if (BodySounds.anyone()) {
			BodySounds.forward((ServerWorld) (Object) this, null, new Vec3d(x, y, z), sound, SoundCategory.BLOCKS, EXPLOSION_VOLUME, EXPLOSION_PITCH, null,
					BodySounds.EXPLOSION_RANGE);
		}
	}
}
