package net.neurallink.mixin;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.mob.WardenEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** vanilla doesn't stop the dig animation of a burrowed warden, so its model would stay underground on return. */
@Mixin(WardenEntity.class)
public abstract class WardenEntityMixin {
	@Inject(method = "onTrackedDataSet", at = @At("TAIL"))
	private void neurallink$stopDigging(TrackedData<?> data, CallbackInfo ci) {
		WardenEntity warden = (WardenEntity) (Object) this;
		if (warden.diggingAnimationState == null) {
			return; // also called from the Entity constructor before fields are set
		}
		if (!warden.isInPose(EntityPose.DIGGING)) {
			warden.diggingAnimationState.stop();
		}
		if (!warden.isInPose(EntityPose.EMERGING)) {
			warden.emergingAnimationState.stop();
		}
	}
}
