package com.runehunter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("runehunter")
public interface RuneHunterConfig extends Config
{
	/** Battle speed: percent applied to every battle timing (higher = slower). */
	enum BattlePace
	{
		RELAXED("Relaxed", 220),
		STANDARD("Standard", 160),
		FAST("Fast", 100);

		private final String label;
		private final int percent;

		BattlePace(String label, int percent)
		{
			this.label = label;
			this.percent = percent;
		}

		public int getPercent()
		{
			return percent;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Range(min = 1, max = 24)
	@ConfigItem(
		keyName = "spawnCap",
		name = "Max creatures per area",
		description = "Maximum number of mini-creatures spawned in the loaded scene",
		position = 1
	)
	default int spawnCap()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "showRadar",
		name = "Nearby radar",
		description = "Show the nearby-creatures tracker overlay",
		position = 2
	)
	default boolean showRadar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableDrops",
		name = "Orb drops from kills",
		description = "Rolling for orb drops when you kill NPCs",
		position = 3
	)
	default boolean enableDrops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableCompanion",
		name = "Companion follower",
		description = "Show your selected caught creature following you",
		position = 4
	)
	default boolean enableCompanion()
	{
		return true;
	}

	@ConfigItem(
		keyName = "wander",
		name = "Creatures wander",
		description = "Minis amble around near their spawn tile instead of standing still",
		position = 5
	)
	default boolean wander()
	{
		return true;
	}

	@ConfigItem(
		keyName = "cinematicCatch",
		name = "Cinematic catch",
		description = "Full-screen cutscene when you throw an orb (letterbox, stage, verdict splash)",
		position = 6
	)
	default boolean cinematicCatch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "battles",
		name = "Wild battles",
		description = "Random wild creatures occasionally challenge your companion to a battle",
		position = 7
	)
	default boolean battles()
	{
		return true;
	}

	@ConfigItem(
		keyName = "battlePace",
		name = "Battle pace",
		description = "How fast wild battles play out — Relaxed gives you plenty of time to read telegraphs and switch prayers",
		position = 11
	)
	default BattlePace battlePace()
	{
		return BattlePace.RELAXED;
	}

	@ConfigItem(
		keyName = "devSpawnBoost",
		name = "[Dev] Spawn boost",
		description = "Testing: roll several extra spawns per region and much more frequent legendaries",
		position = 8
	)
	default boolean devSpawnBoost()
	{
		return false;
	}

	@ConfigItem(
		keyName = "devShinyBoost",
		name = "[Dev] 1/16 shiny rate",
		description = "Testing: shinies roll at 1/16 instead of 1/512",
		position = 9
	)
	default boolean devShinyBoost()
	{
		return false;
	}

	@ConfigItem(
		keyName = "devMarkSpawns",
		name = "[Dev] Mark spawn tiles",
		description = "Testing: draw a marker, name and clickbox on every active spawn in the 3D world",
		position = 10
	)
	default boolean devMarkSpawns()
	{
		return false;
	}
}
