package com.runehunter.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Body-shape families used for GoDex silhouettes: uncaught creatures show a
 * dark mystery silhouette of their archetype (Pokedex-style), caught ones show
 * it filled in their tier color. Icons live at resources sil_<name>.png.
 */
public enum Archetype
{
	DRAGON, DEMON, HUMANOID, BEAST, BIRD, BUG,
	AQUATIC, SLIME, GHOST, SPIDER, GIANT, SERPENT;

	private static final Map<String, Archetype> BY_KEY = new HashMap<>();

	private static void put(Archetype a, String... keys)
	{
		for (String k : keys)
		{
			BY_KEY.put(k, a);
		}
	}

	static
	{
		put(BIRD, "chicken", "seagull", "duck", "penguin", "cockatrice", "kree_arra");
		put(BEAST, "cow", "giant_rat", "wolf", "unicorn", "bloodveld", "turoth", "kurask",
			"hellhound", "demonic_gorilla", "giant_mole", "cerberus", "callisto",
			"dagannoth_rex", "dagannoth_prime", "dagannoth_supreme", "corporeal_beast", "tztok_jad");
		put(HUMANOID, "goblin", "hobgoblin", "chaos_druid", "tzhaar_ket", "commander_zilyana",
			"ahrim_the_blighted", "dharok_the_wretched", "guthan_the_infested",
			"karil_the_tainted", "torag_the_corrupted", "verac_the_defiled",
			"lizardman_shaman", "vet_ion");
		put(DEMON, "imp", "lesser_demon", "greater_demon", "black_demon", "pyrefiend",
			"gargoyle", "nechryael", "abyssal_demon", "k_ril_tsutsaroth", "nex");
		put(BUG, "scorpion", "kalphite_worker", "cave_crawler", "rock_crab",
			"kalphite_queen", "scorpia");
		put(SPIDER, "sarachnis", "venenatis");
		put(SLIME, "rockslug", "jelly", "crawling_hand");
		put(GHOST, "banshee", "aberrant_spectre", "dust_devil", "smoke_devil",
			"thermonuclear_smoke_devil", "chaos_elemental");
		put(GIANT, "hill_giant", "moss_giant", "ice_giant", "general_graardor");
		put(DRAGON, "green_dragon", "blue_dragon", "red_dragon", "black_dragon",
			"bronze_dragon", "iron_dragon", "steel_dragon", "mithril_dragon",
			"adamant_dragon", "drake", "king_black_dragon", "vorkath");
		put(SERPENT, "basilisk", "basilisk_knight", "wyrm", "hydra", "alchemical_hydra", "zulrah");
		put(AQUATIC, "cave_kraken", "kraken");

		// ---- Secret Dex (the panel draws its own gold marker over these) ----
		put(HUMANOID, "zanik", "3rd_age_mage", "3rd_age_ranger", "3rd_age_warrior");
		put(BEAST, "jimmothy");
		put(SERPENT, "marcus");
	}

	public static Archetype of(CreatureDef def)
	{
		return BY_KEY.getOrDefault(def.key(), BEAST);
	}

	public String resource()
	{
		return "sil_" + name().toLowerCase(java.util.Locale.ROOT) + ".png";
	}
}
