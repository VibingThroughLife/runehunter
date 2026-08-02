package com.runehunter.game;

import com.runehunter.data.GearItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Old-school death rules for badge fights and boss fights.
 *
 * <h2>Why "protect 3" is inverted</h2>
 *
 * OSRS protects three items because you carry a whole inventory. A RuneHunter
 * creature has exactly three gear slots, so protecting three would mean losing
 * nothing. The feeling ports across by inverting it: <b>the creature itself is
 * always protected — there is no permadeath — and its equipped gear is the stake.</b>
 * You keep yourself and lose your kit, which is what an OSRS death actually feels
 * like.
 *
 * <h2>The rules</h2>
 *
 * <ul>
 *   <li><b>Unskulled:</b> keep your single best piece, drop the other two. This is
 *       Protect Item, and it means a death is a setback rather than a wipe.</li>
 *   <li><b>Skulled:</b> drop all three. Entering a fight skulled pays more, so it
 *       is a real decision instead of a menu toggle. The battle overlay already
 *       speaks skulls, so players read it instantly.</li>
 * </ul>
 *
 * Dropped gear becomes a {@code LootPile} at the fight location: yours alone for
 * sixty seconds, then open. Unrecovered gear is gone for good and must be
 * re-smithed — which is precisely what gives the gathering skills a permanent
 * reason to exist.
 *
 * <h2>Trust boundary</h2>
 *
 * This class is deliberately pure: no networking, no client, no randomness. It
 * runs on the <b>losing player's own client</b>, which is what keeps a
 * challenger-authoritative duel host from inventing losses. The host decides who
 * lost the fight; only the loser's client decides what leaves their account, and
 * the handoff is confirmed through the same nonce'd two-phase accept the trade
 * flow uses.
 */
public final class DeathStake
{
	/** Seconds the pile is visible only to its owner. Then it is open to all. */
	public static final int OWNER_ONLY_SECONDS = 60;

	/** Extra reward multiplier for fighting skulled, since you risk everything. */
	public static final double SKULL_REWARD_BONUS = 1.5;

	private DeathStake()
	{
	}

	/** The outcome of a death: what survived, and what hit the floor. */
	public static final class Outcome
	{
		private final List<GearItem> kept;
		private final List<GearItem> dropped;
		private final boolean skulled;

		Outcome(List<GearItem> kept, List<GearItem> dropped, boolean skulled)
		{
			this.kept = kept;
			this.dropped = dropped;
			this.skulled = skulled;
		}

		/** Gear still equipped after the death. */
		public List<GearItem> getKept()
		{
			return java.util.Collections.unmodifiableList(kept);
		}

		/** Gear that fell on the floor. Recoverable for {@link #OWNER_ONLY_SECONDS}. */
		public List<GearItem> getDropped()
		{
			return java.util.Collections.unmodifiableList(dropped);
		}

		public boolean wasSkulled()
		{
			return skulled;
		}

		public boolean lostAnything()
		{
			return !dropped.isEmpty();
		}

		/** Player-facing summary, in the plugin's own voice. */
		public String message()
		{
			if (dropped.isEmpty())
			{
				return "Your creature fainted but kept everything it was wearing.";
			}
			final StringBuilder sb = new StringBuilder("Your creature fainted and dropped ");
			for (int i = 0; i < dropped.size(); i++)
			{
				if (i > 0)
				{
					sb.append(i == dropped.size() - 1 ? " and " : ", ");
				}
				sb.append(dropped.get(i).getDisplayName().toLowerCase());
			}
			sb.append(". You have ").append(OWNER_ONLY_SECONDS)
				.append(" seconds to get back to it.");
			return sb.toString();
		}
	}

	/**
	 * Rank gear by how much losing it would hurt. Attack and defence sum is the
	 * honest measure — it is what actually feeds BattleStats — and rarity breaks
	 * ties, since a rarer piece is harder to replace even at equal stats.
	 */
	private static final Comparator<GearItem> BY_VALUE =
		Comparator.<GearItem>comparingInt(g -> g.getAtk() + g.getDef())
			.thenComparing(Comparator.comparingInt(GearItem::getDropWeight).reversed());

	/**
	 * Apply the death rules.
	 *
	 * @param equipped  the creature's three slots; nulls allowed for empty slots
	 * @param skulled   true if the fight was entered skulled
	 * @return what was kept and what was dropped
	 */
	public static Outcome resolve(GearItem[] equipped, boolean skulled)
	{
		final List<GearItem> worn = new ArrayList<>(3);
		if (equipped != null)
		{
			for (GearItem g : equipped)
			{
				if (g != null)
				{
					worn.add(g);
				}
			}
		}

		if (worn.isEmpty())
		{
			return new Outcome(new ArrayList<>(), new ArrayList<>(), skulled);
		}

		if (skulled)
		{
			return new Outcome(new ArrayList<>(), new ArrayList<>(worn), skulled);
		}

		// Unskulled: Protect Item on the single best piece.
		worn.sort(BY_VALUE.reversed());
		final List<GearItem> kept = new ArrayList<>(worn.subList(0, 1));
		final List<GearItem> dropped = new ArrayList<>(worn.subList(1, worn.size()));
		return new Outcome(kept, dropped, skulled);
	}

	/**
	 * Which piece Protect Item would save, so the UI can mark it before the fight
	 * starts. Players should never be surprised by what they were about to lose.
	 */
	public static GearItem protectedPiece(GearItem[] equipped)
	{
		final Outcome o = resolve(equipped, false);
		return o.getKept().isEmpty() ? null : o.getKept().get(0);
	}

	/** Reward multiplier for the risk taken. */
	public static double rewardMultiplier(boolean skulled)
	{
		return skulled ? SKULL_REWARD_BONUS : 1.0;
	}
}
