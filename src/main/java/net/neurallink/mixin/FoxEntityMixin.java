package net.neurallink.mixin;

import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.item.ItemStack;
import net.neurallink.session.SessionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla looting runs outside the AI so a host would re-grab dropped items. Under control only the key picks up. */
@Mixin(FoxEntity.class)
public abstract class FoxEntityMixin {
	@Inject(method = "canPickupItem", at = @At("HEAD"), cancellable = true)
	private void neurallink$noLootUnderControl(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (SessionManager.isControlled((FoxEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
