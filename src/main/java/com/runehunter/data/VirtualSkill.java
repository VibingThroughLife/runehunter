package com.runehunter.data;

import java.awt.Color;
import net.runelite.api.Skill;

/**
 * Virtual OSRS: skills your creatures train while you play.
 *
 * <h2>Why xp is proportional to the real action, not the virtual level</h2>
 *
 * The first cut of this granted xp based on which virtual resource tier you'd
 * unlocked. Real rate data killed it. Actions per hour in OSRS span <b>247x</b> —
 * 25/hr killing Alchemical Hydra up to 6,174/hr on Blast Furnace gold — and,
 * crucially, high-level players train the FASTEST resource, not the highest tier.
 * The best Woodcutting xp in the game at level 99 is teak, a level 35 tree; magic
 * and redwood are the two worst rates in the skill. So a per-action reward scaled
 * by virtual level double-dips: fast method, many procs, and big xp each.
 *
 * The fix is to reward the xp itself:
 *
 * <pre>  virtual xp = (real xp of the action) x {@link #XP_MULTIPLIER}</pre>
 *
 * on a {@link #PROC_CHANCE} roll. That makes virtual xp/hr equal real xp/hr by
 * construction, so no method, skill or tick-manipulation trick can outrun it, and
 * there is no per-skill constant to tune or defend. At a multiplier of 10 a virtual
 * 99 costs exactly the same 13,034,431 xp a real 99 does — which is both the
 * grindiest honest setting and the easiest one to explain to a player.
 *
 * <h2>Resource tiers still matter</h2>
 *
 * Tiers no longer drive xp. They decide WHAT you find, which is flavour and, more
 * importantly, feeds Smithing: better virtual level means better ore, which means
 * better {@link GearItem} for your creatures.
 *
 * <h2>Badges</h2>
 *
 * Each skill has its own badge track. A badge is earned by beating a themed
 * creature from the roster, and raises that skill's level cap. Because the first
 * badges gate levels the xp curve gives away almost free, early progress is
 * governed by <em>content</em> rather than time — which is what stops a maxed
 * account walking through the early game. See {@link Badge}.
 */
public enum VirtualSkill
{
	WOODCUTTING("Woodcutting", Skill.WOODCUTTING, Kind.GATHERING, new Color(0x8D6E63),
		new Tier[]{
			new Tier(1, "Logs"), new Tier(15, "Oak logs"), new Tier(30, "Willow logs"),
			new Tier(45, "Maple logs"), new Tier(60, "Yew logs"), new Tier(75, "Magic logs"),
			new Tier(90, "Redwood logs"),
		},
		new Badge[]{
			new Badge(1, 20, "wolf", "Timberfang"),
			new Badge(2, 40, "chaos_druid", "The Grovewarden"),
			new Badge(3, 60, "kurask", "Thornhide"),
			new Badge(4, 80, "sarachnis", "The Canopy Queen"),
			new Badge(5, 99, "zulrah", "Deeproot"),
		}),

	FISHING("Fishing", Skill.FISHING, Kind.GATHERING, new Color(0x4FC3F7),
		new Tier[]{
			new Tier(1, "Shrimp"), new Tier(20, "Trout"), new Tier(35, "Lobster"),
			new Tier(50, "Swordfish"), new Tier(62, "Monkfish"), new Tier(76, "Shark"),
			new Tier(85, "Anglerfish"),
		},
		new Badge[]{
			new Badge(1, 20, "duck", "Pondskipper"),
			new Badge(2, 40, "rockslug", "Brinecrawler"),
			new Badge(3, 60, "jelly", "The Tidewalker"),
			new Badge(4, 80, "cave_kraken", "Inkmaw"),
			new Badge(5, 99, "kraken", "The Deep"),
		}),

	MINING("Mining", Skill.MINING, Kind.GATHERING, new Color(0x90A4AE),
		new Tier[]{
			new Tier(1, "Copper ore"), new Tier(15, "Iron ore"), new Tier(30, "Coal"),
			new Tier(40, "Mithril ore"), new Tier(55, "Adamantite ore"),
			new Tier(70, "Runite ore"), new Tier(85, "Amethyst"),
		},
		new Badge[]{
			new Badge(1, 20, "rock_crab", "Pebblehide"),
			new Badge(2, 40, "hill_giant", "Quarrymaw"),
			new Badge(3, 60, "basilisk", "Stonegaze"),
			new Badge(4, 80, "mithril_dragon", "The Vein Tyrant"),
			new Badge(5, 99, "giant_mole", "Deepdelver"),
		}),

	HUNTER("Hunter", Skill.HUNTER, Kind.GATHERING, new Color(0xA1887F),
		new Tier[]{
			new Tier(1, "Bird snare"), new Tier(19, "Swamp lizard"),
			new Tier(43, "Red salamander"), new Tier(60, "Grey chinchompa"),
			new Tier(63, "Red chinchompa"), new Tier(80, "Black chinchompa"),
			new Tier(91, "Herbiboar"),
		},
		new Badge[]{
			new Badge(1, 20, "scorpion", "Pinch"),
			new Badge(2, 40, "kalphite_worker", "Skitterjaw"),
			new Badge(3, 60, "wyrm", "Coilscale"),
			new Badge(4, 80, "drake", "Emberwing"),
			new Badge(5, 99, "vorkath", "The Frozen Hunt"),
		}),

	SLAYER("Slayer", Skill.SLAYER, Kind.GATHERING, new Color(0xEF5350),
		new Tier[]{
			new Tier(1, "Creature essence"), new Tier(20, "Refined essence"),
			new Tier(40, "Dense essence"), new Tier(60, "Volatile essence"),
			new Tier(75, "Ancient essence"), new Tier(90, "Primordial essence"),
		},
		new Badge[]{
			new Badge(1, 20, "giant_rat", "Gnawer"),
			new Badge(2, 40, "crawling_hand", "The Grasp"),
			new Badge(3, 60, "bloodveld", "Lashtongue"),
			new Badge(4, 80, "abyssal_demon", "The Rift Walker"),
			new Badge(5, 99, "cerberus", "Hellkeeper"),
		}),

	/**
	 * The artisan skill and the payoff: procs CONSUME banked ore to forge
	 * {@link GearItem}s rather than gathering anything.
	 */
	SMITHING("Smithing", Skill.SMITHING, Kind.ARTISAN, new Color(0xFFB74D),
		new Tier[]{
			new Tier(1, "Bronze bar"), new Tier(20, "Iron bar"), new Tier(40, "Mithril bar"),
			new Tier(55, "Adamantite bar"), new Tier(70, "Runite bar"),
			new Tier(85, "Dragon fragment"),
		},
		new Badge[]{
			new Badge(1, 20, "goblin", "Slaghammer"),
			new Badge(2, 40, "hobgoblin", "The Bellowsmith"),
			new Badge(3, 60, "bronze_dragon", "Emberscale"),
			new Badge(4, 80, "adamant_dragon", "The Forgewyrm"),
			new Badge(5, 99, "king_black_dragon", "The Anvil King"),
		});

	public enum Kind
	{
		/** Procs yield resources. */
		GATHERING,
		/** Procs consume resources and yield gear. */
		ARTISAN
	}

	/**
	 * Virtual xp granted per point of real xp on a successful proc.
	 *
	 * At 10, with a 1/10 proc, virtual xp/hr equals real xp/hr — so a virtual 99
	 * costs exactly one real 99's worth of training in that skill. Lower it to make
	 * the grind harsher (5 = two real 99s), raise it to soften (20 = half).
	 */
	public static final int XP_MULTIPLIER = 10;

	/** Chance a real xp-granting action fires a virtual one. */
	public static final double PROC_CHANCE = 0.10;

	/** Resources banked per successful gathering proc. Flavour only — not xp. */
	public static final int BUNDLE_SIZE = 10;

	/** A resource band, unlocked at {@code minLevel}. Decides what you find. */
	public static final class Tier
	{
		private final int minLevel;
		private final String resource;

		public Tier(int minLevel, String resource)
		{
			this.minLevel = minLevel;
			this.resource = resource;
		}

		public int getMinLevel()
		{
			return minLevel;
		}

		public String getResource()
		{
			return resource;
		}
	}

	/**
	 * A gym. Beat {@code opponentKey} — a creature already in the roster — to raise
	 * this skill's level cap to {@code levelCap}.
	 *
	 * The opponent tier climbs with the badge (common, uncommon, rare, epic,
	 * legendary), so a badge fight demands a companion strong enough to take it,
	 * which comes from hunting and battling rather than skilling. That is the
	 * mechanism: real stats cannot buy you past a badge.
	 */
	public static final class Badge
	{
		private final int number;
		private final int levelCap;
		private final String opponentKey;
		private final String title;

		public Badge(int number, int levelCap, String opponentKey, String title)
		{
			this.number = number;
			this.levelCap = levelCap;
			this.opponentKey = opponentKey;
			this.title = title;
		}

		public int getNumber()
		{
			return number;
		}

		public int getLevelCap()
		{
			return levelCap;
		}

		/** Roster key of the creature you must beat. */
		public String getOpponentKey()
		{
			return opponentKey;
		}

		/** Display name for the fight, e.g. "Timberfang". */
		public String getTitle()
		{
			return title;
		}
	}

	/** Level cap before any badge is earned. */
	public static final int STARTING_CAP = 10;

	private final String displayName;
	private final Skill realSkill;
	private final Kind kind;
	private final Color color;
	private final Tier[] tiers;
	private final Badge[] badges;

	VirtualSkill(String displayName, Skill realSkill, Kind kind, Color color,
		Tier[] tiers, Badge[] badges)
	{
		this.displayName = displayName;
		this.realSkill = realSkill;
		this.kind = kind;
		this.color = color;
		this.tiers = tiers;
		this.badges = badges;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	/** The real OSRS skill whose xp drops drive this one. */
	public Skill getRealSkill()
	{
		return realSkill;
	}

	public Kind getKind()
	{
		return kind;
	}

	public Color getColor()
	{
		return color;
	}

	public Tier[] getTiers()
	{
		return tiers.clone();
	}

	public Badge[] getBadges()
	{
		return badges.clone();
	}

	/** Best resource band unlocked at {@code level}. Never null. */
	public Tier tierFor(int level)
	{
		Tier best = tiers[0];
		for (Tier t : tiers)
		{
			if (level >= t.getMinLevel())
			{
				best = t;
			}
		}
		return best;
	}

	/**
	 * Virtual xp for one proc, given the real xp the triggering action awarded.
	 * Deliberately independent of virtual level — see the class javadoc.
	 */
	public static int xpForAction(int realXpGained)
	{
		return Math.max(1, realXpGained) * XP_MULTIPLIER;
	}

	/** The next badge to earn, or null once all are held. */
	public Badge nextBadge(int badgesEarned)
	{
		return badgesEarned >= badges.length ? null : badges[badgesEarned];
	}

	/** Level ceiling granted by the badges held so far. */
	public int capForBadges(int badgesEarned)
	{
		if (badgesEarned <= 0)
		{
			return STARTING_CAP;
		}
		return badges[Math.min(badgesEarned, badges.length) - 1].getLevelCap();
	}

	/**
	 * The binding ceiling: the lower of your badge cap and your real level in the
	 * mirrored skill. Badges stop maxed accounts sprinting; the real-level check
	 * stops a fresh account training a skill it hasn't touched.
	 */
	public int effectiveCap(int badgesEarned, int realLevel)
	{
		return Math.max(1, Math.min(capForBadges(badgesEarned), Math.max(1, realLevel)));
	}

	public static VirtualSkill byRealSkill(Skill skill)
	{
		for (VirtualSkill v : values())
		{
			if (v.realSkill == skill)
			{
				return v;
			}
		}
		return null;
	}
}
