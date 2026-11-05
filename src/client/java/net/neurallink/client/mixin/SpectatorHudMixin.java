package net.neurallink.client.mixin;

import net.minecraft.client.gui.hud.SpectatorHud;
import net.neurallink.client.ClientSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** no spectator teleport menu in a session. */
@Mixin(SpectatorHud.class)
public abstract class SpectatorHudMixin {
	@Inject(method = "useSelectedCommand", at = @At("HEAD"), cancellable = true)
	private void neurallink$noMenuInSession(CallbackInfo ci) {
		if (ClientSession.isActive()) {
			ci.cancel();
		}
	}
}
