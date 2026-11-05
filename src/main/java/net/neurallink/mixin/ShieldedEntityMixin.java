package net.neurallink.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.Entity;
import net.neurallink.session.SessionManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Invulnerable without the saved flag so a crash or portal can't leave it on the mob. */
@Mixin(Entity.class)
public abstract class ShieldedEntityMixin {
	@Inject(method = "isInvulnerable", at = @At("RETURN"), cancellable = true)
	private void neurallink$shieldedIsInvulnerable(CallbackInfoReturnable<Boolean> cir) {
		if (neurallink$isShielded()) {
			cir.setReturnValue(true);
		}
	}

	@ModifyExpressionValue(method = "isAlwaysInvulnerableTo", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;invulnerable:Z", opcode = Opcodes.GETFIELD))
	private boolean neurallink$shieldedAgainstDamage(boolean invulnerable) {
		return invulnerable || neurallink$isShielded();
	}

	@Unique
	private boolean neurallink$isShielded() {
		Entity self = (Entity) (Object) this;
		return !self.getWorld().isClient && SessionManager.isShielded(self);
	}
}
