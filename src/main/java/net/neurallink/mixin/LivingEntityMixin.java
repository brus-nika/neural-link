package net.neurallink.mixin;

import net.minecraft.entity.LivingEntity;
import net.neurallink.chip.ChipLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Goals and brains both check targets here. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "canTarget(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
	private void neurallink$spareOperator(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (ChipLogic.protects((LivingEntity) (Object) this, target)) {
			cir.setReturnValue(false);
		}
	}
}
