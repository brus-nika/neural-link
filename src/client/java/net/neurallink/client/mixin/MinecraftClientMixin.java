package net.neurallink.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.neurallink.client.ClientSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "handleInputEvents", at = @At("HEAD"), cancellable = true)
	private void neurallink$sessionKeys(CallbackInfo ci) {
		if (ClientSession.isActive()) {
			ClientSession.handleKeys((MinecraftClient) (Object) this);
			ci.cancel();
		}
	}

	@Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
	private void neurallink$noSpectatorOutlines(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (ClientSession.isActive()) {
			cir.setReturnValue(entity.isGlowing());
		}
	}
}
