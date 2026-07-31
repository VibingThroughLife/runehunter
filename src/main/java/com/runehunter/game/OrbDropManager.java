package com.runehunter.game;

import com.runehunter.data.CreatureDef;
import com.runehunter.data.CreatureRoster;
import com.runehunter.data.OrbType;
import com.runehunter.spawn.SpawnManager;
import com.runehunter.storage.CollectionStore;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;

/**
 * Ties RuneHunter to normal gameplay: your kills roll for orb drops, and killing
 * a creature's real counterpart lures its mini to you (species lure).
 */
public class OrbDropManager
{
	private static final double DROP_CHANCE = 0.20;
	private static final int ELDRITCH_DENOMINATOR = 128;
	private static final int LURE_SPAWN_DENOMINATOR = 40;

	private final Client client;
	private final CollectionStore store;
	private final SpawnManager spawnManager;
	private final Runnable uiRefresh;

	public OrbDropManager(Client client, CollectionStore store, SpawnManager spawnManager, Runnable uiRefresh)
	{
		this.client = client;
		this.store = store;
		this.spawnManager = spawnManager;
		this.uiRefresh = uiRefresh;
	}

	/** Call from the NpcLootReceived event (fires for kills credited to you). */
	public void onKill(NPC npc)
	{
		if (npc == null)
		{
			return;
		}
		ThreadLocalRandom r = ThreadLocalRandom.current();
		int cb = Math.max(0, npc.getCombatLevel());

		// Orb roll
		if (r.nextDouble() < DROP_CHANCE)
		{
			OrbType orb = rollOrb(r, cb);
			store.addOrb(orb, 1);
			message("You find " + article(orb) + " " + orb.getDisplayName().toLowerCase()
				+ " among the remains.");
			uiRefresh.run();
		}

		// Rare eldritch roll for high-level kills
		if (cb >= 100 && r.nextInt(ELDRITCH_DENOMINATOR) == 0)
		{
			store.addOrb(OrbType.ELDRITCH, 1);
			message("Something ancient hums in your pack... you found an Eldritch orb!");
			uiRefresh.run();
		}

		// Species lure
		CreatureDef def = CreatureRoster.byNpcName(npc.getName());
		if (def != null)
		{
			spawnManager.addLure(def);
			if (r.nextInt(LURE_SPAWN_DENOMINATOR) == 0)
			{
				spawnManager.personalSpawnNearPlayer(def);
				message("Your hunt has drawn out a wild mini " + def.getNpcName() + "!");
			}
		}
	}

	private OrbType rollOrb(ThreadLocalRandom r, int combatLevel)
	{
		double roll = r.nextDouble();
		if (combatLevel >= 100)
		{
			if (roll < 0.40)
			{
				return OrbType.CRYSTAL;
			}
			return roll < 0.85 ? OrbType.ELEMENTAL : OrbType.UNPOWERED;
		}
		if (combatLevel >= 40)
		{
			return roll < 0.30 ? OrbType.ELEMENTAL : OrbType.UNPOWERED;
		}
		return OrbType.UNPOWERED;
	}

	private static String article(OrbType t)
	{
		char c = t.getDisplayName().charAt(0);
		return "AEIOUaeiou".indexOf(c) >= 0 ? "an" : "a";
	}

	private void message(String msg)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
	}
}
