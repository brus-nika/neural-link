package net.neurallink.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.neurallink.chip.ChipLogic;
import net.neurallink.entity.BodyEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Slimes hurt by touch but vanilla counts only players and iron golems, so the body counts as a player. */
@Mixin(SlimeEntity.class)
public abstract class SlimeEntityMixin {
	@Shadow
	protected abstract boolean canAttack();

	@Shadow
	protected abstract void damage(LivingEntity target);

	@Inject(method = "pushAwayFrom", at = @At("TAIL"))
	private void neurallink$hitBody(Entity entity, CallbackInfo ci) {
		if (entity instanceof BodyEntity body && canAttack() && !ChipLogic.protects((SlimeEntity) (Object) this, body)) {
			damage(body);
		}
	}
}
