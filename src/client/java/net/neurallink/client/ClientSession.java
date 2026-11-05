package net.neurallink.client;

import java.util.Arrays;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.neurallink.client.gui.HostWheelScreen;
import net.neurallink.client.gui.VisorScreen;
import net.neurallink.client.hud.AbilityPanel;
import net.neurallink.client.hud.DetectionFrames;
import net.neurallink.client.hud.HudSounds;
import net.neurallink.client.hud.SessionHud;
import net.neurallink.client.hud.VisorBoot;
import net.neurallink.client.hud.VisorEffects;
import net.neurallink.client.hud.VisorTransitions;
import net.neurallink.client.mixin.MinecraftClientInvoker;
import net.neurallink.client.render.MobHands;
import net.neurallink.client.render.SpawnWall;
import net.neurallink.control.ControlState;
import net.neurallink.network.ChipListPayload;
import net.neurallink.network.ExitPayload;
import net.neurallink.network.HudStatePayload;
import net.neurallink.network.InputPayload;
import net.neurallink.network.RequestChipsPayload;
import net.neurallink.network.SlotInputPayload;
import net.neurallink.registry.ModSounds;
import org.lwjgl.glfw.GLFW;

/** Client side of a session: input and camera turn locally, no server lag. */
public final class ClientSession {
	private static final int EXIT_COLLAPSE_TICKS = 5;
	private static final int WEAK_SIGNAL = 30;
	private static final int CRACKLE_INTERVAL_TICKS = 70;

	private static boolean active;
	private static int mobId = -1;
	private static boolean menuRequested;
	private static int ticksSinceRequest;
	private static int exitTicks;
	private static int crackleTicks;
	private static boolean verticalHintCounted;
	private static final boolean[] slotDown = new boolean[ControlState.SLOTS];
	private static InputPayload lastInput;

	private ClientSession() {
	}

	public static boolean isActive() {
		return active;
	}

	public static int mobId() {
		return active ? mobId : -1;
	}

	static void setState(boolean newActive, int newMobId) {
		boolean started = newActive && !active;
		boolean ended = !newActive && active;
		if (newActive && active && newMobId != mobId) {
			lastInput = null; // switch: new host has its own look so first input resends
		}
		active = newActive;
		mobId = newMobId;
		Arrays.fill(slotDown, false);
		if (started || ended) {
			exitTicks = 0;
		}
		if (started) {
			lastInput = null; // new session starts from the host's look so first input always goes out
			verticalHintCounted = false;
			SessionHud.reset();
			VisorTransitions.onSessionStart();
		} else if (ended) {
			AbilityPanel.clearIcons();
			SpawnWall.clear(); // next session gets its own zone from the server
			VisorTransitions.onSessionEnd();
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.currentScreen instanceof HostWheelScreen) {
				client.setScreen(null); // the wheel belongs to a session, from the body it'd reconnect
			}
			// vanilla drops the creeper/spider view only on camera change, and the host may vanish first.
			if (client.player != null) {
				client.setCameraEntity(client.player);
			}
		}
	}

	/** Opens only on the server's reply: no visor, no screen. In a session it's the host wheel. */
	static void onChipList(MinecraftClient client, ChipListPayload list) {
		if (client.currentScreen instanceof VisorScreen screen) {
			screen.update(list);
		} else if (client.currentScreen instanceof HostWheelScreen wheel) {
			wheel.update(list);
		} else if (menuRequested && client.currentScreen == null && exitTicks == 0 && !VisorTransitions.isLoading()) {
			client.setScreen(active ? new HostWheelScreen(list, isMenuKeyDown(client)) : new VisorScreen(list));
		}
		menuRequested = false;
	}

	/** after a setting change, toggles vanilla mob view on the current camera. */
	public static void applyMobVision(MinecraftClient client) {
		Entity camera = client.getCameraEntity();
		if (camera == null || camera == client.player) {
			return;
		}
		if (ClientConfig.get().mobVision) {
			client.gameRenderer.onCameraEntitySet(camera); // camera mixin keeps it first person
		} else {
			client.gameRenderer.clearPostProcessor();
		}
	}

	/** Operator's ground drive direction from the last input, zero when released. Phantoms and the dragon fly on anyway. */
	public static Vec3d moveIntent(Entity host) {
		InputPayload input = lastInput;
		float forward = input == null ? 0.0F : input.forward();
		float sideways = input == null ? 0.0F : input.sideways();
		if (host instanceof PhantomEntity || host instanceof EnderDragonEntity) {
			forward = 1.0F;
		}
		float yaw = (input == null ? host.getYaw() : input.yaw()) * MathHelper.RADIANS_PER_DEGREE;
		return new Vec3d(sideways * MathHelper.cos(yaw) - forward * MathHelper.sin(yaw), 0.0, forward * MathHelper.cos(yaw) + sideways * MathHelper.sin(yaw));
	}

	private static boolean isMenuKeyDown(MinecraftClient client) {
		return isDown(client, NeuralLinkClient.MENU_KEY);
	}

	static void tick(MinecraftClient client) {
		if (client.player == null) {
			// not on DISCONNECT: it runs on Netty and misses reconfiguration, which also drops the player.
			setState(false, -1);
			DetectionFrames.clear();
			VisorEffects.reset();
			VisorBoot.reset();
			return;
		}
		VisorBoot.tick(client);
		while (NeuralLinkClient.MENU_KEY.wasPressed()) {
			if (exitTicks == 0 && !VisorTransitions.isLoading()) { // collapsing to the body or a connect underway
				menuRequested = true;
				ClientPlayNetworking.send(new RequestChipsPayload());
			}
		}
		if ((client.currentScreen instanceof VisorScreen || client.currentScreen instanceof HostWheelScreen) && ++ticksSinceRequest >= 20) {
			ticksSinceRequest = 0;
			ClientPlayNetworking.send(new RequestChipsPayload());
		}
		if (!active) {
			while (NeuralLinkClient.EXIT_KEY.wasPressed()) {
				// a press outside a session would pile up and close the next one at once.
			}
			return;
		}
		while (NeuralLinkClient.EXIT_KEY.wasPressed()) {
			if (exitTicks == 0) {
				exitTicks = EXIT_COLLAPSE_TICKS;
				VisorTransitions.beginCollapse();
				ClientConfig.get().exitHintUses++;
				ClientConfig.save();
			}
		}
		// Lets the image collapse before the server returns the player.
		if (exitTicks > 0 && --exitTicks == 0) {
			ClientPlayNetworking.send(new ExitPayload());
		}
		HudStatePayload hud = SessionHud.state();
		if (hud != null && hud.signal() < WEAK_SIGNAL && ++crackleTicks >= CRACKLE_INTERVAL_TICKS) {
			crackleTicks = 0;
			HudSounds.play(ModSounds.NOISE, 0.25F);
		}
		// a far mob can arrive after the camera packet and vanilla won't switch.
		Entity mob = client.world.getEntityById(mobId);
		if (mob != null && client.getCameraEntity() != mob) {
			client.setCameraEntity(mob);
		}
		if (mob instanceof LivingEntity living) {
			MobHands.tick(living);
		}
		GameOptions options = client.options;
		// Menu or search open: host stands. While a far host loads the old one is still driven.
		boolean free = client.currentScreen == null && !VisorTransitions.coversView();
		float forward = free ? axis(options.forwardKey, options.backKey) : 0.0F;
		float sideways = free ? axis(options.leftKey, options.rightKey) : 0.0F;
		InputPayload input = new InputPayload(forward, sideways, free && options.jumpKey.isPressed(), free && options.sneakKey.isPressed(),
				client.player.getYaw(), client.player.getPitch());
		if (!input.equals(lastInput)) {
			lastInput = input;
			ClientPlayNetworking.send(input);
		}
		// a session where Space or Shift actually moved the host counts as one hint use.
		if (!verticalHintCounted && free && hud != null && hud.vertical() && (input.jump() || input.sneak())) {
			verticalHintCounted = true;
			ClientConfig.get().verticalHintUses++;
			ClientConfig.save();
		}
		sendSlot(0, free && options.attackKey.isPressed());
		sendSlot(1, free && isDown(client, NeuralLinkClient.SLOT_2_KEY));
		sendSlot(2, free && isDown(client, NeuralLinkClient.SLOT_3_KEY));
	}

	private static void sendSlot(int slot, boolean pressed) {
		if (pressed != slotDown[slot]) {
			slotDown[slot] = pressed;
			ClientPlayNetworking.send(new SlotInputPayload(slot, pressed));
		}
	}

	private static boolean isDown(MinecraftClient client, KeyBinding binding) {
		InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
		long window = client.getWindow().getHandle();
		return switch (key.getCategory()) {
			case MOUSE -> GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS;
			case KEYSYM -> key.getCode() != InputUtil.UNKNOWN_KEY.getCode() && InputUtil.isKeyPressed(window, key.getCode());
			// Raw read needs a key token, scancode-only keys have none.
			case SCANCODE -> binding.isPressed();
		};
	}

	private static float axis(KeyBinding positive, KeyBinding negative) {
		return (positive.isPressed() ? 1.0F : 0.0F) - (negative.isPressed() ? 1.0F : 0.0F);
	}

	/** replaces handleInputEvents in a session: no player actions, chat and camera view stay. */
	public static void handleKeys(MinecraftClient client) {
		GameOptions options = client.options;
		while (options.togglePerspectiveKey.wasPressed()) {
			boolean wasFirstPerson = options.getPerspective().isFirstPerson();
			options.setPerspective(options.getPerspective().next());
			// As in vanilla mob view is first person only, else it sticks after F5.
			if (wasFirstPerson != options.getPerspective().isFirstPerson()) {
				client.gameRenderer.onCameraEntitySet(options.getPerspective().isFirstPerson() ? client.getCameraEntity() : null);
			}
		}
		if (options.chatKey.wasPressed()) {
			((MinecraftClientInvoker) client).neurallink$openChatScreen("");
		} else if (options.commandKey.wasPressed()) {
			((MinecraftClientInvoker) client).neurallink$openChatScreen("/");
		}
		for (KeyBinding key : options.hotbarKeys) {
			drain(key);
		}
		drain(options.attackKey);
		drain(options.useKey);
		drain(options.pickItemKey);
		drain(options.inventoryKey);
		drain(options.dropKey);
		drain(options.swapHandsKey);
		drain(options.advancementsKey);
		drain(options.socialInteractionsKey);
		drain(options.smoothCameraKey);
	}

	private static void drain(KeyBinding key) {
		while (key.wasPressed()) {
			// otherwise queued presses fire after the session ends.
		}
	}
}
