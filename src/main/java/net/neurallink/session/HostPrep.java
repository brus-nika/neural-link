package net.neurallink.session;

import net.minecraft.entity.CrossbowUser;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SpellcastingIllagerEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.neurallink.mixin.FoxEntityInvoker;
import net.neurallink.mixin.SpellcastingIllagerEntityAccessor;
import net.neurallink.registry.ModAttachments;

/** takes the host from its AI and gives it back. */
final class HostPrep {
	private HostPrep() {
	}

	static void prepare(MobEntity mob) {
		mob.stopRiding();
		mob.getNavigation().stop();
		mob.setTarget(null);
		if (mob instanceof WardenEntity) {
			mob.setPose(EntityPose.STANDING); // a dig/emerge pose never ends without AI
		}
		clearAiState(mob);
	}

	/** Goals that undo these don't run under control, so the state would stick all session. */
	private static void clearAiState(MobEntity mob) {
		mob.setAttacking(false);
		if (mob.isSleeping()) {
			mob.wakeUp();
		}
		if (mob instanceof FoxEntity fox) {
			((FoxEntityInvoker) fox).neurallink$stopSleeping(); // its isSleeping flag isn't touched by wakeUp
		} else if (mob instanceof CreeperEntity creeper && !creeper.isIgnited()) {
			creeper.setFuseSpeed(-1);
		} else if (mob instanceof GhastEntity ghast) {
			ghast.setShooting(false);
		} else if (mob instanceof SpellcastingIllagerEntity caster) {
			caster.getDataTracker().set(SpellcastingIllagerEntityAccessor.neurallink$spell(), (byte) 0);
		} else if (mob instanceof TameableEntity pet) {
			pet.setInSittingPose(false); // isSitting stays, release restores the pose
		}
	}

	static void release(ControlSession session) {
		MobEntity mob = session.mob;
		mob.setForwardSpeed(0.0F);
		mob.setSidewaysSpeed(0.0F);
		mob.setJumping(false);
		mob.setAttacking(false);
		mob.clearActiveItem();
		mob.setNoGravity(session.originalNoGravity);
		mob.removeAttached(ModAttachments.ORIGINAL_NO_GRAVITY);
		if (mob instanceof TameableEntity pet) {
			pet.setInSittingPose(pet.isSitting());
		}
		if (mob instanceof CrossbowUser user) {
			user.setCharging(false); // only crossbow cleanup on exit, a weapon swapped mid-charge has no profile
		}
		if (mob instanceof EnderDragonEntity dragon && dragon.isAlive() && session.dragonPhase != PhaseType.HOVER
				&& dragon.getPhaseManager().getCurrent().getType() != PhaseType.DYING) {
			dragon.getPhaseManager().setPhase(PhaseType.HOLDING_PATTERN); // not the stored phase: strafing lost its target and sitting would be mid-air
		}
	}
}
