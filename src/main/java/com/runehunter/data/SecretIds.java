package com.runehunter.data;

import net.runelite.api.JagexColor;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;

/**
 * <b>The one table.</b> Every cache id and colour the Secret Dex depends on
 * lives here so it can be hot-swapped after eyeballing a secret in game —
 * nothing else in the plugin hardcodes a secret's id.
 *
 * <p>None of these were verifiable at authoring time (the build box has no
 * game cache), so each entry carries the reasoning and the alternates worth
 * trying. Change a number here, {@code ::rh secret reload}, look again.
 *
 * <h2>Zanik</h2>
 * Several Zanik NPCs exist. {@code LOTG_ZANIK} (Land of the Goblins) is the
 * newest overworld variant and the one that walks around Yubiusk, so it should
 * carry a normal stand/walk rig. Alternates, in order of preference:
 * {@code LOTG_ZANIK_FOLLOWER} (11261 — a follower, guaranteed walk rig) and
 * {@code DORGESH_ZANIK} (3186 — the classic Dorgesh-Kaan model).
 * Avoid the {@code DTTD_*} cutscene variants: several are pose-locked.
 *
 * <h2>Jimmothy (raccoon kitbash)</h2>
 * Base candidates were the Giant squirrel pet, a wolf and the Giant rat.
 * <b>Picked: the Giant squirrel pet</b> ({@code SKILLPET_AGILITY}). At mini
 * scale silhouette is everything, and it is the only one of the three with the
 * two features that make a raccoon read instantly: a low, hunched quadruped
 * body and a huge bushy tail held clear of the ground (the tail is what the
 * rings need somewhere to live). The wolf's tail is thin and drooped and its
 * legs are far too long; the Giant rat's pointed snout and bald whip tail read
 * "vermin", which is exactly wrong for Jimmothy. The squirrel is also already
 * pet-sized, so the mini scale barely has to fight the source model.
 *
 * <h2>Marcus</h2>
 * Resolved by cache <i>name</i> ("Crocodile") like the rest of the roster, so
 * we pick up whatever the live desert crocodile id is. Pin {@link #MARCUS_NPC}
 * to a real id if the scan ever grabs a quest lookalike.
 *
 * <h2>3rd Age</h2>
 * No OSRS NPC wears 3rd age, so these are Man-base kitbashes: the four item
 * models per set are merged onto the Man model and the body is repainted 3rd
 * age white/gold. Item ids are the treasure-trail ("TRAIL_") set — Jagex's
 * internal names for 3rd age gear are TRAIL_MAGE_*, TRAIL_RANGER_* and
 * TRAIL_SILVER_PLATE_* / TRAIL_FIGHTER_*.
 */
public final class SecretIds
{
	// ------------------------------------------------------------------
	// NPC bases
	// ------------------------------------------------------------------

	/** Zanik — Land of the Goblins overworld variant. Alt: 11261, 3186. */
	public static final int ZANIK_NPC = NpcID.LOTG_ZANIK;

	/** Jimmothy's base — the Giant squirrel (Agility) pet. Alt: NpcID.WOLF (106). */
	public static final int JIMMOTHY_NPC = NpcID.SKILLPET_AGILITY;

	/** Marcus's base — -1 means "resolve the cache name 'Crocodile' by scan". */
	public static final int MARCUS_NPC = -1;

	/** 3rd Age bodies — the plain level-2 Man. */
	public static final int THIRD_AGE_NPC = NpcID.MAN;

	// ------------------------------------------------------------------
	// 3rd age item models (merged onto the Man base)
	// ------------------------------------------------------------------

	/** 3rd age mage hat / robe top / robe / amulet. */
	public static final int[] THIRD_AGE_MAGE_ITEMS = {
		ItemID.TRAIL_MAGE_HAT,       // 10342 — 3rd age mage hat
		ItemID.TRAIL_MAGE_TORSO,     // 10338 — 3rd age robe top
		ItemID.TRAIL_MAGE_LEGS,      // 10340 — 3rd age robe
		ItemID.TRAIL_MAGE_AMULET,    // 10344 — 3rd age amulet
	};

	/** 3rd age coif / range top / range legs / vambraces. */
	public static final int[] THIRD_AGE_RANGER_ITEMS = {
		ItemID.TRAIL_RANGER_COIF,      // 10334 — 3rd age coif
		ItemID.TRAIL_RANGER_TORSO,     // 10330 — 3rd age range top
		ItemID.TRAIL_RANGER_LEGS,      // 10332 — 3rd age range legs
		ItemID.TRAIL_RANGER_VAMBRACES, // 10336 — 3rd age vambraces
	};

	/** 3rd age full helmet / platebody / platelegs / kiteshield. */
	public static final int[] THIRD_AGE_WARRIOR_ITEMS = {
		ItemID.TRAIL_FIGHTER_HELM,       // 10350 — 3rd age full helmet
		ItemID.TRAIL_SILVER_PLATE_CHEST, // 10348 — 3rd age platebody
		ItemID.TRAIL_SILVER_PLATE_LEGS,  // 10346 — 3rd age platelegs
		ItemID.TRAIL_FIGHTER_SHIELD,     // 10352 — 3rd age kiteshield
	};

	/**
	 * Uniform scale applied to merged item models (native = 128). Inventory
	 * models are authored for the item icon, not for a body, so this is the
	 * first dial to turn if the armour looks like confetti around the Man.
	 */
	public static final int THIRD_AGE_ITEM_SCALE = 128;

	/**
	 * Height offset for merged item models, model units, positive = up.
	 *
	 * Item models are authored around their own origin for the inventory icon, so
	 * merging them onto a body at lift 0 drops them all at the Man's feet — which
	 * is exactly what "clipped at the feet" looks like. A player-scale model is
	 * roughly 200 units tall, so torso height is around 100-120.
	 *
	 * Tune with Toolkit -> "Reload models" -> respawn, no restart needed. If one
	 * value can't satisfy helm, body and weapon at once, that's the signal to split
	 * this into per-slot offsets rather than keep compromising.
	 */
	public static final int THIRD_AGE_ITEM_LIFT = 110;

	/**
	 * Yaw correction for merged item models, in degrees: 0, 90, 180 or 270.
	 *
	 * The RuneLite API only exposes {@code ItemComposition.getInventoryModel()} —
	 * there is no accessor for a worn/equipped model. So what gets merged is the
	 * INVENTORY ICON model, authored to look right under the icon camera rather
	 * than on a body, which is why the armour arrives rotated.
	 *
	 * {@code Mesh} only offers Y-axis (yaw) rotation, so this dial can fix a
	 * left/right facing problem but NOT a pitch problem. If the armour is lying
	 * on its back rather than turned sideways, no value here will save it — take
	 * the fallback and drop .mergeItems(), because the white/gold recolour alone
	 * still reads as 3rd Age and looks deliberate rather than broken.
	 */
	public static final int THIRD_AGE_ITEM_YAW = 90;

	// ------------------------------------------------------------------
	// Palette (packed Jagex HSL: hue 0-63, saturation 0-7, luminance 0-127)
	// ------------------------------------------------------------------

	/** Raccoon bandit mask / tail rings — near black. */
	public static final short RACCOON_DARK = JagexColor.packHSL(0, 0, 12);

	/** 3rd age white. */
	public static final short THIRD_AGE_WHITE = JagexColor.packHSL(0, 0, 105);

	/** 3rd age gold trim. */
	public static final short THIRD_AGE_GOLD = JagexColor.packHSL(6, 5, 78);

	// ------------------------------------------------------------------
	// Mini scales (native = 128; the normal rule is 70 / npcSize)
	// ------------------------------------------------------------------

	public static final int ZANIK_SCALE = 62;
	public static final int JIMMOTHY_SCALE = 74;
	public static final int MARCUS_SCALE = 46;
	public static final int THIRD_AGE_SCALE = 60;

	// ------------------------------------------------------------------
	// Rigs
	// ------------------------------------------------------------------

	/**
	 * Zanik is a cave goblin on the standard humanoid skeleton, so the plain
	 * human stand/walk is the safe default and is what she uses when simply
	 * standing around. If she looks stiff in game, try the Dorgeshuun pair
	 * {@link AnimationID#DORGESH_GOBLIN_READY_SPEAR} (6006) /
	 * {@link AnimationID#DORGESH_GOBLIN_WALK_SPEAR} (6005) — those are the
	 * cave-goblin-specific rig, but they pose the arms as if carrying a spear.
	 */
	public static final int ZANIK_IDLE = AnimationID.HUMAN_READY;   // 808
	public static final int ZANIK_WALK = AnimationID.HUMAN_WALK_F;  // 819

	/** Giant squirrel pet rig — its "walk" is a hop. */
	public static final int JIMMOTHY_IDLE = AnimationID.SQUIRREL_IDLE; // 7309
	public static final int JIMMOTHY_WALK = AnimationID.SQUIRREL_HOP;  // 7310

	/** Desert crocodile rig. */
	public static final int MARCUS_IDLE = AnimationID.CROC_READY; // 2037
	public static final int MARCUS_WALK = AnimationID.CROC_WALK;  // 2036

	/** 3rd Age humanoids ride the Man's rig. */
	public static final int THIRD_AGE_IDLE = AnimationID.HUMAN_READY;  // 808
	public static final int THIRD_AGE_WALK = AnimationID.HUMAN_WALK_F; // 819

	private SecretIds()
	{
	}
}
