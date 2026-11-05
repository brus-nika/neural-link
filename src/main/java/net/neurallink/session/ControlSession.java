package net.neurallink.session;

import java.util.Optional;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neurallink.control.ControlState;
import net.neurallink.entity.BodyEntity;
import net.neurallink.network.HudStatePayload;
import net.neurallink.network.SpawnZonePayload;

/** a live session. Packets set input, the mob's tick applies it. Body and food snapshot survive a host change. */
final class ControlSession {
	final ServerPlayerEntity player;
	MobEntity mob;
	final BodyEntity body;
	SessionRecord record;
	ControlState state;
	boolean originalNoGravity;
	PhaseType<?> dragonPhase;
	/** A spectator still burns food on healing; end discards it. */
	final NbtCompound hunger = new NbtCompound();
	HudStatePayload lastHud;
	/** null until sent so every session tells the client its zone. */
	SpawnZonePayload lastZone;
	/** Zone found this tick, reused by the payload within it. */
	Optional<SpawnZone.Box> zone = Optional.empty();
	int zoneTick = -1;
	int lookLockUntil;
	/** connect tick; a switch keeps it. */
	final int startTick;

	ControlSession(ServerPlayerEntity player, MobEntity mob, BodyEntity body, SessionRecord record) {
		this.player = player;
		this.body = body;
		this.record = record;
		this.startTick = player.getServer().getTicks();
		attach(mob);
		player.getHungerManager().writeNbt(this.hunger);
	}

	/** Host state taken fresh, a switch brings a new mob. */
	void attach(MobEntity next) {
		mob = next;
		state = new ControlState(next);
		originalNoGravity = next.hasNoGravity();
		dragonPhase = next instanceof EnderDragonEntity dragon ? dragon.getPhaseManager().getCurrent().getType() : null;
		lastHud = null;
	}
}
