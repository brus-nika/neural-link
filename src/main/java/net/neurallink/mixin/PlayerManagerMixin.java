package net.neurallink.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neurallink.chip.ChipRegistry;
import net.neurallink.chip.HostTracker;
import net.neurallink.network.ModNetworking;
import net.neurallink.session.SessionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric's DISCONNECT fires on Netty and can race the save; this runs on the server thread before savePlayerData. */
@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
	@Inject(method = "remove", at = @At("HEAD"))
	private void neurallink$endSession(ServerPlayerEntity player, CallbackInfo ci) {
		SessionManager.onDisconnect(player);
		ChipRegistry.get(player.getServer()).onOwnerLeft(player);
		HostTracker.forget(player.getUuid());
		ModNetworking.forget(player.getUuid());
	}
}
