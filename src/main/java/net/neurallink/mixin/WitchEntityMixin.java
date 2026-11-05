package net.neurallink.mixin;

import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.potion.Potion;
import net.minecraft.registry.entry.RegistryEntry;
import net.neurallink.session.SessionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Drinking is decided in tickMovement, not the AI. Under control only on the key. */
@Mixin(WitchEntity.class)
public abstract class WitchEntityMixin {
	@ModifyVariable(method = "tickMovement", at = @At("STORE"))
	private RegistryEntry<Potion> neurallink$drinkOnKey(RegistryEntry<Potion> potion) {
		return SessionManager.isControlled((WitchEntity) (Object) this) ? null : potion;
	}
}
