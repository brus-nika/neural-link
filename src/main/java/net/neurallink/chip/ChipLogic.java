package net.neurallink.chip;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import net.neurallink.advancement.Achievements;
import net.neurallink.config.NeuralLinkConfig;
import net.neurallink.entity.BodyEntity;
import net.neurallink.item.ChipBinding;
import net.neurallink.item.ChipItem;
import net.neurallink.registry.ModAttachments;
import net.neurallink.registry.ModComponents;
import net.neurallink.registry.ModEntities;
import net.neurallink.registry.ModItems;
import net.neurallink.session.EndReason;
import net.neurallink.session.SessionManager;
import net.neurallink.session.SpawnZone;

public final class ChipLogic {
	private static final int ACHIEVEMENT_CHIPS = 8;
	private static final int NESTING = 4; // bundles nest without limit

	private ChipLogic() {
	}

	public static ItemStack wornHelmet(PlayerEntity player) {
		ItemStack head = player.getEquippedStack(EquipmentSlot.HEAD);
		return head.isOf(ModItems.NEURAL_HELMET) ? head : ItemStack.EMPTY;
	}

	/** anywhere the player still has the helmet, nested bundles and shulkers included. */
	public static boolean holdsHelmet(PlayerEntity player, UUID helmetId) {
		if (keepsHelmet(player, helmetId)) {
			return true;
		}
		// Crafting grids and anvils return items on close.
		for (Slot slot : player.currentScreenHandler.slots) {
			if (containsHelmet(slot.getStack(), helmetId, NESTING)) {
				return true;
			}
		}
		// a server-opened screen leaves the inventory grid and cursor off the new screen.
		RecipeInputInventory grid = player.playerScreenHandler.getCraftingInput();
		for (int i = 0; i < grid.size(); i++) {
			if (containsHelmet(grid.getStack(i), helmetId, NESTING)) {
				return true;
			}
		}
		return containsHelmet(player.currentScreenHandler.getCursorStack(), helmetId, NESTING)
				|| containsHelmet(player.playerScreenHandler.getCursorStack(), helmetId, NESTING);
	}

	/** Only what's saved with the player: cursor and grids drop on leaving. */
	public static boolean keepsHelmet(PlayerEntity player, UUID helmetId) {
		PlayerInventory inventory = player.getInventory();
		for (int i = 0; i < inventory.size(); i++) {
			if (containsHelmet(inventory.getStack(i), helmetId, NESTING)) {
				return true;
			}
		}
		EnderChestInventory enderChest = player.getEnderChestInventory();
		for (int i = 0; i < enderChest.size(); i++) {
			if (containsHelmet(enderChest.getStack(i), helmetId, NESTING)) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsHelmet(ItemStack stack, UUID helmetId, int depth) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.isOf(ModItems.NEURAL_HELMET) && helmetId.equals(stack.get(ModComponents.HELMET_ID))) {
			return true;
		}
		if (depth == 0) {
			return false;
		}
		ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
		if (container != null && container.streamNonEmpty().anyMatch(inner -> containsHelmet(inner, helmetId, depth - 1))) {
			return true;
		}
		BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
		return bundle != null && bundle.stream().anyMatch(inner -> containsHelmet(inner, helmetId, depth - 1));
	}

	/** the id appears at first binding, so fresh helmets have none. */
	private static UUID helmetId(ItemStack helmet) {
		UUID id = helmet.get(ModComponents.HELMET_ID);
		if (id == null) {
			id = UUID.randomUUID();
			helmet.set(ModComponents.HELMET_ID, id);
		}
		return id;
	}

	/** a chipped mob won't attack its operator or his body. */
	public static boolean protects(LivingEntity mob, LivingEntity target) {
		UUID operator = target instanceof BodyEntity body ? body.getOwner().orElse(null) : target instanceof PlayerEntity ? target.getUuid() : null;
		UUID chipId = operator == null || mob.getServer() == null ? null : mob.getAttached(ModAttachments.CHIP);
		return chipId != null && ChipRegistry.get(mob.getServer()).find(chipId).filter(entry -> entry.owner().equals(operator)).isPresent();
	}

	public static ChipResult bind(PlayerEntity player, ItemStack chip) {
		ItemStack helmet = wornHelmet(player);
		if (helmet.isEmpty()) {
			return ChipResult.NEED_HELMET;
		}
		ChipBinding binding = chip.get(ModComponents.CHIP_BINDING);
		if (binding != null && !ownsChip(player, binding)) {
			return ChipResult.NOT_OWNER;
		}
		UUID helmetId = helmetId(helmet);
		if (binding != null && binding.helmetId().equals(helmetId)) {
			return ChipResult.ALREADY_BOUND;
		}
		ChipRegistry registry = ChipRegistry.get(player.getServer());
		registry.claimHelmet(helmetId, player.getUuid());
		if (registry.activeCount(helmetId) >= NeuralLinkConfig.maxChipsPerHelmet) {
			return limitRefusal(registry, helmetId, player);
		}
		if (overPlayerLimit(registry, player)) {
			return ChipResult.LIMIT;
		}
		if (binding != null) {
			chip.set(ModComponents.CHIP_BINDING, new ChipBinding(binding.chipId(), helmetId, player.getUuid()));
			return ChipResult.REBOUND;
		}
		ChipBinding created = new ChipBinding(UUID.randomUUID(), helmetId, player.getUuid());
		if (chip.getCount() == 1) {
			chip.set(ModComponents.CHIP_BINDING, created); // in place: vanilla overwrites a replaced hand stack
		} else {
			ItemStack bound = chip.split(1);
			bound.set(ModComponents.CHIP_BINDING, created);
			player.getInventory().offerOrDrop(bound);
		}
		return ChipResult.BOUND;
	}

	public static ChipResult insert(PlayerEntity player, ItemStack chip, Entity target) {
		ChipBinding binding = chip.get(ModComponents.CHIP_BINDING);
		if (binding == null) {
			return ChipResult.NOT_BOUND;
		}
		if (!ownsChip(player, binding)) {
			return ChipResult.NOT_OWNER;
		}
		if (!(target instanceof MobEntity mob) || !mob.isAlive() || target instanceof BodyEntity || isBlacklisted(target.getType())) {
			return ChipResult.INVALID_TARGET;
		}
		if (isForbiddenDimension(target.getWorld().getRegistryKey())) {
			return ChipResult.FORBIDDEN_DIMENSION;
		}
		if (player instanceof ServerPlayerEntity operator && SpawnZone.forOperator(operator, target.getWorld())
				.filter(zone -> zone.touches(target.getX(), target.getZ(), target.getWidth() / 2.0)).isPresent()) {
			return ChipResult.SPAWN_PROTECTED; // the host couldn't stay there anyway
		}
		if (isDisabledBoss(target.getType())) {
			return ChipResult.BOSSES_DISABLED;
		}
		if (target instanceof Tameable tameable && tameable.getOwnerUuid() != null && !tameable.getOwnerUuid().equals(player.getUuid())) {
			return ChipResult.NOT_PET_OWNER;
		}
		if (ownsHost(player, mob)) {
			return ChipResult.ALREADY_CHIPPED;
		}
		ChipRegistry registry = ChipRegistry.get(player.getServer());
		if (registry.find(binding.chipId()).isPresent()) {
			return ChipResult.CHIP_IN_USE;
		}
		if (registry.activeCount(binding.helmetId()) >= NeuralLinkConfig.maxChipsPerHelmet) {
			return limitRefusal(registry, binding.helmetId(), player);
		}
		if (overPlayerLimit(registry, player)) {
			return ChipResult.LIMIT;
		}
		if (mob.hasAttached(ModAttachments.CHIP)) {
			// Hide foreign implants: give the generic refusal, and only after any refusal a normal mob would get, or a full
			// visor gives hosts away.
			return ChipResult.INVALID_TARGET;
		}
		mob.setAttached(ModAttachments.CHIP, binding.chipId());
		if (mob.getTarget() == player) {
			mob.setTarget(null);
		}
		registry.put(ChipEntry.of(new ChipBinding(binding.chipId(), binding.helmetId(), player.getUuid()), mob, (ServerWorld) mob.getWorld()));
		chip.decrement(1);
		if (player instanceof ServerPlayerEntity operator) {
			checkNetwork(operator, registry, binding.helmetId());
		}
		return ChipResult.INSERTED;
	}

	static void checkNetwork(ServerPlayerEntity operator, ChipRegistry registry, UUID helmetId) {
		if (registry.activeCount(helmetId) >= ACHIEVEMENT_CHIPS) {
			Achievements.grant(operator, "network");
		}
	}

	/** a spare chip follows its helmet; whoever bound it under a copy keeps it while wearing that copy. */
	private static boolean ownsChip(PlayerEntity player, ChipBinding binding) {
		UUID helmetOwner = ChipRegistry.get(player.getServer()).helmetOwner(binding.helmetId(), binding.owner());
		return player.getUuid().equals(helmetOwner)
				|| binding.owner().equals(player.getUuid()) && binding.helmetId().equals(wornHelmet(player).get(ModComponents.HELMET_ID));
	}

	private static boolean ownsHost(PlayerEntity player, MobEntity mob) {
		UUID chipId = mob.getAttached(ModAttachments.CHIP);
		return chipId != null && ChipRegistry.get(player.getServer()).find(chipId).filter(entry -> entry.owner().equals(player.getUuid())).isPresent();
	}

	/** Limit is per helmet but the counter shows only the wearer's chips, so a full helmet's copy needs its own reason. */
	private static ChipResult limitRefusal(ChipRegistry registry, UUID helmetId, PlayerEntity player) {
		boolean foreign = registry.forHelmet(helmetId).stream().anyMatch(entry -> !entry.lost() && !entry.owner().equals(player.getUuid()));
		return foreign ? ChipResult.FOREIGN_LIMIT : ChipResult.LIMIT;
	}

	/** limit across all the player's helmets, 0 means none. */
	private static boolean overPlayerLimit(ChipRegistry registry, PlayerEntity player) {
		return NeuralLinkConfig.maxChipsPerPlayer > 0 && registry.activeCountOfOwner(player.getUuid()) >= NeuralLinkConfig.maxChipsPerPlayer;
	}

	public static boolean isForbiddenDimension(RegistryKey<World> world) {
		return NeuralLinkConfig.dimensionBlacklist.contains(world.getValue().toString());
	}

	/** Why server rules now forbid this host; null if allowed. */
	public static ChipResult forbiddenHost(MobEntity mob) {
		if (isForbiddenDimension(mob.getWorld().getRegistryKey())) {
			return ChipResult.FORBIDDEN_DIMENSION;
		}
		if (isDisabledBoss(mob.getType())) {
			return ChipResult.BOSSES_DISABLED;
		}
		return isBlacklisted(mob.getType()) ? ChipResult.FORBIDDEN_HOST : null;
	}

	public static boolean isBlacklisted(EntityType<?> type) {
		return NeuralLinkConfig.entityBlacklist.contains(Registries.ENTITY_TYPE.getId(type).toString());
	}

	public static boolean isDisabledBoss(EntityType<?> type) {
		return type.isIn(ModEntities.BOSSES) && !NeuralLinkConfig.allowBosses;
	}

	/** Shift+right-click with shears returns the chip to its owner. */
	public static ChipResult extract(PlayerEntity player, MobEntity mob) {
		UUID chipId = mob.getAttached(ModAttachments.CHIP);
		if (chipId == null || !mob.isAlive()) {
			return ChipResult.UNKNOWN_CHIP;
		}
		ChipRegistry registry = ChipRegistry.get(player.getServer());
		Optional<ChipEntry> entry = registry.find(chipId);
		if (entry.isEmpty()) {
			return ChipResult.UNKNOWN_CHIP;
		}
		if (!entry.get().owner().equals(player.getUuid())) {
			return ChipResult.NOT_OWNER;
		}
		if (SessionManager.isControlled(mob)) {
			return ChipResult.BUSY;
		}
		mob.removeAttached(ModAttachments.CHIP);
		registry.remove(chipId);
		ItemStack chip = new ItemStack(ModItems.MICROCHIP);
		chip.set(ModComponents.CHIP_BINDING, new ChipBinding(chipId, entry.get().helmetId(), player.getUuid()));
		player.getInventory().offerOrDrop(chip);
		return ChipResult.EXTRACTED;
	}

	/** implant destroyed, mob lives. Unloaded mobs lose the mark on load, see {@link ChipTransfer#onEntityLoaded}. */
	public static ChipResult disable(ServerPlayerEntity player, UUID chipId) {
		ChipRegistry registry = ChipRegistry.get(player.getServer());
		Optional<ChipEntry> found = registry.find(chipId);
		if (found.isEmpty() || found.get().lost()) {
			return ChipResult.UNKNOWN_CHIP;
		}
		ChipEntry entry = found.get();
		if (!entry.owner().equals(player.getUuid())) {
			return ChipResult.NOT_OWNER;
		}
		if (SessionManager.isControlling(entry.mobId())) {
			return ChipResult.BUSY;
		}
		Entity mob = destroy(player.getServer(), entry);
		if (mob != null) {
			ChipFeedback.send(player, ChipResult.DISABLED, mob);
		}
		return ChipResult.DISABLED;
	}

	/** admin disable of any implant, lost ones too. A controlled host is released first. */
	public static void disableByAdmin(MinecraftServer server, ChipEntry entry) {
		ServerPlayerEntity operator = SessionManager.operatorOf(entry.mobId()).orElse(null);
		if (operator != null) {
			SessionManager.exit(operator, EndReason.KICKED);
		}
		Entity mob = destroy(server, entry);
		ServerPlayerEntity owner = server.getPlayerManager().getPlayer(entry.owner());
		if (owner != null) {
			if (mob != null) {
				ChipFeedback.send(owner, ChipResult.DISABLED, mob);
			}
			ChipResult.ADMIN_DISABLED.notify(owner);
		}
		if (operator != null && operator != owner) {
			ChipResult.ADMIN_KICKED.notify(operator);
		}
	}

	/** Removes the implant, returns the host if loaded. */
	private static Entity destroy(MinecraftServer server, ChipEntry entry) {
		SessionManager.cancelPending(entry.chipId());
		ChipRegistry.get(server).remove(entry.chipId());
		ServerWorld world = server.getWorld(entry.world());
		Entity mob = world == null ? null : world.getEntity(entry.mobId());
		if (mob != null) {
			mob.removeAttached(ModAttachments.CHIP);
		}
		return mob;
	}

	/** only the owner pins, and not lost hosts. An old star stays in the entry. */
	public static Optional<ChipEntry> pinnable(PlayerEntity player, UUID chipId) {
		return ChipRegistry.get(player.getServer()).find(chipId).filter(found -> found.owner().equals(player.getUuid()) && !found.lost());
	}

	public static boolean setFavorite(PlayerEntity player, UUID chipId, boolean favorite) {
		Optional<ChipEntry> entry = pinnable(player, chipId);
		if (entry.isEmpty() || entry.get().favorite() == favorite) {
			return false;
		}
		ChipRegistry.get(player.getServer()).put(entry.get().withFavorite(favorite));
		return true;
	}

	/** Removes a lost host from the menu; live ones stay. */
	public static boolean forget(PlayerEntity player, UUID chipId) {
		ChipRegistry registry = ChipRegistry.get(player.getServer());
		boolean allowed = registry.find(chipId).filter(entry -> entry.lost() && entry.owner().equals(player.getUuid())).isPresent();
		if (allowed) {
			registry.remove(chipId);
		}
		return allowed;
	}

	/** via the event, not useOnEntity, or villagers trade and sheep get sheared first. */
	public static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity clicked, EntityHitResult hit) {
		if (player.isSpectator()) {
			// PASS alone would still let vanilla open chest minecarts.
			return player instanceof ServerPlayerEntity operator && SessionManager.isInSession(operator) ? ActionResult.FAIL : ActionResult.PASS;
		}
		Entity entity = clicked instanceof EnderDragonPart part ? part.owner : clicked;
		ItemStack stack = player.getStackInHand(hand);
		if (stack.getItem() instanceof ChipItem && (entity instanceof MobEntity || entity instanceof PlayerEntity)) {
			if (!world.isClient) {
				insert(player, stack, entity).notify(player, entity);
			}
			return ActionResult.SUCCESS;
		}
		// a click sends interactAt then interact: extracting on the first would let the second shear. Foreign hosts shear
		// normally so the spy isn't revealed.
		if (!world.isClient && hit == null && stack.isOf(Items.SHEARS) && player.isSneaking() && entity instanceof MobEntity mob && ownsHost(player, mob)) {
			extract(player, mob).notify(player, mob);
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}
}
