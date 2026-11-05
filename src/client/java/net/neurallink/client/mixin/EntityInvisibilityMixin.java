package net.neurallink.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.AbstractTeam;
import net.neurallink.client.ClientSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In a session invisible entities stay hidden. The player's host always shows or a burrowed one vanishes on F5. */
@Mixin(Entity.class)
public abstract class EntityInvisibilityMixin {
	@Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
	private void neurallink$noSpectatorSight(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = MinecraftClient.getInstance();
		Entity self = (Entity) (Object) this;
		if (ClientSession.isActive() && player == client.player && self != client.getCameraEntity()) {
			AbstractTeam team = self.getScoreboardTeam();
			boolean teammate = team != null && player.getScoreboardTeam() == team && team.shouldShowFriendlyInvisibles();
			cir.setReturnValue(!teammate && self.isInvisible());
		}
	}
}
