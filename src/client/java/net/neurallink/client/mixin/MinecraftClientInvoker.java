package net.neurallink.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface MinecraftClientInvoker {
	@Invoker("openChatScreen")
	void neurallink$openChatScreen(String text);

	/** Render layers bind the main framebuffer, so an off-screen capture briefly replaces it. */
	@Mutable
	@Accessor("framebuffer")
	void neurallink$setFramebuffer(Framebuffer framebuffer);
}
