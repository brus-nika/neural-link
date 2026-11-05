package net.neurallink.item;

import java.util.List;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.neurallink.chip.ChipLogic;
import net.neurallink.registry.ModComponents;

public class ChipItem extends Item {
	public ChipItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		if (!world.isClient) {
			ChipLogic.bind(user, user.getStackInHand(hand)).notify(user);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		boolean bound = stack.contains(ModComponents.CHIP_BINDING);
		tooltip.add(Text.translatable(bound ? "item.neurallink.microchip.bound" : "item.neurallink.microchip.unbound").formatted(Formatting.GRAY));
	}
}
