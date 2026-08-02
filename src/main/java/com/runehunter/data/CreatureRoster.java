package com.runehunter.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static com.runehunter.data.Habitat.*;
import static com.runehunter.data.Tier.*;

/**
 * The v1 roster: 87 creatures across 5 tiers. Names must match the in-game
 * NPC names exactly (case-insensitive) for runtime id resolution.
 *
 * <p>{@link #SECRET} holds the Secret Dex easter eggs and is deliberately kept
 * out of {@link #ALL} — see the comment above the list.
 */
public final class CreatureRoster
{
	public static final List<CreatureDef> ALL;

	/** Secret Dex characters. Never iterate this for totals or wild rolls. */
	public static final List<CreatureDef> SECRET;

	/** ALL followed by SECRET — for storage, scanning and key lookups. */
	public static final List<CreatureDef> ALL_INCLUDING_SECRETS;

	static
	{
		List<CreatureDef> l = new ArrayList<>();

		// ---- Tier 1: Commons (12) ----
		l.add(new CreatureDef("Chicken", COMMON, EVERYWHERE));
		l.add(new CreatureDef("Cow", COMMON, EVERYWHERE));
		l.add(new CreatureDef("Goblin", COMMON, EVERYWHERE));
		l.add(new CreatureDef("Giant rat", COMMON, EVERYWHERE));
		l.add(new CreatureDef("Imp", COMMON, EVERYWHERE));
		l.add(new CreatureDef("Scorpion", COMMON, DESERT, MISTHALIN));
		l.add(new CreatureDef("Wolf", COMMON, ASGARNIA, FREMENNIK, KANDARIN));
		l.add(new CreatureDef("Seagull", COMMON, ASGARNIA, KANDARIN, KARAMJA));
		l.add(new CreatureDef("Duck", COMMON, MISTHALIN, KANDARIN));
		l.add(new CreatureDef("Rock Crab", COMMON, FREMENNIK));
		l.add(new CreatureDef("Unicorn", COMMON, MISTHALIN, ASGARNIA));
		l.add(new CreatureDef("Penguin", COMMON, KANDARIN, FREMENNIK));

		// ---- Tier 2: Uncommons (15) ----
		l.add(new CreatureDef("Hill Giant", UNCOMMON, MISTHALIN, WILDERNESS));
		l.add(new CreatureDef("Moss giant", UNCOMMON, KARAMJA, MISTHALIN));
		l.add(new CreatureDef("Ice giant", UNCOMMON, ASGARNIA, WILDERNESS));
		l.add(new CreatureDef("Hobgoblin", UNCOMMON, ASGARNIA, WILDERNESS));
		l.add(new CreatureDef("Green dragon", UNCOMMON, WILDERNESS));
		l.add(new CreatureDef("Lesser demon", UNCOMMON, KARAMJA, WILDERNESS));
		l.add(new CreatureDef("Chaos druid", UNCOMMON, WILDERNESS, KANDARIN));
		l.add(new CreatureDef("Kalphite Worker", UNCOMMON, DESERT));
		l.add(new CreatureDef("Crawling Hand", UNCOMMON, MORYTANIA));
		l.add(new CreatureDef("Cave crawler", UNCOMMON, FREMENNIK));
		l.add(new CreatureDef("Banshee", UNCOMMON, MORYTANIA));
		l.add(new CreatureDef("Rockslug", UNCOMMON, FREMENNIK));
		l.add(new CreatureDef("Cockatrice", UNCOMMON, FREMENNIK));
		l.add(new CreatureDef("Pyrefiend", UNCOMMON, FREMENNIK, WILDERNESS));
		l.add(new CreatureDef("TzHaar-Ket", UNCOMMON, KARAMJA));

		// ---- Tier 3: Rares (18) ----
		l.add(new CreatureDef("Blue dragon", RARE, ASGARNIA));
		l.add(new CreatureDef("Red dragon", RARE, KARAMJA));
		l.add(new CreatureDef("Black dragon", RARE, WILDERNESS, ASGARNIA));
		l.add(new CreatureDef("Bronze dragon", RARE, KARAMJA));
		l.add(new CreatureDef("Iron dragon", RARE, KARAMJA));
		// pinned: lowest combat id (139) is an oddball catacombs rig; 274 is the classic
		l.add(new CreatureDef("Steel dragon", 274, RARE, KARAMJA, WILDERNESS));
		l.add(new CreatureDef("Basilisk", RARE, FREMENNIK));
		l.add(new CreatureDef("Bloodveld", RARE, MORYTANIA));
		l.add(new CreatureDef("Jelly", RARE, FREMENNIK));
		l.add(new CreatureDef("Turoth", RARE, FREMENNIK));
		l.add(new CreatureDef("Aberrant spectre", RARE, MORYTANIA));
		l.add(new CreatureDef("Dust devil", RARE, DESERT));
		l.add(new CreatureDef("Kurask", RARE, FREMENNIK));
		l.add(new CreatureDef("Gargoyle", RARE, MORYTANIA));
		l.add(new CreatureDef("Hellhound", RARE, WILDERNESS, ASGARNIA));
		l.add(new CreatureDef("Greater demon", RARE, WILDERNESS, KARAMJA));
		l.add(new CreatureDef("Black demon", RARE, WILDERNESS, ASGARNIA));
		l.add(new CreatureDef("Wyrm", RARE, KEBOS));

		// ---- Tier 4: Epics (18) ----
		l.add(new CreatureDef("Ahrim the Blighted", EPIC, MORYTANIA));
		l.add(new CreatureDef("Dharok the Wretched", EPIC, MORYTANIA));
		l.add(new CreatureDef("Guthan the Infested", EPIC, MORYTANIA));
		l.add(new CreatureDef("Karil the Tainted", EPIC, MORYTANIA));
		l.add(new CreatureDef("Torag the Corrupted", EPIC, MORYTANIA));
		l.add(new CreatureDef("Verac the Defiled", EPIC, MORYTANIA));
		l.add(new CreatureDef("Mithril dragon", EPIC, FREMENNIK));
		l.add(new CreatureDef("Adamant dragon", EPIC, KEBOS));
		l.add(new CreatureDef("Nechryael", EPIC, MORYTANIA));
		l.add(new CreatureDef("Abyssal demon", EPIC, MORYTANIA, WILDERNESS));
		l.add(new CreatureDef("Cave kraken", EPIC, FREMENNIK, KEBOS));
		l.add(new CreatureDef("Smoke devil", EPIC, DESERT));
		l.add(new CreatureDef("Drake", EPIC, KEBOS));
		l.add(new CreatureDef("Hydra", EPIC, KEBOS));
		l.add(new CreatureDef("Basilisk Knight", EPIC, FREMENNIK));
		l.add(new CreatureDef("Demonic gorilla", net.runelite.api.gameval.NpcID.MM2_DEMON_GORILLA_2_MELEE, EPIC, KARAMJA));
		l.add(new CreatureDef("Lizardman shaman", EPIC, KEBOS));
		l.add(new CreatureDef("Sarachnis", EPIC, KEBOS));

		// ---- Tier 5: Legendaries (24) ----
		l.add(new CreatureDef("Giant Mole", LEGENDARY, ASGARNIA));
		l.add(new CreatureDef("King Black Dragon", LEGENDARY, WILDERNESS));
		l.add(new CreatureDef("Kalphite Queen", net.runelite.api.gameval.NpcID.KALPHITE_QUEEN, LEGENDARY, DESERT));
		l.add(new CreatureDef("Dagannoth Rex", LEGENDARY, FREMENNIK));
		l.add(new CreatureDef("Dagannoth Prime", LEGENDARY, FREMENNIK));
		l.add(new CreatureDef("Dagannoth Supreme", LEGENDARY, FREMENNIK));
		l.add(new CreatureDef("General Graardor", LEGENDARY, WILDERNESS));
		l.add(new CreatureDef("K'ril Tsutsaroth", LEGENDARY, WILDERNESS));
		l.add(new CreatureDef("Kree'arra", LEGENDARY, WILDERNESS));
		l.add(new CreatureDef("Commander Zilyana", LEGENDARY, WILDERNESS));
		l.add(new CreatureDef("Zulrah", LEGENDARY, TIRANNWN));
		l.add(new CreatureDef("Vorkath", LEGENDARY, FREMENNIK));
		l.add(new CreatureDef("Cerberus", LEGENDARY, ASGARNIA));
		l.add(new CreatureDef("Kraken", LEGENDARY, FREMENNIK));
		l.add(new CreatureDef("Thermonuclear smoke devil", LEGENDARY, DESERT));
		l.add(new CreatureDef("Alchemical Hydra", LEGENDARY, KEBOS));
		l.add(new CreatureDef("Corporeal Beast", LEGENDARY, WILDERNESS));
		l.add(new CreatureDef("TzTok-Jad", LEGENDARY, KARAMJA));
		l.add(new CreatureDef("Callisto", net.runelite.api.gameval.NpcID.CALLISTO, LEGENDARY, WILDERNESS));
		// pinned: 6504 is the old pre-rework rig; 6610 is the modern spider
		l.add(new CreatureDef("Venenatis", 6610, LEGENDARY, WILDERNESS));
		l.add(new CreatureDef("Vet'ion", LEGENDARY, WILDERNESS));
		l.add(new CreatureDef("Scorpia", LEGENDARY, WILDERNESS));
		l.add(new CreatureDef("Chaos Elemental", LEGENDARY, WILDERNESS));
		l.add(new CreatureDef("Nex", LEGENDARY, WILDERNESS));

		ALL = Collections.unmodifiableList(l);

		// ------------------------------------------------------------------
		// The Secret Dex.
		//
		// Deliberately NOT in ALL: every "iterate the roster" site in the
		// plugin — GoDex sections, the dex progress total, the Nearby radar,
		// the dev lineup, battle wild rolls — therefore ignores secrets for
		// free, and the dex total stays at 87. Only the paths that must
		// resolve a caught secret (catching, storage, companion, activity) go
		// through byKey / ALL_INCLUDING_SECRETS, which cover both lists.
		//
		// Every id and colour below comes from SecretIds — that is the single
		// table to edit after seeing one in game.
		// ------------------------------------------------------------------
		List<CreatureDef> s = new ArrayList<>();

		// Zanik uses her real cache model, no kitbash — just a mini scale.
		s.add(secret("Zanik", "Zanik", SecretIds.ZANIK_NPC,
			ModelRecipe.builder().scale(SecretIds.ZANIK_SCALE).build()));

		// Jimmothy: Giant squirrel pet, desaturated to raccoon grey, with a
		// dark band across the head (bandit mask) and rings down the tail.
		// The slab fractions are the part to tune in game: Jagex model space
		// has Y growing DOWNWARD, so 0.0 is the top of the model.
		s.add(secret("Giant squirrel", "Jimmothy", SecretIds.JIMMOTHY_NPC,
			ModelRecipe.builder()
				.desaturate()
				.paint(ModelRecipe.Axis.Y, 0.04, 0.22, SecretIds.RACCOON_DARK)
				.stripe(ModelRecipe.Axis.Z, 0.55, 1.00, SecretIds.RACCOON_DARK, 4)
				.scale(SecretIds.JIMMOTHY_SCALE)
				.build()));

		// Marcus: the desert crocodile, resolved by cache name. Palette left
		// alone — the croc's own green is the recognisable part; the recipe
		// hook is here if we ever want the friendly recolour.
		s.add(secret("Crocodile", "Marcus", SecretIds.MARCUS_NPC,
			ModelRecipe.builder().scale(SecretIds.MARCUS_SCALE).build()));

		// 3rd Age: Man base + the four item models of each set, then the body
		// repainted 3rd age white with a gold band at the head. Rarer than the
		// other three — see SpawnManager's secret weights.
		s.add(secret("Man", "3rd Age Mage", SecretIds.THIRD_AGE_NPC,
			thirdAge(SecretIds.THIRD_AGE_MAGE_ITEMS)));
		s.add(secret("Man", "3rd Age Ranger", SecretIds.THIRD_AGE_NPC,
			thirdAge(SecretIds.THIRD_AGE_RANGER_ITEMS)));
		s.add(secret("Man", "3rd Age Warrior", SecretIds.THIRD_AGE_NPC,
			thirdAge(SecretIds.THIRD_AGE_WARRIOR_ITEMS)));

		SECRET = Collections.unmodifiableList(s);

		List<CreatureDef> combined = new ArrayList<>(ALL);
		combined.addAll(SECRET);
		ALL_INCLUDING_SECRETS = Collections.unmodifiableList(combined);
	}

	/** Secrets are Legendary-difficulty catches and can turn up anywhere. */
	private static CreatureDef secret(String cacheName, String displayName, int npcId, ModelRecipe recipe)
	{
		return new CreatureDef(cacheName, displayName, npcId, LEGENDARY, true, recipe, EVERYWHERE);
	}

	private static ModelRecipe thirdAge(int[] itemIds)
	{
		return ModelRecipe.builder()
			.mergeItems(itemIds)
			.itemScale(SecretIds.THIRD_AGE_ITEM_SCALE)
			.itemLift(SecretIds.THIRD_AGE_ITEM_LIFT)
			.itemYaw(SecretIds.THIRD_AGE_ITEM_YAW)
			// gold band at the head, then torso and legs in 3rd age white
			.paint(ModelRecipe.Axis.Y, 0.00, 0.17, SecretIds.THIRD_AGE_GOLD)
			.paint(ModelRecipe.Axis.Y, 0.17, 0.90, SecretIds.THIRD_AGE_WHITE)
			.scale(SecretIds.THIRD_AGE_SCALE)
			.build();
	}

	/**
	 * Look up a creature by storage key, secrets included — catching, the
	 * collection store, the companion and the activity feed all rely on this
	 * resolving a caught secret. Nothing counts or totals through this method,
	 * so covering both lists here cannot leak a secret into the dex.
	 */
	public static CreatureDef byKey(String key)
	{
		for (CreatureDef d : ALL_INCLUDING_SECRETS)
		{
			if (d.key().equals(key))
			{
				return d;
			}
		}
		return null;
	}

	/** Explicit alias for {@link #byKey(String)}, for call sites that want to say so. */
	public static CreatureDef byKeyIncludingSecrets(String key)
	{
		return byKey(key);
	}

	/**
	 * Look up by in-game NPC name. Public roster only: live NPC observation
	 * (animation learning, orb drops) must never bind a real "Man" or
	 * "Crocodile" to a secret.
	 */
	public static CreatureDef byNpcName(String name)
	{
		if (name == null)
		{
			return null;
		}
		for (CreatureDef d : ALL)
		{
			if (d.getCacheName().equalsIgnoreCase(name))
			{
				return d;
			}
		}
		return null;
	}

	/**
	 * Cache-name lookup for the model scan only, secrets included. Secrets
	 * with a pinned NPC id are skipped by the scan, so the only secret this
	 * can return is one still resolving by name (Marcus).
	 */
	public static CreatureDef byCacheNameIncludingSecrets(String name)
	{
		if (name == null)
		{
			return null;
		}
		for (CreatureDef d : ALL_INCLUDING_SECRETS)
		{
			if (d.getCacheName().equalsIgnoreCase(name))
			{
				return d;
			}
		}
		return null;
	}

	/** Secret matching a display-name fragment (dev tooling). Case-insensitive. */
	public static CreatureDef secretMatching(String fragment)
	{
		if (fragment == null || fragment.trim().isEmpty())
		{
			return null;
		}
		String frag = fragment.trim().toLowerCase(java.util.Locale.ROOT);
		// dev shorthand: "3a mage" / "3a warrior"
		frag = frag.replaceAll("^3a\\b", "3rd age");
		String keyFrag = frag.replaceAll("[^a-z0-9]+", "_");
		for (CreatureDef d : SECRET)
		{
			if (d.getDisplayName().toLowerCase(java.util.Locale.ROOT).contains(frag)
				|| d.key().contains(keyFrag))
			{
				return d;
			}
		}
		return null;
	}

	private CreatureRoster()
	{
	}
}
