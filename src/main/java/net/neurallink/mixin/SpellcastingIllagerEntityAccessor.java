package net.neurallink.mixin;

import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.mob.SpellcastingIllagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Tracked value, not setSpell: its Spell enum is protected. */
@Mixin(SpellcastingIllagerEntity.class)
public interface SpellcastingIllagerEntityAccessor {
	@Accessor("SPELL")
	static TrackedData<Byte> neurallink$spell() {
		throw new AssertionError();
	}
}
