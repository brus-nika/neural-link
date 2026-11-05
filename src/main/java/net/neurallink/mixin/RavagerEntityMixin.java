package net.neurallink.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.RavagerEntity;
import net.neurallink.session.SessionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** a stun would freeze the host and end in a roar hitting its own body, so a blocked hit only pushes. */
@Mixin(RavagerEntity.class)
public abstract class RavagerEntityMixin {
	@Shadow
	private int roarTick;

	@Shadow
	private void knockBack(Entity entity) {
		throw new AssertionError();
	}

	@Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
	private void neurallink$noStun(LivingEntity target, CallbackInfo ci) {
		if (SessionManager.isControlled((RavagerEntity) (Object) this)) {
			ci.cancel();
			if (roarTick == 0) {
				knockBack(target);
				target.velocityModified = true;
			}
		}
	}
}
