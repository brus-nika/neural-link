package net.neurallink.client.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.util.hit.HitResult;
import net.neurallink.client.ClientSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Spectators get no crosshair, but a session aims with it. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Inject(method = "shouldRenderSpectatorCrosshair", at = @At("HEAD"), cancellable = true)
	private void neurallink$crosshairInSession(HitResult hitResult, CallbackInfoReturnable<Boolean> cir) {
		if (ClientSession.isActive()) {
			cir.setReturnValue(true);
		}
	}
}
