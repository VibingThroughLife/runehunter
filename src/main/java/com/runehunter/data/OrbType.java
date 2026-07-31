package com.runehunter.data;

/**
 * The "pokeball" line. Orbs are earned from NPC kills and are the only way
 * to catch creatures. Eldritch orbs always succeed.
 */
public enum OrbType
{
	UNPOWERED("Unpowered orb", 1.0),
	ELEMENTAL("Elemental orb", 1.6),
	CRYSTAL("Crystal orb", 2.6),
	ELDRITCH("Eldritch orb", 100.0);

	private final String displayName;
	private final double catchMultiplier;

	OrbType(String displayName, double catchMultiplier)
	{
		this.displayName = displayName;
		this.catchMultiplier = catchMultiplier;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public double getCatchMultiplier()
	{
		return catchMultiplier;
	}
}
