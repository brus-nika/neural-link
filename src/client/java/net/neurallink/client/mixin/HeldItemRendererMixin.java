package net.neurallink.client.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.neurallink.client.ClientSession;
import net.neurallink.client.render.MobHands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	@Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
			at = @At("HEAD"), cancellable = true)
	private void neurallink$mobHands(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate vertices, ClientPlayerEntity player, int light, CallbackInfo ci) {
		if (ClientSession.isActive()) {
			MobHands.render((HeldItemRenderer) (Object) this, tickDelta, matrices, vertices, light);
			ci.cancel();
		}
	}
}
