package com.runehunter.party;

import com.runehunter.data.CreatureDef;
import com.runehunter.data.CreatureRoster;
import com.runehunter.data.GearItem;
import com.runehunter.storage.CollectionStore;
import java.util.ArrayList;
import java.util.List;

/**
 * A creature as it travels over the wire: definition + level/xp + equipped
 * gear + owning player's name. Built either from the local store or from
 * message fields (returning null when the creature key is unknown to this
 * client's roster — version-skew safe).
 */
public final class CompanionSnapshot
{
	private final String playerName;
	private final CreatureDef def;
	private final int level;
	private final int xp;
	private final List<GearItem> gear;

	private CompanionSnapshot(String playerName, CreatureDef def, int level, int xp, List<GearItem> gear)
	{
		this.playerName = playerName;
		this.def = def;
		this.level = level;
		this.xp = xp;
		this.gear = gear;
	}

	/** Snapshot any owned creature from the local store. */
	public static CompanionSnapshot of(CollectionStore store, CreatureDef def, String playerName)
	{
		List<GearItem> gear = new ArrayList<>();
		for (GearItem g : store.getEquipped(def))
		{
			if (g != null)
			{
				gear.add(g);
			}
		}
		return new CompanionSnapshot(playerName, def, store.getLevel(def), store.getXp(def), gear);
	}

	/** Rebuild from message fields; null if the creature key is unknown. */
	public static CompanionSnapshot fromWire(String playerName, String creatureKey, int level, int xp, String gearCsv)
	{
		if (creatureKey == null || creatureKey.isEmpty())
		{
			return null;
		}
		CreatureDef def = CreatureRoster.byKey(creatureKey);
		if (def == null)
		{
			return null;
		}
		List<GearItem> gear = new ArrayList<>();
		if (gearCsv != null && !gearCsv.isEmpty())
		{
			for (String name : gearCsv.split(","))
			{
				GearItem g = GearItem.byName(name);
				if (g != null)
				{
					gear.add(g);
				}
			}
		}
		return new CompanionSnapshot(playerName, def, Math.max(1, Math.min(99, level)), Math.max(0, xp), gear);
	}

	public String gearCsv()
	{
		StringBuilder sb = new StringBuilder();
		for (GearItem g : gear)
		{
			sb.append(sb.length() > 0 ? "," : "").append(g.name());
		}
		return sb.toString();
	}

	public int gearAtk()
	{
		int sum = 0;
		for (GearItem g : gear)
		{
			sum += g.getAtk();
		}
		return sum;
	}

	public int gearDef()
	{
		int sum = 0;
		for (GearItem g : gear)
		{
			sum += g.getDef();
		}
		return sum;
	}

	public String getPlayerName()
	{
		return playerName;
	}

	public CreatureDef getDef()
	{
		return def;
	}

	public int getLevel()
	{
		return level;
	}

	public int getXp()
	{
		return xp;
	}

	public List<GearItem> getGear()
	{
		return gear;
	}

	public String describe()
	{
		return def.getNpcName() + " (Lv " + level + (gear.isEmpty() ? ")" : ", " + gear.size() + " gear)");
	}
}
