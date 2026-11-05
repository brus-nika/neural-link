package net.neurallink.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;

/** every mod advancement: the server grants it by name when the event happens. */
public class EventCriterion extends AbstractCriterion<EventCriterion.Conditions> {
	public void trigger(ServerPlayerEntity player, String event) {
		trigger(player, conditions -> conditions.event().equals(event));
	}

	@Override
	public Codec<Conditions> getConditionsCodec() {
		return Conditions.CODEC;
	}

	public record Conditions(Optional<LootContextPredicate> player, String event) implements AbstractCriterion.Conditions {
		static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(Conditions::player),
				Codec.STRING.fieldOf("event").forGetter(Conditions::event)
		).apply(instance, Conditions::new));
	}
}
