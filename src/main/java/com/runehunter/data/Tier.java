package com.runehunter.data;

import java.awt.Color;

/**
 * Rarity tiers. Spawn weight is relative; catch chance is the base chance
 * before the orb multiplier is applied.
 */
public enum Tier
{
	COMMON("Common", 0.90, 100, new Color(0x9e9e9e), 0.02),
	UNCOMMON("Uncommon", 0.65, 40, new Color(0x4caf50), 0.06),
	RARE("Rare", 0.40, 16, new Color(0x2196f3), 0.12),
	EPIC("Epic", 0.25, 5, new Color(0x9c27b0), 0.20),
	LEGENDARY("Legendary", 0.15, 1, new Color(0xffb300), 0.28);

	private final String displayName;
	private final double baseCatchChance;
	private final int spawnWeight;
	private final Color color;
	private final double baseFleeChance;

	Tier(String displayName, double baseCatchChance, int spawnWeight, Color color, double baseFleeChance)
	{
		this.displayName = displayName;
		this.baseCatchChance = baseCatchChance;
		this.spawnWeight = spawnWeight;
		this.color = color;
		this.baseFleeChance = baseFleeChance;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public double getBaseCatchChance()
	{
		return baseCatchChance;
	}

	public int getSpawnWeight()
	{
		return spawnWeight;
	}

	public Color getColor()
	{
		return color;
	}

	/** Chance the creature flees entirely after a failed catch (scales with fail count). */
	public double fleeChance(int failedAttempts)
	{
		return Math.min(0.75, baseFleeChance + failedAttempts * 0.08);
	}
}
