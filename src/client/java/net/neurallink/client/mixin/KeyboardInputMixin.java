package net.neurallink.client.mixin;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.neurallink.client.ClientSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** in a session movement keys steer the mob, the player stays put. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void neurallink$freezePlayer(CallbackInfo ci) {
		if (ClientSession.isActive()) {
			this.playerInput = PlayerInput.DEFAULT;
			this.movementForward = 0.0F;
			this.movementSideways = 0.0F;
			ci.cancel();
		}
	}
}
