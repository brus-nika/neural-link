package net.neurallink.mixin;

import net.minecraft.entity.mob.CreakingEntity;
import net.neurallink.session.SessionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A host freezes on the key, not under gaze. Vanilla frozen creakings always have lit eyes, so the client can tell them
 * apart.
 */
@Mixin(CreakingEntity.class)
public abstract class CreakingEntityMixin {
	@Inject(method = "shouldBeUnrooted", at = @At("HEAD"), cancellable = true)
	private void neurallink$rootOnKey(CallbackInfoReturnable<Boolean> cir) {
		CreakingEntity creaking = (CreakingEntity) (Object) this;
		if (SessionManager.isControlled(creaking)) {
			creaking.setActive(false);
			cir.setReturnValue(!SessionManager.isFrozen(creaking));
		}
	}

	/** legs stop in place instead of settling into the stance. */
	@Inject(method = "updateLimbs", at = @At("HEAD"), cancellable = true)
	private void neurallink$holdPose(float posDelta, CallbackInfo ci) {
		CreakingEntity creaking = (CreakingEntity) (Object) this;
		if (!creaking.isUnrooted() && !creaking.isActive()) {
			ci.cancel();
		}
	}
}
