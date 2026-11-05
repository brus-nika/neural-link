package net.neurallink.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.mob.VexEntity;
import net.neurallink.session.SessionManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** a vex always phases through blocks; under control only on the toggle. */
@Mixin(VexEntity.class)
public abstract class VexEntityMixin {
	@WrapOperation(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/mob/VexEntity;noClip:Z", opcode = Opcodes.PUTFIELD))
	private void neurallink$phaseOnlyOnToggle(VexEntity vex, boolean noClip, Operation<Void> original) {
		original.call(vex, noClip && (vex.getWorld().isClient || !SessionManager.blocksPhasing(vex)));
	}
}
