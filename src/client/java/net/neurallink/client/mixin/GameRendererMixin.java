package net.neurallink.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import net.neurallink.client.ClientConfig;
import net.neurallink.client.ClientSession;
import net.neurallink.client.hud.EasterEggs;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	/** spectators get no hands but in a session the mob's items go there. */
	@WrapOperation(method = "renderHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;getCurrentGameMode()Lnet/minecraft/world/GameMode;"))
	private GameMode neurallink$handsInSession(ClientPlayerInteractionManager manager, Operation<GameMode> original) {
		return ClientSession.isActive() ? GameMode.SURVIVAL : original.call(manager);
	}

	/** Dinnerbone host: world view matrix rolls 180°. HUD and screens stay upright. */
	@WrapOperation(method = "renderWorld", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;rotation(Lorg/joml/Quaternionfc;)Lorg/joml/Matrix4f;"))
	private Matrix4f neurallink$upsideDownView(Matrix4f matrix, Quaternionfc rotation, Operation<Matrix4f> original) {
		Matrix4f view = original.call(matrix, rotation);
		return EasterEggs.flipped(MinecraftClient.getInstance()) ? view.rotateLocalZ(MathHelper.PI) : view;
	}

	/**
	 * vanilla's creeper/spider/enderman view is kept to first person, off with mob vision off. Not tied to the session since
	 * the camera packet may come first.
	 */
	@Inject(method = "onCameraEntitySet", at = @At("TAIL"))
	private void neurallink$plainMobView(Entity entity, CallbackInfo info) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (entity != null && entity != client.player && (!ClientConfig.get().mobVision || !client.options.getPerspective().isFirstPerson())) {
			((GameRenderer) (Object) this).clearPostProcessor();
		}
	}
}
