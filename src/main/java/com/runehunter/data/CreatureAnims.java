package com.runehunter.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Curated idle/walk animation table for the roster — generated from a full
 * cache dump ({@code ./gradlew -q dumpAnims}): for every roster NPC id the
 * dump lists the NpcManager's standingAnimation/walkingAnimation, i.e. the
 * exact rig the real NPC uses. Values below are for the id our scan resolves
 * (lowest combat-leveled interactible variant, or the roster's pinned
 * override), so the rig always matches the model we build.
 *
 * Raw ints on purpose: many of these have no gameval AnimationID name, and
 * the dump — not the constant names — is the source of truth.
 *
 * Keys are {@link CreatureDef#key()} values.
 */
public final class CreatureAnims
{
	private static final Map<String, int[]> TABLE = new HashMap<>();

	private static void put(String key, int idle, int walk)
	{
		TABLE.put(key, new int[]{idle, walk});
	}

	static
	{
		// ---- Tier 1: Commons ----
		put("chicken", 5386, 5385);            // id 1173
		put("cow", 5852, 5848);                // id 2790
		put("goblin", 6181, 6180);             // id 655 (yes, 6181 IS the real goblin stand)
		put("giant_rat", 4932, 4931);          // id 2510
		put("imp", 171, 168);                  // id 3134
		put("scorpion", 6252, 6253);           // id 2479
		put("wolf", 6580, 6556);               // id 106
		put("seagull", 6815, 6819);            // id 1338
		put("duck", 6818, 6817);               // id 1838 — walk is the swim, curated pick
		put("rock_crab", 1310, 1311);          // id 100 (NOT the 3424 sand-crab rig)
		put("unicorn", 6374, 6373);            // id 2837
		put("penguin", 5668, 5666);            // id 2063

		// ---- Tier 2: Uncommons ----
		put("hill_giant", 4650, 4649);         // id 2098
		put("moss_giant", 4656, 4654);         // id 2090
		put("ice_giant", 4670, 4669);          // id 2085
		put("hobgoblin", 166, 162);            // id 132
		put("green_dragon", 90, 79);           // id 260 — shared chromatic dragon rig
		put("lesser_demon", 66, 63);           // id 2005 — shared demon rig
		put("chaos_druid", 808, 819);          // id 520 — human rig
		put("kalphite_worker", 6218, 6220);    // id 955
		put("crawling_hand", 1588, 1589);      // id 448
		put("cave_crawler", 226, 225);         // id 406
		put("banshee", 1522, 1521);            // id 414
		put("rockslug", 1566, 1564);           // id 421
		put("cockatrice", 1561, 1559);         // id 419
		put("pyrefiend", 1578, 1579);          // id 433
		put("tzhaar_ket", 2603, 2601);         // id 2173

		// ---- Tier 3: Rares ----
		put("blue_dragon", 90, 79);            // id 265
		put("red_dragon", 90, 79);             // id 247
		put("black_dragon", 90, 79);           // id 252
		put("bronze_dragon", 90, 79);          // id 270
		put("iron_dragon", 90, 79);            // id 272
		put("steel_dragon", 90, 79);           // id 274 (pinned; id 139 is an oddball rig)
		put("basilisk", 1545, 1544);           // id 417
		put("bloodveld", 1551, 1549);          // id 484
		put("jelly", 1583, 1584);              // id 437
		put("turoth", 1594, 1593);             // id 426
		put("aberrant_spectre", 1506, 1505);   // id 2
		put("dust_devil", 1556, 1554);         // id 423
		put("kurask", 1511, 1510);             // id 410
		put("gargoyle", 1516, 1515);           // id 412
		put("hellhound", 6561, 6583);          // id 104
		put("greater_demon", 66, 63);          // id 2025
		put("black_demon", 66, 63);            // id 240
		put("wyrm", 8266, 8266);               // id 8610 — same anim both slots per dump

		// ---- Tier 4: Epics ----
		put("ahrim_the_blighted", 813, 1205);  // id 1672 — staff-carry human rig
		put("dharok_the_wretched", 2065, 2064); // id 1673
		put("guthan_the_infested", 813, 1205); // id 1674 — spear-carry, same rig as Ahrim
		put("karil_the_tainted", 808, 819);    // id 1675 — plain human rig, NOT the xbow consts
		put("torag_the_corrupted", 808, 819);  // id 1676
		put("verac_the_defiled", 2061, 2060);  // id 1677
		put("mithril_dragon", 90, 79);         // id 2919
		put("adamant_dragon", 90, 79);         // id 8030
		put("nechryael", 1527, 1526);          // id 8
		put("abyssal_demon", 1536, 1534);      // id 415
		put("cave_kraken", 3989, 3989);        // id 492 — bob-in-place both slots
		put("smoke_devil", 1829, 1828);        // id 498
		put("drake", 8274, 8273);              // id 8612 (id 2004 "Drake" is a cb0 duck!)
		put("hydra", 8260, 8259);              // id 8609
		put("basilisk_knight", 8497, 8498);    // id 9293
		put("demonic_gorilla", 7230, 7233);    // pinned id 7145
		put("lizardman_shaman", 7191, 7195);   // id 6766
		put("sarachnis", 8320, 8319);          // id 8713

		// ---- Tier 5: Legendaries ----
		put("giant_mole", 3309, 3313);         // id 5779
		put("king_black_dragon", 90, 4635);    // id 239 — dragon stand, KBD's own walk
		put("kalphite_queen", 6239, 6238);     // pinned id 963 (crawling form)
		put("dagannoth_rex", 2850, 2849);      // id 2267
		put("dagannoth_prime", 2850, 2849);    // id 2266
		put("dagannoth_supreme", 2850, 2849);  // id 2265
		put("general_graardor", 7017, 7016);   // id 2215
		put("k_ril_tsutsaroth", 6935, 4070);   // id 3129
		put("kree_arra", 6976, 6977);          // id 3162 — walk restored per dump
		put("commander_zilyana", 6966, 6965);  // id 2205
		put("zulrah", 5070, 5070);             // id 2042 — sway both slots
		put("vorkath", 7948, 7947);            // id 8060
		put("cerberus", 4484, 4488);           // id 5862
		put("kraken", 3989, 3989);             // id 494 — the dump confirms 3989 is right
		put("thermonuclear_smoke_devil", 1829, 1828); // id 499
		put("alchemical_hydra", 8233, 8232);   // id 8615 (stage 1)
		put("corporeal_beast", 1678, 1684);    // id 319
		put("tztok_jad", 2650, 2651);          // id 3127
		put("callisto", 10011, 10009);         // pinned id 6609 (reworked rig)
		put("venenatis", 9986, 9988);          // pinned id 6610 (reworked rig)
		put("vet_ion", 9965, 9967);            // id 6611
		put("scorpia", 6252, 6262);            // id 6615 — scorpion stand, her own walk
		put("chaos_elemental", 3144, 3145);    // id 2054
		put("nex", 9177, 9175);                // id 11278

		// ---- Secret Dex ----
		// Not from the anim dump (these ids aren't on the public roster), so
		// each secret rides the rig of the base NPC it borrows. The ids live
		// in SecretIds — read the notes there before swapping any of them.
		put("zanik", SecretIds.ZANIK_IDLE, SecretIds.ZANIK_WALK);           // human rig 808/819
		put("jimmothy", SecretIds.JIMMOTHY_IDLE, SecretIds.JIMMOTHY_WALK);  // squirrel pet 7309/7310
		put("marcus", SecretIds.MARCUS_IDLE, SecretIds.MARCUS_WALK);        // crocodile 2037/2036
		put("3rd_age_mage", SecretIds.THIRD_AGE_IDLE, SecretIds.THIRD_AGE_WALK);
		put("3rd_age_ranger", SecretIds.THIRD_AGE_IDLE, SecretIds.THIRD_AGE_WALK);
		put("3rd_age_warrior", SecretIds.THIRD_AGE_IDLE, SecretIds.THIRD_AGE_WALK);
	}

	private CreatureAnims()
	{
	}

	/** Idle animation id for a creature key, or -1 if not in the table. */
	public static int idle(String key)
	{
		int[] a = TABLE.get(key);
		return a == null ? -1 : a[0];
	}

	/** Walk animation id for a creature key, or -1 if not in the table. */
	public static int walk(String key)
	{
		int[] a = TABLE.get(key);
		return a == null ? -1 : a[1];
	}

	/** How many creatures have a table entry (for dev diagnostics). */
	public static int coverage()
	{
		return TABLE.size();
	}
}
