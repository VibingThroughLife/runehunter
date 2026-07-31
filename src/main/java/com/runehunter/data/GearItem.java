package com.runehunter.data;

import java.awt.Color;

/**
 * Equipment your creatures can wear, win, and (one day) trade. Three slots,
 * four metal tiers — OSRS to the bone. Bonuses feed BattleStats.
 */
public enum GearItem
{
	// slot, atk, def, dropWeight (higher = more common), display tint
	BRONZE_HELM("Bronze helm", Slot.HELM, 0, 2, 40, new Color(0x8D6E63)),
	BRONZE_PLATE("Bronze platebody", Slot.BODY, 0, 4, 32, new Color(0x8D6E63)),
	BRONZE_CLAWS("Bronze claws", Slot.WEAPON, 3, 0, 36, new Color(0x8D6E63)),

	MITHRIL_HELM("Mithril helm", Slot.HELM, 0, 5, 16, new Color(0x5C6BC0)),
	MITHRIL_PLATE("Mithril platebody", Slot.BODY, 0, 9, 12, new Color(0x5C6BC0)),
	MITHRIL_CLAWS("Mithril claws", Slot.WEAPON, 7, 0, 14, new Color(0x5C6BC0)),

	RUNE_HELM("Rune helm", Slot.HELM, 0, 9, 6, new Color(0x26A69A)),
	RUNE_PLATE("Rune platebody", Slot.BODY, 0, 15, 4, new Color(0x26A69A)),
	RUNE_CLAWS("Rune claws", Slot.WEAPON, 12, 0, 5, new Color(0x26A69A)),

	DRAGON_HELM("Dragon helm", Slot.HELM, 2, 14, 2, new Color(0xD84315)),
	DRAGON_PLATE("Dragon platebody", Slot.BODY, 0, 22, 1, new Color(0xD84315)),
	DRAGON_CLAWS("Dragon claws", Slot.WEAPON, 19, 0, 1, new Color(0xD84315));

	public enum Slot
	{
		HELM, BODY, WEAPON
	}

	private final String displayName;
	private final Slot slot;
	private final int atk;
	private final int def;
	private final int dropWeight;
	private final Color color;

	GearItem(String displayName, Slot slot, int atk, int def, int dropWeight, Color color)
	{
		this.displayName = displayName;
		this.slot = slot;
		this.atk = atk;
		this.def = def;
		this.dropWeight = dropWeight;
		this.color = color;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public Slot getSlot()
	{
		return slot;
	}

	public int getAtk()
	{
		return atk;
	}

	public int getDef()
	{
		return def;
	}

	public int getDropWeight()
	{
		return dropWeight;
	}

	public Color getColor()
	{
		return color;
	}

	/** Weighted random drop. */
	public static GearItem randomDrop(java.util.Random rng)
	{
		int total = 0;
		for (GearItem g : values())
		{
			total += g.dropWeight;
		}
		int roll = rng.nextInt(total);
		for (GearItem g : values())
		{
			roll -= g.dropWeight;
			if (roll < 0)
			{
				return g;
			}
		}
		return BRONZE_HELM;
	}

	public static GearItem byName(String name)
	{
		try
		{
			return valueOf(name);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}
}
