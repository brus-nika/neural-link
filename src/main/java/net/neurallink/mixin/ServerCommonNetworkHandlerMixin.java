package net.neurallink.mixin;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.neurallink.session.TabMask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** not in changeGameMode: the mask must also reach players joining mid-session. */
@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonNetworkHandlerMixin {
	@ModifyVariable(method = "send", at = @At("HEAD"), argsOnly = true)
	private Packet<?> neurallink$maskTab(Packet<?> packet) {
		if (packet instanceof PlayerListS2CPacket list && (Object) this instanceof ServerPlayNetworkHandler handler) {
			return TabMask.forRecipient(list, handler.player);
		}
		return packet;
	}
}
