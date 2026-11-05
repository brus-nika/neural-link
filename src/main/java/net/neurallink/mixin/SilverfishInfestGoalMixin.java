package net.neurallink.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.InfestedBlock;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.neurallink.registry.ModAttachments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** infesting discards the silverfish and the implant, so a host only wanders. */
@Mixin(targets = "net.minecraft.entity.mob.SilverfishEntity$WanderAndInfestGoal")
public abstract class SilverfishInfestGoalMixin extends WanderAroundGoal {
	protected SilverfishInfestGoalMixin(PathAwareEntity mob, double speed) {
		super(mob, speed);
	}

	@Redirect(method = "canStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/InfestedBlock;isInfestable(Lnet/minecraft/block/BlockState;)Z"))
	private boolean neurallink$hostStaysOut(BlockState state) {
		return !this.mob.hasAttached(ModAttachments.CHIP) && InfestedBlock.isInfestable(state);
	}
}
