package net.neurallink.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.Phase;
import net.minecraft.util.math.Vec3d;
import net.neurallink.chip.ChipLogic;
import net.neurallink.session.SessionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The dragon never calls tickNewAi, flight lives in tickMovement. We swap its destination for a point ahead of the
 * operator's gaze so the rest stays vanilla.
 */
@Mixin(EnderDragonEntity.class)
public abstract class EnderDragonEntityMixin {
	@Inject(method = "tickMovement", at = @At("HEAD"))
	private void neurallink$control(CallbackInfo ci) {
		EnderDragonEntity dragon = (EnderDragonEntity) (Object) this;
		if (!dragon.getWorld().isClient) {
			SessionManager.applyControl(dragon);
		}
	}

	@WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/boss/dragon/phase/Phase;getPathTarget()Lnet/minecraft/util/math/Vec3d;"))
	private Vec3d neurallink$flyAlongLook(Phase phase, Operation<Vec3d> original) {
		return SessionManager.lookTarget((EnderDragonEntity) (Object) this).orElseGet(() -> original.call(phase));
	}

	/** overrides LivingEntity.canTarget without super, so LivingEntityMixin never sees it. */
	@Inject(method = "canTarget(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
	private void neurallink$spareOperator(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (ChipLogic.protects((EnderDragonEntity) (Object) this, target)) {
			cir.setReturnValue(false);
		}
	}
}
