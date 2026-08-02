package com.runehunter.party;

import com.runehunter.data.CreatureDef;
import com.runehunter.data.GearItem;
import com.runehunter.storage.CollectionStore;
import net.runelite.client.config.ConfigManager;

/**
 * Applies trades on top of CollectionStore WITHOUT modifying it: mutations
 * that the store has no public API for (decrementing a caught count, moving
 * equipped gear off a departing creature) are written through the public
 * ConfigManager API to the exact keys the store persists ("col_<key>" /
 * "equip_<key>", group "runehunter", RSProfile scope), followed by
 * CollectionStore.load() to re-sync its in-memory state. Additions (xp
 * merge, gear into the bank) use the store's own public methods.
 *
 * SHINY-PROTECTION RULE: a trade always transfers a PLAIN (non-shiny) copy;
 * shiny counts never move. A creature is tradeable only while caughtCount >
 * shinyCount — you can only give away spare plain copies, so nobody's only
 * (or last) shiny can ever leave their collection, even by accident.
 *
 * All methods must be called on the client thread.
 */
public class TradeLedger
{
	private static final String GROUP = "runehunter";

	private final ConfigManager configManager;
	private final CollectionStore store;

	public TradeLedger(ConfigManager configManager, CollectionStore store)
	{
		this.configManager = configManager;
		this.store = store;
	}

	/** [caughtCount, shinyCount] straight from the persisted key. */
	private int[] counts(CreatureDef d)
	{
		String v = configManager.getRSProfileConfiguration(GROUP,
			com.runehunter.storage.SaveProfile.key("col_" + d.key()));
		int[] out = {0, 0};
		if (v != null)
		{
			String[] parts = v.split(",");
			try
			{
				out[0] = Integer.parseInt(parts[0].trim());
				out[1] = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
			}
			catch (NumberFormatException ignored)
			{
			}
		}
		return out;
	}

	private void writeCounts(CreatureDef d, int caught, int shiny)
	{
		configManager.setRSProfileConfiguration(GROUP,
			com.runehunter.storage.SaveProfile.key("col_" + d.key()),
			Math.max(0, caught) + "," + Math.max(0, shiny));
	}

	/** True when a spare plain copy exists (see the shiny rule above). */
	public boolean isTradeable(CreatureDef d)
	{
		int[] c = counts(d);
		return c[0] > 0 && c[0] > c[1];
	}

	/** Human reason a creature can't be offered, or null if it can. */
	public String tradeBlockReason(CreatureDef d)
	{
		int[] c = counts(d);
		if (c[0] <= 0)
		{
			return "You haven't caught one.";
		}
		if (c[0] <= c[1])
		{
			return "Only shiny copies left — shinies never leave your collection.";
		}
		return null;
	}

	/**
	 * The offered creature leaves this account: plain count -1, its equipped
	 * gear departs with it (cleared, NOT returned to the bank), and the
	 * companion slot clears if that was the last copy.
	 */
	public void applyOutgoing(CreatureDef d)
	{
		int[] c = counts(d);
		if (c[0] <= 0 || c[0] <= c[1])
		{
			return; // shouldn't happen — offer validation runs first
		}
		writeCounts(d, c[0] - 1, c[1]);
		// equipped gear travels with the creature
		configManager.setRSProfileConfiguration(GROUP,
			com.runehunter.storage.SaveProfile.key("equip_" + d.key()), ",,");
		if (c[0] - 1 <= 0 && d.key().equals(store.getCompanionKey()))
		{
			store.setCompanion(null);
		}
		store.load();
	}

	/**
	 * A creature arrives: plain count +1 (never shiny — see rule), xp merges
	 * as max(existing, incoming), the incoming equipped gear lands in the
	 * gear bank. Deliberately avoids recordCatch() so trades award no
	 * duplicate-essence and don't pollute the catch feed.
	 */
	public void applyIncoming(CompanionSnapshot incoming)
	{
		CreatureDef d = incoming.getDef();
		int[] c = counts(d);
		writeCounts(d, c[0] + 1, c[1]);
		store.load();

		int xpGap = incoming.getXp() - store.getXp(d);
		if (xpGap > 0)
		{
			store.addXp(d, xpGap);
		}
		for (GearItem g : incoming.getGear())
		{
			store.addGear(g);
		}
	}
}
