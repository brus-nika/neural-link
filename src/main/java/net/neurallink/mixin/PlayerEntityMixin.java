package net.neurallink.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neurallink.session.SessionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shift leaves the camera in vanilla spectator but is a control key in a session. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	@Inject(method = "shouldDismount", at = @At("HEAD"), cancellable = true)
	private void neurallink$keepCamera(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayerEntity player && SessionManager.isInSession(player)) {
			cir.setReturnValue(false);
		}
	}
}
