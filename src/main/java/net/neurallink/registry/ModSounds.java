package net.neurallink.registry;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.neurallink.NeuralLink;

public final class ModSounds {
	public static final SoundEvent MENU_OPEN = register("menu_open");
	public static final SoundEvent CLICK = register("click");
	public static final SoundEvent PAIR = register("pair");
	public static final SoundEvent INSERT = register("insert");
	public static final SoundEvent EXTRACT = register("extract");
	public static final SoundEvent REFUSE = register("refuse");
	public static final SoundEvent CONNECT = register("connect");
	public static final SoundEvent DISCONNECT = register("disconnect");
	public static final SoundEvent LOST = register("lost");
	public static final SoundEvent NOISE = register("noise");
	public static final SoundEvent BODY_ALARM = register("body_alarm");
	public static final SoundEvent SWITCH = register("switch");
	public static final SoundEvent ABILITY_READY = register("ability_ready");
	public static final SoundEvent WHEEL_OPEN = register("wheel_open");
	public static final SoundEvent VISOR_BOOT = register("visor_boot");
	public static final SoundEvent SIGNAL_LOST = register("signal_lost");
	public static final SoundEvent WHEEL_TICK = register("wheel_tick");
	public static final SoundEvent WHEEL_SELECT = register("wheel_select");
	public static final SoundEvent WALL_BUMP = register("wall_bump");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = NeuralLink.id(name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	public static void register() {
	}
}
