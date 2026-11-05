package net.neurallink.mixin;

import net.minecraft.entity.mob.EndermiteEntity;
import net.neurallink.registry.ModAttachments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** the life timer reads isPersistent, not cannotDespawn, so a host needs its own stop. */
@Mixin(EndermiteEntity.class)
public abstract class EndermiteEntityMixin {
	@Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/mob/EndermiteEntity;isPersistent()Z"))
	private boolean neurallink$stopTimer(EndermiteEntity mite) {
		return mite.isPersistent() || mite.hasAttached(ModAttachments.CHIP);
	}
}
