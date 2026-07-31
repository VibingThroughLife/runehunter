package com.runehunter.storage;

import com.runehunter.data.CreatureDef;
import com.runehunter.data.CreatureRoster;
import com.runehunter.data.OrbType;
import com.runehunter.data.Tier;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;

/**
 * Per-account persistence via RSProfile-scoped config keys. Fully local —
 * no server, no external calls.
 */
public class CollectionStore
{
	private static final String GROUP = "runehunter";

	private final ConfigManager configManager;

	private static final int CATCH_LOG_MAX = 30;

	/** key -> [caughtCount, shinyCount] */
	private final Map<String, int[]> collection = new HashMap<>();
	private final Map<OrbType, Integer> orbs = new EnumMap<>(OrbType.class);
	/** Most-recent-first entries: [epochMillis, creatureKey, shinyFlag]. */
	private final java.util.LinkedList<String[]> catchLog = new java.util.LinkedList<>();
	private int essence;
	private String companionKey;

	public int getEssence()
	{
		return essence;
	}

	public String getCompanionKey()
	{
		return companionKey;
	}

	public CollectionStore(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	public void load()
	{
		collection.clear();
		orbs.clear();
		// Secrets included: a caught secret has to survive a relog like
		// anything else. totalCaughtSpecies() below stays on ALL, so the dex
		// total and the progress bar are unaffected.
		for (CreatureDef d : CreatureRoster.ALL_INCLUDING_SECRETS)
		{
			String v = rsGet("col_" + d.key());
			if (v != null)
			{
				String[] parts = v.split(",");
				try
				{
					collection.put(d.key(), new int[]{
						Integer.parseInt(parts[0]),
						parts.length > 1 ? Integer.parseInt(parts[1]) : 0});
				}
				catch (NumberFormatException ignored)
				{
				}
			}
		}

		String orbStr = rsGet("orbs");
		if (orbStr != null)
		{
			String[] parts = orbStr.split(",");
			OrbType[] types = OrbType.values();
			for (int i = 0; i < types.length && i < parts.length; i++)
			{
				try
				{
					orbs.put(types[i], Integer.parseInt(parts[i]));
				}
				catch (NumberFormatException ignored)
				{
				}
			}
		}
		else
		{
			// Starter pack for new hunters
			orbs.put(OrbType.UNPOWERED, 10);
			persistOrbs();
		}

		String ess = rsGet("essence");
		essence = ess != null ? safeInt(ess) : 0;
		companionKey = rsGet("companion");
		training.clear();
		loadGear();

		catchLog.clear();
		String logStr = rsGet("catchlog");
		if (logStr != null && !logStr.isEmpty())
		{
			for (String entry : logStr.split(";"))
			{
				String[] parts = entry.split("\\|");
				if (parts.length == 3)
				{
					catchLog.add(parts);
				}
			}
		}
	}

	// ---- collection ----

	public boolean isCaught(CreatureDef d)
	{
		int[] c = collection.get(d.key());
		return c != null && c[0] > 0;
	}

	public boolean isShinyCaught(CreatureDef d)
	{
		int[] c = collection.get(d.key());
		return c != null && c[1] > 0;
	}

	public int caughtCount(CreatureDef d)
	{
		int[] c = collection.get(d.key());
		return c == null ? 0 : c[0];
	}

	public int totalCaughtSpecies()
	{
		int n = 0;
		for (CreatureDef d : CreatureRoster.ALL)
		{
			if (isCaught(d))
			{
				n++;
			}
		}
		return n;
	}

	/**
	 * Caught Secret Dex creatures, in roster order — empty until the player
	 * finds their first one. Deliberately the only way the UI can learn a
	 * secret exists: nothing here reveals the uncaught ones.
	 */
	public java.util.List<CreatureDef> caughtSecrets()
	{
		java.util.List<CreatureDef> out = new java.util.ArrayList<>();
		for (CreatureDef d : CreatureRoster.SECRET)
		{
			if (isCaught(d))
			{
				out.add(d);
			}
		}
		return out;
	}

	/** Records a catch; returns essence awarded for duplicates (0 for a new dex entry). */
	public int recordCatch(CreatureDef d, boolean shiny)
	{
		int[] c = collection.computeIfAbsent(d.key(), k -> new int[]{0, 0});
		boolean dupe = c[0] > 0;
		c[0]++;
		if (shiny)
		{
			c[1]++;
		}
		rsSet("col_" + d.key(), c[0] + "," + c[1]);

		int awarded = 0;
		if (dupe)
		{
			awarded = essenceFor(d.getTier()) * (shiny ? 10 : 1);
			essence += awarded;
			rsSet("essence", Integer.toString(essence));
		}

		logCatch(d, shiny);
		return awarded;
	}

	// ---- catch history (local activity feed) ----

	private void logCatch(CreatureDef d, boolean shiny)
	{
		catchLog.addFirst(new String[]{
			Long.toString(System.currentTimeMillis()), d.key(), shiny ? "1" : "0"});
		while (catchLog.size() > CATCH_LOG_MAX)
		{
			catchLog.removeLast();
		}
		StringBuilder sb = new StringBuilder();
		for (String[] e : catchLog)
		{
			if (sb.length() > 0)
			{
				sb.append(';');
			}
			sb.append(e[0]).append('|').append(e[1]).append('|').append(e[2]);
		}
		rsSet("catchlog", sb.toString());
	}

	/** Most-recent-first catch history: [epochMillis, creatureKey, shinyFlag]. */
	public java.util.List<String[]> recentCatches()
	{
		return new java.util.ArrayList<>(catchLog);
	}

	// ---- training: xp / levels / battle record (RSProfile-scoped) ----

	/** key -> [xp, wins, losses] */
	private final Map<String, int[]> training = new HashMap<>();

	private int[] trainingOf(String key)
	{
		return training.computeIfAbsent(key, k ->
		{
			String v = rsGet("train_" + k);
			if (v != null)
			{
				String[] p = v.split(",");
				try
				{
					return new int[]{Integer.parseInt(p[0]),
						p.length > 1 ? Integer.parseInt(p[1]) : 0,
						p.length > 2 ? Integer.parseInt(p[2]) : 0};
				}
				catch (NumberFormatException ignored)
				{
				}
			}
			return new int[]{0, 0, 0};
		});
	}

	private void persistTraining(String key)
	{
		int[] t = trainingOf(key);
		rsSet("train_" + key, t[0] + "," + t[1] + "," + t[2]);
	}

	public int getXp(CreatureDef d)
	{
		return trainingOf(d.key())[0];
	}

	public int getWins(CreatureDef d)
	{
		return trainingOf(d.key())[1];
	}

	public int getLosses(CreatureDef d)
	{
		return trainingOf(d.key())[2];
	}

	public void addXp(CreatureDef d, int xp)
	{
		trainingOf(d.key())[0] += Math.max(0, xp);
		persistTraining(d.key());
	}

	public void recordBattle(CreatureDef d, boolean won)
	{
		trainingOf(d.key())[won ? 1 : 2]++;
		persistTraining(d.key());
	}

	/** OSRS-flavored level curve: 1 at 0xp, 99 cap. */
	public static int levelForXp(int xp)
	{
		return Math.min(99, 1 + (int) Math.floor(Math.sqrt(xp / 12.0)));
	}

	public static int xpForLevel(int level)
	{
		int l = Math.max(1, Math.min(99, level));
		return 12 * (l - 1) * (l - 1);
	}

	public int getLevel(CreatureDef d)
	{
		return levelForXp(getXp(d));
	}

	// ---- gear: shared inventory + per-creature equipment (RSProfile) ----

	private final java.util.List<com.runehunter.data.GearItem> gearInventory = new java.util.ArrayList<>();
	private final Map<String, com.runehunter.data.GearItem[]> equipped = new HashMap<>();

	private void loadGear()
	{
		gearInventory.clear();
		equipped.clear();
		String inv = rsGet("gearinv");
		if (inv != null && !inv.isEmpty())
		{
			for (String n : inv.split(","))
			{
				com.runehunter.data.GearItem g = com.runehunter.data.GearItem.byName(n);
				if (g != null)
				{
					gearInventory.add(g);
				}
			}
		}
	}

	private void persistGearInventory()
	{
		StringBuilder sb = new StringBuilder();
		for (com.runehunter.data.GearItem g : gearInventory)
		{
			sb.append(sb.length() > 0 ? "," : "").append(g.name());
		}
		rsSet("gearinv", sb.toString());
	}

	public java.util.List<com.runehunter.data.GearItem> getGearInventory()
	{
		return new java.util.ArrayList<>(gearInventory);
	}

	public void addGear(com.runehunter.data.GearItem item)
	{
		gearInventory.add(item);
		persistGearInventory();
	}

	/** Equipped items for a creature, indexed by GearItem.Slot ordinal (nullable). */
	public com.runehunter.data.GearItem[] getEquipped(CreatureDef d)
	{
		return equipped.computeIfAbsent(d.key(), k ->
		{
			com.runehunter.data.GearItem[] out = new com.runehunter.data.GearItem[3];
			String v = rsGet("equip_" + k);
			if (v != null)
			{
				String[] parts = v.split(",", -1);
				for (int i = 0; i < 3 && i < parts.length; i++)
				{
					out[i] = com.runehunter.data.GearItem.byName(parts[i]);
				}
			}
			return out;
		}).clone();
	}

	/**
	 * Equip an item from the inventory (swapping out anything in its slot).
	 * Pass null to unequip the slot back to the inventory.
	 */
	public boolean equip(CreatureDef d, com.runehunter.data.GearItem.Slot slot, com.runehunter.data.GearItem item)
	{
		if (item != null && (item.getSlot() != slot || !gearInventory.remove(item)))
		{
			return false;
		}
		com.runehunter.data.GearItem[] cur = equipped.computeIfAbsent(d.key(),
			k -> getEquipped(d));
		com.runehunter.data.GearItem prev = cur[slot.ordinal()];
		if (prev != null)
		{
			gearInventory.add(prev);
		}
		cur[slot.ordinal()] = item;
		equipped.put(d.key(), cur);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 3; i++)
		{
			sb.append(i > 0 ? "," : "").append(cur[i] == null ? "" : cur[i].name());
		}
		rsSet("equip_" + d.key(), sb.toString());
		persistGearInventory();
		return true;
	}

	// ---- QA bug reports (plain config, not per-account) ----

	public void reportBug(CreatureDef d, String note)
	{
		java.util.List<String> entries = rawReports();
		String encoded;
		try
		{
			encoded = java.net.URLEncoder.encode(note == null ? "" : note, "UTF-8");
		}
		catch (java.io.UnsupportedEncodingException e)
		{
			encoded = "";
		}
		entries.add(d.key() + "=" + encoded);
		configManager.setConfiguration(GROUP, "bugreports", String.join(";", entries));
	}

	private java.util.List<String> rawReports()
	{
		String v = configManager.getConfiguration(GROUP, "bugreports");
		if (v == null || v.isEmpty())
		{
			return new java.util.ArrayList<>();
		}
		return new java.util.ArrayList<>(java.util.Arrays.asList(v.split(";")));
	}

	/** Formatted "creature_key — note" lines, oldest first. */
	public java.util.List<String> reportedBugs()
	{
		java.util.List<String> out = new java.util.ArrayList<>();
		for (String entry : rawReports())
		{
			int eq = entry.indexOf('=');
			if (eq < 0)
			{
				out.add(entry);
				continue;
			}
			String note;
			try
			{
				note = java.net.URLDecoder.decode(entry.substring(eq + 1), "UTF-8");
			}
			catch (Exception e)
			{
				note = entry.substring(eq + 1);
			}
			out.add(entry.substring(0, eq) + (note.isEmpty() ? "" : " — " + note));
		}
		return out;
	}

	public void clearBugReports()
	{
		configManager.unsetConfiguration(GROUP, "bugreports");
	}

	private static int essenceFor(Tier t)
	{
		switch (t)
		{
			case COMMON: return 5;
			case UNCOMMON: return 10;
			case RARE: return 20;
			case EPIC: return 40;
			case LEGENDARY:
			default: return 80;
		}
	}

	// ---- orbs ----

	public int orbCount(OrbType t)
	{
		return orbs.getOrDefault(t, 0);
	}

	public void addOrb(OrbType t, int n)
	{
		orbs.merge(t, n, Integer::sum);
		persistOrbs();
	}

	public boolean consumeOrb(OrbType t)
	{
		int have = orbCount(t);
		if (have <= 0)
		{
			return false;
		}
		orbs.put(t, have - 1);
		persistOrbs();
		return true;
	}

	private void persistOrbs()
	{
		StringBuilder sb = new StringBuilder();
		for (OrbType t : OrbType.values())
		{
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			sb.append(orbCount(t));
		}
		rsSet("orbs", sb.toString());
	}

	// ---- companion ----

	public void setCompanion(String key)
	{
		companionKey = key;
		if (key == null)
		{
			configManager.unsetRSProfileConfiguration(GROUP, "companion");
		}
		else
		{
			rsSet("companion", key);
		}
	}

	// ---- helpers ----

	private String rsGet(String key)
	{
		return configManager.getRSProfileConfiguration(GROUP, key);
	}

	private void rsSet(String key, String value)
	{
		configManager.setRSProfileConfiguration(GROUP, key, value);
	}

	private static int safeInt(String s)
	{
		try
		{
			return Integer.parseInt(s);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
}
