package net.neurallink.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import net.neurallink.client.ClientSession;
import net.neurallink.client.hud.EasterEggs;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera turns straight from the mouse, not the server mob position, so the view doesn't lag by ping. Also flips for
 * Dinnerbone.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"))
	private float neurallink$yaw(Entity entity, float tickDelta, Operation<Float> original) {
		MinecraftClient client = MinecraftClient.getInstance();
		return ClientSession.isActive() && client.player != null ? client.player.getYaw(tickDelta) : original.call(entity, tickDelta);
	}

	@WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"))
	private float neurallink$pitch(Entity entity, float tickDelta, Operation<Float> original) {
		MinecraftClient client = MinecraftClient.getInstance();
		return ClientSession.isActive() && client.player != null ? client.player.getPitch(tickDelta) : original.call(entity, tickDelta);
	}

	@Shadow
	@Final
	private Vector3f verticalPlane;
	@Shadow
	@Final
	private Vector3f diagonalPlane;

	/**
	 * Dinnerbone or Grumm host: the picture flips in the view matrix (GameRendererMixin) and camera rotation stays vanilla
	 * for billboards and particles. Up and side vectors turn here so the sound listener matches. Checked every frame.
	 */
	@Inject(method = "update", at = @At("TAIL"))
	private void neurallink$upsideDown(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo info) {
		boolean flipped = EasterEggs.flipped(MinecraftClient.getInstance());
		EasterEggs.onFrame(flipped);
		if (flipped) {
			verticalPlane.negate();
			diagonalPlane.negate();
		}
	}
}
