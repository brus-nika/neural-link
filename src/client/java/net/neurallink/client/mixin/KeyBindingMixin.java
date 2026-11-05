package net.neurallink.client.mixin;

import java.util.Map;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.neurallink.client.NeuralLinkClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Slot 3 shares F with swap hands, so the slot reads the key directly. */
@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
	@Shadow
	@Final
	private static Map<InputUtil.Key, KeyBinding> KEY_TO_BINDINGS;

	@Inject(method = "updateKeysByCode", at = @At("TAIL"))
	private static void neurallink$keepVanillaKey(CallbackInfo ci) {
		KeyBinding swap = KeyBinding.byId("key.swapOffhand");
		InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(NeuralLinkClient.SLOT_3_KEY);
		if (swap != null && key.equals(KeyBindingHelper.getBoundKeyOf(swap)) && KEY_TO_BINDINGS.get(key) == NeuralLinkClient.SLOT_3_KEY) {
			KEY_TO_BINDINGS.put(key, swap);
		}
	}
}
