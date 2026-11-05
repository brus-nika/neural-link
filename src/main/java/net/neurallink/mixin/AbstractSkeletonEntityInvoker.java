package net.neurallink.mixin;

import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSkeletonEntity.class)
public interface AbstractSkeletonEntityInvoker {
	@Invoker("createArrowProjectile")
	PersistentProjectileEntity neurallink$createArrow(ItemStack arrow, float damageModifier, ItemStack shotFrom);
}
