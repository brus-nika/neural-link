package net.neurallink.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.neurallink.chip.ChipLogic;
import net.neurallink.registry.ModAttachments;
import net.neurallink.session.SessionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No AI tick under control. Not setAiDisabled, that freezes the mob in the air. */
@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
	@ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
	private LivingEntity neurallink$spareOperator(LivingEntity target) {
		return target != null && ChipLogic.protects((MobEntity) (Object) this, target) ? null : target;
	}

	@Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
	private void neurallink$replaceAi(CallbackInfo ci) {
		if (SessionManager.applyControl((MobEntity) (Object) this)) {
			ci.cancel();
		}
	}

	/** holds the mob without the vanilla persistence flag so extraction has nothing to undo. */
	@ModifyReturnValue(method = "cannotDespawn", at = @At("RETURN"))
	private boolean neurallink$keepHost(boolean original) {
		return original || ((MobEntity) (Object) this).hasAttached(ModAttachments.CHIP);
	}
}
