package net.neurallink.client.mixin;

import net.minecraft.client.render.entity.CreakingEntityRenderer;
import net.minecraft.client.render.entity.state.CreakingEntityRenderState;
import net.minecraft.entity.mob.CreakingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A frozen host keeps its caught pose instead of vanilla's stance. */
@Mixin(CreakingEntityRenderer.class)
public abstract class CreakingEntityRendererMixin {
	@Inject(method = "updateRenderState(Lnet/minecraft/entity/mob/CreakingEntity;Lnet/minecraft/client/render/entity/state/CreakingEntityRenderState;F)V", at = @At("TAIL"))
	private void neurallink$keepFrozenPose(CreakingEntity creaking, CreakingEntityRenderState state, float tickDelta, CallbackInfo ci) {
		if (!creaking.isUnrooted() && !creaking.isActive()) {
			state.unrooted = true;
			// no interpolation: legs don't update so it would jitter.
			state.limbFrequency = creaking.limbAnimator.getPos();
			state.limbAmplitudeMultiplier = creaking.limbAnimator.getSpeed();
		}
	}
}
