package net.neurallink.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.neurallink.client.ClientSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer", remap = false)
public abstract class IrisHandRendererMixin {
	/**
	 * under shaders only Iris draws hands and it refuses mob cameras and spectators. Name only, descriptor differs in dev
	 * and prod.
	 */
	@Inject(method = "canRender", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void neurallink$mobHands(CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientSession.isActive() && !client.gameRenderer.getCamera().isThirdPerson() && !client.options.hudHidden) {
			cir.setReturnValue(true);
		}
	}
}
