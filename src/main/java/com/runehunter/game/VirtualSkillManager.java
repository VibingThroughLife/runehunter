package com.runehunter.game;

import com.runehunter.data.SkillXp;
import com.runehunter.data.VirtualSkill;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;

/**
 * Virtual OSRS — your creatures train while you play.
 *
 * Watches real xp drops. Roughly one in ten becomes a virtual proc: a bundle of
 * resources, virtual xp, occasionally a level. Gathered resources feed Smithing,
 * which forges the gear creatures wear in battle.
 *
 * What this deliberately does NOT do: touch real OSRS. It grants nothing in-game,
 * automates nothing, and reveals nothing the client doesn't already show. It only
 * observes xp you earned by playing.
 *
 * Persistence is RSProfile-scoped like {@link com.runehunter.storage.CollectionStore},
 * so virtual progress is per-account and stays on this machine.
 */
public class VirtualSkillManager
{
	private static final String GROUP = "runehunter";

	/**
	 * Ceiling on procs per rolling hour. The model assumes ~107/hr from typical
	 * skilling, but 2-tick teaks or blast furnace fire several times faster and
	 * would collapse the curve. This sits well above normal play — you will never
	 * notice it unless you are deliberately doing something degenerate.
	 */
	private static final int PROC_CAP_PER_HOUR = 200;
	private static final long HOUR_MILLIS = 3_600_000L;

	/** Ignore absurd single xp jumps (quest rewards, lamps) — they aren't "actions". */
	private static final int MAX_CREDIBLE_ACTION_XP = 10_000;

	private final Client client;
	private final ConfigManager configManager;

	/** Virtual xp per skill. */
	private final Map<VirtualSkill, Integer> xp = new EnumMap<>(VirtualSkill.class);
	/** Badges earned per skill. Each raises that skill's level cap. */
	private final Map<VirtualSkill, Integer> badges = new EnumMap<>(VirtualSkill.class);
	/** Resource name -> quantity banked. Insertion-ordered so the UI reads sensibly. */
	private final Map<String, Integer> bank = new LinkedHashMap<>();
	/** Last observed real total xp, to derive per-action deltas. */
	private final Map<Skill, Integer> lastRealXp = new EnumMap<>(Skill.class);
	/** Timestamps of recent procs, for the rolling hourly cap. */
	private final Deque<Long> recentProcs = new ArrayDeque<>();

	private Consumer<Proc> listener = p -> { };

	public VirtualSkillManager(Client client, ConfigManager configManager)
	{
		this.client = client;
		this.configManager = configManager;
	}

	/** What happened on a successful proc. Handed to the notification layer. */
	public static final class Proc
	{
		private final VirtualSkill skill;
		private final String resource;
		private final int quantity;
		private final int xpGained;
		private final int newLevel;
		private final boolean levelUp;
		private final boolean atCap;

		Proc(VirtualSkill skill, String resource, int quantity, int xpGained,
			int newLevel, boolean levelUp, boolean atCap)
		{
			this.atCap = atCap;
			this.skill = skill;
			this.resource = resource;
			this.quantity = quantity;
			this.xpGained = xpGained;
			this.newLevel = newLevel;
			this.levelUp = levelUp;
		}

		public VirtualSkill getSkill()
		{
			return skill;
		}

		public String getResource()
		{
			return resource;
		}

		public int getQuantity()
		{
			return quantity;
		}

		public int getXpGained()
		{
			return xpGained;
		}

		public int getNewLevel()
		{
			return newLevel;
		}

		public boolean isLevelUp()
		{
			return levelUp;
		}

		/** True when the proc was blocked by a badge or real-level ceiling. */
		public boolean isAtCap()
		{
			return atCap;
		}

		/** "Your creatures found 10 magic logs." */
		public String findMessage()
		{
			return resource == null ? "" : "Your creatures found " + quantity + " " + resource.toLowerCase() + ".";
		}

		/** "Congratulations, your creatures reached level 76 Woodcutting." */
		public String levelMessage()
		{
			return "Congratulations, your creatures reached level "
				+ newLevel + " " + skill.getDisplayName() + ".";
		}
	}

	/** Notified on every successful proc. Wire this to the toast overlay. */
	public void setListener(Consumer<Proc> listener)
	{
		this.listener = listener == null ? p -> { } : listener;
	}

	// ------------------------------------------------------------------
	// Event entry point
	// ------------------------------------------------------------------

	/**
	 * Call from the plugin's {@code @Subscribe onStatChanged}. Runs on the client
	 * thread and does no allocation on the common path (most xp drops are not procs).
	 */
	public void handleStatChanged(StatChanged event)
	{
		final Skill real = event.getSkill();
		final VirtualSkill skill = VirtualSkill.byRealSkill(real);
		if (skill == null)
		{
			return; // not a mirrored skill (and filters out Skill.OVERALL)
		}

		final int total = event.getXp();
		final Integer previous = lastRealXp.put(real, total);
		if (previous == null)
		{
			// First sighting this session — establish a baseline without proccing,
			// otherwise logging in would fire a proc off the full career total.
			return;
		}

		final int delta = total - previous;
		if (delta <= 0 || delta > MAX_CREDIBLE_ACTION_XP)
		{
			return; // no gain, or a lamp/quest reward rather than an action
		}

		if (ThreadLocalRandom.current().nextDouble() >= VirtualSkill.PROC_CHANCE)
		{
			return;
		}

		if (!allowProc())
		{
			return; // hourly ceiling reached
		}

		award(skill, event.getLevel(), delta);
	}

	/** Rolling-hour ceiling. Trims expired entries as it goes. */
	private boolean allowProc()
	{
		final long now = System.currentTimeMillis();
		while (!recentProcs.isEmpty() && now - recentProcs.peekFirst() > HOUR_MILLIS)
		{
			recentProcs.pollFirst();
		}
		if (recentProcs.size() >= PROC_CAP_PER_HOUR)
		{
			return false;
		}
		recentProcs.addLast(now);
		return true;
	}

	/**
	 * Grant one proc.
	 *
	 * Virtual xp is proportional to the REAL xp the action awarded, never to virtual
	 * level — OSRS action rates span 247x and high-level players train the fastest
	 * resource rather than the highest tier, so any level-scaled reward double-dips.
	 *
	 * The ceiling is the lower of the skill's badge cap and your real level. Badges
	 * are what stop a maxed account sprinting the early game: the first few gate
	 * levels the xp curve gives away almost free, so early progress is content-gated
	 * rather than time-gated.
	 */
	private void award(VirtualSkill skill, int realLevel, int realXpGained)
	{
		final int currentXp = getXp(skill);
		final int currentLevel = SkillXp.levelForXp(currentXp);
		final int cap = skill.effectiveCap(getBadges(skill), realLevel);

		if (currentLevel >= cap || currentLevel >= SkillXp.MAX_LEVEL)
		{
			listener.accept(new Proc(skill, null, 0, 0, currentLevel, false, true));
			return;
		}

		final int gained = VirtualSkill.xpForAction(realXpGained);
		// Never let one proc vault past the cap.
		final int ceilingXp = SkillXp.xpForLevel(cap);
		final int updated = Math.min(ceilingXp, Math.min(SkillXp.MAX_XP, currentXp + gained));
		final boolean levelUp = SkillXp.levelForXp(updated) > currentLevel;

		xp.put(skill, updated);
		rsSet("vskill_" + skill.name(), Integer.toString(updated));

		final VirtualSkill.Tier tier = skill.tierFor(SkillXp.levelForXp(updated));
		if (skill.getKind() == VirtualSkill.Kind.GATHERING)
		{
			addResource(tier.getResource(), VirtualSkill.BUNDLE_SIZE);
		}

		listener.accept(new Proc(skill, tier.getResource(), VirtualSkill.BUNDLE_SIZE,
			updated - currentXp, SkillXp.levelForXp(updated), levelUp, false));
	}

	// ------------------------------------------------------------------
	// State
	// ------------------------------------------------------------------

	public int getBadges(VirtualSkill skill)
	{
		return badges.getOrDefault(skill, 0);
	}

	/** Award the next badge for this skill, unlocking the higher level cap. */
	public void awardBadge(VirtualSkill skill)
	{
		final int held = getBadges(skill);
		if (held >= skill.getBadges().length)
		{
			return;
		}
		badges.put(skill, held + 1);
		rsSet("vbadge_" + skill.name(), Integer.toString(held + 1));
	}

	/** The fight standing between this skill and its next cap, or null when maxed. */
	public VirtualSkill.Badge nextBadge(VirtualSkill skill)
	{
		return skill.nextBadge(getBadges(skill));
	}

	/** Current binding ceiling for this skill. */
	public int levelCap(VirtualSkill skill)
	{
		return skill.effectiveCap(getBadges(skill), getRealLevel(skill));
	}

	public int getXp(VirtualSkill skill)
	{
		return xp.getOrDefault(skill, 0);
	}

	public int getLevel(VirtualSkill skill)
	{
		return SkillXp.levelForXp(getXp(skill));
	}

	/** Real level for the mirrored skill, i.e. the current ceiling. */
	public int getRealLevel(VirtualSkill skill)
	{
		try
		{
			return Math.max(1, client.getRealSkillLevel(skill.getRealSkill()));
		}
		catch (Exception e)
		{
			return 1; // not logged in yet
		}
	}

	/** True when this skill is sitting at its real-level ceiling and cannot progress. */
	public boolean isCapped(VirtualSkill skill)
	{
		return getLevel(skill) >= levelCap(skill);
	}

	public int resourceCount(String resource)
	{
		return bank.getOrDefault(resource, 0);
	}

	public Map<String, Integer> getBank()
	{
		return java.util.Collections.unmodifiableMap(bank);
	}

	public void addResource(String resource, int amount)
	{
		if (amount <= 0)
		{
			return;
		}
		bank.merge(resource, amount, Integer::sum);
		saveBank();
	}

	/** Spend banked resources. Returns false and changes nothing if short. */
	public boolean consumeResource(String resource, int amount)
	{
		final int have = bank.getOrDefault(resource, 0);
		if (amount <= 0 || have < amount)
		{
			return false;
		}
		if (have == amount)
		{
			bank.remove(resource);
		}
		else
		{
			bank.put(resource, have - amount);
		}
		saveBank();
		return true;
	}

	/** Total virtual level across all skills — the headline number for the panel. */
	public int totalLevel()
	{
		int total = 0;
		for (VirtualSkill s : VirtualSkill.values())
		{
			total += getLevel(s);
		}
		return total;
	}

	// ------------------------------------------------------------------
	// Persistence — RSProfile-scoped, same shape as CollectionStore
	// ------------------------------------------------------------------

	public void load()
	{
		xp.clear();
		badges.clear();
		bank.clear();
		lastRealXp.clear();
		recentProcs.clear();

		for (VirtualSkill s : VirtualSkill.values())
		{
			final String b = rsGet("vbadge_" + s.name());
			if (b != null)
			{
				try
				{
					badges.put(s, Math.max(0, Math.min(s.getBadges().length, Integer.parseInt(b.trim()))));
				}
				catch (NumberFormatException ignored)
				{
					// corrupt key: treat as no badges rather than refusing to load
				}
			}

			final String v = rsGet("vskill_" + s.name());
			if (v != null)
			{
				try
				{
					xp.put(s, Math.max(0, Math.min(SkillXp.MAX_XP, Integer.parseInt(v.trim()))));
				}
				catch (NumberFormatException ignored)
				{
					// corrupt key: treat as zero rather than refusing to load
				}
			}
		}

		final String raw = rsGet("vbank");
		if (raw != null && !raw.isEmpty())
		{
			for (String entry : raw.split(";"))
			{
				final int eq = entry.lastIndexOf('=');
				if (eq <= 0)
				{
					continue;
				}
				try
				{
					final int n = Integer.parseInt(entry.substring(eq + 1).trim());
					if (n > 0)
					{
						bank.put(entry.substring(0, eq), n);
					}
				}
				catch (NumberFormatException ignored)
				{
					// skip the malformed entry, keep the rest
				}
			}
		}
	}

	private void saveBank()
	{
		if (bank.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(GROUP,
				com.runehunter.storage.SaveProfile.key("vbank"));
			return;
		}
		final StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Integer> e : bank.entrySet())
		{
			if (sb.length() > 0)
			{
				sb.append(';');
			}
			// Resource names are our own constants — no ';' or '=' in any of them.
			sb.append(e.getKey()).append('=').append(e.getValue());
		}
		rsSet("vbank", sb.toString());
	}

	private String rsGet(String key)
	{
		return configManager.getRSProfileConfiguration(GROUP,
			com.runehunter.storage.SaveProfile.key(key));
	}

	private void rsSet(String key, String value)
	{
		configManager.setRSProfileConfiguration(GROUP,
			com.runehunter.storage.SaveProfile.key(key), value);
	}
}
