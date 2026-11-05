package net.neurallink.client.gui;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.neurallink.chip.ChipCard;
import net.neurallink.client.ClientConfig;

/** Menu order: unreachable last, no signal before lost; starred first, then the chosen mode. */
public enum MenuSort {
	STATUS(Comparator.<ChipCard, ChipCard.Status>comparing(ChipCard::status).thenComparingInt(ChipCard::distance)),
	DISTANCE(Comparator.comparingInt(ChipCard::distance)),
	TYPE(Comparator.<ChipCard, String>comparing(card -> Registries.ENTITY_TYPE.get(card.entityType()).getName().getString().toLowerCase(Locale.ROOT))
			.thenComparingInt(ChipCard::distance)),
	SIGNAL(Comparator.<ChipCard>comparingInt(card -> -card.signal()).thenComparingInt(ChipCard::distance));

	private final Comparator<ChipCard> order;
	private final Text label;

	MenuSort(Comparator<ChipCard> mode) {
		// the controlled host first. Lost goes below no signal, the server sends it no star.
		order = Comparator.<ChipCard, Boolean>comparing(card -> !card.current())
				.thenComparingInt(card -> card.status().connectable() ? 0 : card.status().ordinal())
				.thenComparing(card -> !card.favorite())
				.thenComparing(mode)
				.thenComparing(ChipCard::chipId); // the server's order varies, without this equal cards could swap
		label = Text.translatable("menu.neurallink.sort", Text.translatable("menu.neurallink.sort." + name().toLowerCase(Locale.ROOT)));
	}

	public static MenuSort current() {
		try {
			return valueOf(ClientConfig.get().menuSort);
		} catch (IllegalArgumentException | NullPointerException e) {
			return STATUS;
		}
	}

	public MenuSort next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public Text label() {
		return label;
	}

	public List<ChipCard> sort(List<ChipCard> cards) {
		return cards.stream().sorted(order).toList();
	}
}
