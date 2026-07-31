package com.runehunter.game;

import com.runehunter.data.OrbType;
import com.runehunter.data.Tier;
import com.runehunter.spawn.SpawnManager;
import com.runehunter.spawn.SpawnedCreature;
import com.runehunter.storage.CollectionStore;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Projectile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.ColorUtil;

/**
 * Hover detection, menu-entry catching, and the catch state machine.
 *
 * The catch plays as a Temple of Light crossing (MEP2): our Man starts on a
 * ledge, leaps across three wall-peg handholds — each peg is one catch check —
 * and reaches the far ledge to claim the creature. Fail a check and he takes
 * the famous handhold fall. Three checks each pass with probability
 * cbrt(catchChance), so the overall odds are exactly the configured
 * tier x orb chance.
 *
 * In the 3D world only the orb projectile plays; the cinematic overlay
 * renders the crossing.
 */
public class CatchManager
{
	// ---- timing, in client ticks (~20ms each) ----
	private static final int THROW_TICKS = 40;

	/** Gold used for the Secret Dex catch fanfare (matches the panel header). */
	private static final java.awt.Color SECRET_GOLD = new java.awt.Color(0xE8B84A);

	// ---- crossing segment types (public contract for the overlay) ----
	public static final int SEG_INTRO = 0;  // standing on the near ledge
	public static final int SEG_JUMP = 1;   // leaping to peg `peg` (3 = far ledge)
	public static final int SEG_GRIP = 2;   // hanging on peg `peg` — the check
	public static final int SEG_FALL = 3;   // failed check at peg `peg`
	public static final int SEG_CHEER = 4;  // made it — celebrating on the far ledge

	private static final int INTRO_TICKS = 28;
	private static final int JUMP_TICKS = 30;
	private static final int GRIP_TICKS = 46;
	private static final int FINAL_GRIP_TICKS = 60;
	private static final int FALL_TICKS = 75;
	private static final int CHEER_TICKS = 95;

	public enum Phase
	{
		IDLE, THROW, CROSSING
	}

	private final Client client;
	private final ClientThread clientThread;
	private final SpawnManager spawnManager;
	private final CollectionStore store;
	private final Runnable uiRefresh;

	private SpawnedCreature hovered;

	// -- active catch sequence state --
	private Phase phase = Phase.IDLE;
	private int phaseTick;
	private int phaseTotal = 1;
	private List<int[]> segments;   // each: {type, ticks, peg}
	private int segmentIndex;
	private int checksPassed;
	private boolean success;
	private boolean outcomeApplied;
	private SpawnedCreature target;
	private OrbType thrownOrb;

	public CatchManager(Client client, ClientThread clientThread, SpawnManager spawnManager,
		CollectionStore store, Runnable uiRefresh)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.spawnManager = spawnManager;
		this.store = store;
		this.uiRefresh = uiRefresh;
	}

	// ---- read-only snapshot for the cinematic overlay ----

	public Phase getPhase()
	{
		return phase;
	}

	/** 0..1 progress through the current phase/segment. */
	public double getPhaseProgress()
	{
		return phaseTotal <= 0 ? 0 : 1.0 - ((double) phaseTick / phaseTotal);
	}

	/** Current crossing segment type (SEG_*), or -1 outside CROSSING. */
	public int getSegmentType()
	{
		return phase == Phase.CROSSING && segments != null && segmentIndex < segments.size()
			? segments.get(segmentIndex)[0] : -1;
	}

	/** Peg index for the current segment: 0..2 pegs, 3 = far ledge. */
	public int getSegmentPeg()
	{
		return phase == Phase.CROSSING && segments != null && segmentIndex < segments.size()
			? segments.get(segmentIndex)[2] : 0;
	}

	/** Checks passed so far (0..3) — drives the pips. */
	public int getChecksPassed()
	{
		return checksPassed;
	}

	public boolean wasSuccess()
	{
		return success;
	}

	public SpawnedCreature getTarget()
	{
		return target;
	}

	public OrbType getThrownOrb()
	{
		return thrownOrb;
	}

	public SpawnedCreature getHovered()
	{
		return hovered;
	}

	// ---- hover + menu ----

	/** Call every ClientTick: hover/menu handling plus sequence advancement. */
	public void clientTick()
	{
		advanceSequence();

		hovered = findHovered();
		if (hovered == null || client.isMenuOpen() || phase != Phase.IDLE)
		{
			return;
		}

		String targetText = ColorUtil.wrapWithColorTag(hovered.displayName(),
			hovered.getDef().getTier().getColor());

		boolean anyOrbs = false;
		for (OrbType orb : OrbType.values())
		{
			if (store.orbCount(orb) <= 0)
			{
				continue;
			}
			anyOrbs = true;
			final OrbType thrown = orb;
			final SpawnedCreature targetSpawn = hovered;
			client.getMenu().createMenuEntry(-1)
				.setOption("Throw " + orb.getDisplayName())
				.setTarget(targetText)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> beginCatch(targetSpawn, thrown));
		}

		if (!anyOrbs)
		{
			client.getMenu().createMenuEntry(-1)
				.setOption("No orbs!")
				.setTarget(targetText)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> message(
					"You're out of orbs — kill monsters to earn more (higher combat = better orbs)."));
		}

		// QA: flag anything that looks broken; ::rh report lists everything flagged
		final SpawnedCreature reportSpawn = hovered;
		client.getMenu().createMenuEntry(-1)
			.setOption("Report bug")
			.setTarget(targetText)
			.setType(MenuAction.RUNELITE)
			.onClick(e -> promptBugReport(reportSpawn));
	}

	private void promptBugReport(SpawnedCreature s)
	{
		final String name = s.getDef().getNpcName();
		javax.swing.SwingUtilities.invokeLater(() ->
		{
			String note = (String) javax.swing.JOptionPane.showInputDialog(null,
				"What's wrong with " + name + "?\n(animation, size, position, anything)",
				"RuneHunter — report bug",
				javax.swing.JOptionPane.PLAIN_MESSAGE, null, null, "");
			if (note == null)
			{
				return; // cancelled
			}
			store.reportBug(s.getDef(), note.trim());
			clientThread.invokeLater(() ->
				message("Reported " + name + " — run ::rh report to see the full list."));
		});
	}

	private SpawnedCreature findHovered()
	{
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return null;
		}
		for (SpawnedCreature s : spawnManager.getActiveSpawns())
		{
			LocalPoint lp = LocalPoint.fromWorld(client, s.getWorldPoint());
			if (lp == null)
			{
				continue;
			}
			Shape clickbox = Perspective.getClickbox(client, client.getTopLevelWorldView(), s.getModel(),
				s.getObject().getOrientation(), lp.getX(), lp.getY(),
				Perspective.getTileHeight(client, lp, s.getWorldPoint().getPlane()));
			if (clickbox != null && clickbox.contains(mouse.getX(), mouse.getY()))
			{
				return s;
			}
		}
		return null;
	}

	// ---- sequence ----

	private void beginCatch(SpawnedCreature s, OrbType orb)
	{
		if (phase != Phase.IDLE)
		{
			message("You're already mid-throw!");
			return;
		}
		if (!spawnManager.getActiveSpawns().contains(s))
		{
			return; // despawned since click
		}
		if (!store.consumeOrb(orb))
		{
			message("You don't have any " + orb.getDisplayName().toLowerCase() + "s left.");
			return;
		}

		target = s;
		thrownOrb = orb;
		buildCrossing(s, orb);

		launchOrbProjectile(s);
		phase = Phase.THROW;
		phaseTick = THROW_TICKS;
		phaseTotal = THROW_TICKS;
		uiRefresh.run();
	}

	/**
	 * Roll the three checks honestly (each passes with cbrt(catchChance), so
	 * overall = catchChance) and build the segment script: intro, then
	 * jump+grip per surviving peg; a failed check ends in the fall, a full
	 * crossing ends on the far ledge with a cheer.
	 */
	private void buildCrossing(SpawnedCreature s, OrbType orb)
	{
		// Secrets are always a Legendary-difficulty catch, whatever tier they
		// carry — pinned here so a future secret can't be made easy by accident.
		double base = s.getDef().isSecret()
			? Tier.LEGENDARY.getBaseCatchChance()
			: s.getDef().getTier().getBaseCatchChance();
		double chance = Math.min(0.99, base * orb.getCatchMultiplier());
		double perCheck = Math.cbrt(chance);
		ThreadLocalRandom rng = ThreadLocalRandom.current();

		int failAt = -1;
		if (orb != OrbType.ELDRITCH)
		{
			for (int check = 0; check < 3; check++)
			{
				if (rng.nextDouble() > perCheck)
				{
					failAt = check;
					break;
				}
			}
		}
		success = failAt < 0;
		checksPassed = 0;
		outcomeApplied = false;

		segments = new ArrayList<>();
		segments.add(new int[]{SEG_INTRO, INTRO_TICKS, 0});
		int lastPeg = success ? 2 : failAt;
		for (int peg = 0; peg <= lastPeg; peg++)
		{
			segments.add(new int[]{SEG_JUMP, JUMP_TICKS, peg});
			segments.add(new int[]{SEG_GRIP, peg == 2 ? FINAL_GRIP_TICKS : GRIP_TICKS, peg});
		}
		if (success)
		{
			segments.add(new int[]{SEG_JUMP, JUMP_TICKS, 3});
			segments.add(new int[]{SEG_CHEER, CHEER_TICKS, 3});
		}
		else
		{
			segments.add(new int[]{SEG_FALL, FALL_TICKS, failAt});
		}
	}

	private void launchOrbProjectile(SpawnedCreature s)
	{
		try
		{
			if (client.getLocalPlayer() == null)
			{
				return;
			}
			WorldPoint from = client.getLocalPlayer().getWorldLocation();
			int cycle = client.getGameCycle();
			Projectile p = client.createProjectile(SpotanimID.ENCHANTED_VIAL_TRAVEL,
				from, 90, client.getLocalPlayer(),
				s.getWorldPoint(), 30, null,
				cycle, cycle + THROW_TICKS, 20, 60);
			client.getProjectiles().addLast(p);
		}
		catch (Exception ignored)
		{
			// purely cosmetic — the catch continues without the arc
		}
	}

	private void advanceSequence()
	{
		if (phase == Phase.IDLE)
		{
			return;
		}

		// Target vanished mid-sequence (reseed, scene load)? Bail out cleanly
		// unless the outcome has already been applied.
		if (!outcomeApplied && !spawnManager.getActiveSpawns().contains(target))
		{
			cleanupSequence();
			return;
		}

		if (--phaseTick > 0)
		{
			return;
		}

		if (phase == Phase.THROW)
		{
			phase = Phase.CROSSING;
			segmentIndex = 0;
			phaseTick = segments.get(0)[1];
			phaseTotal = phaseTick;
			message("The " + target.displayName() + " is snared! Bring it home across the gap...");
			return;
		}

		// CROSSING: current segment finished
		int[] done = segments.get(segmentIndex);
		onSegmentEnd(done);

		segmentIndex++;
		if (segmentIndex >= segments.size())
		{
			cleanupSequence();
			return;
		}
		int[] next = segments.get(segmentIndex);
		phaseTick = next[1];
		phaseTotal = phaseTick;
		onSegmentStart(next);
	}

	private void onSegmentEnd(int[] segment)
	{
		if (segment[0] == SEG_GRIP)
		{
			// surviving a grip = check passed (a failed check's grip is
			// followed by SEG_FALL — count it passed only if we didn't fall)
			boolean fallNext = segmentIndex + 1 < segments.size()
				&& segments.get(segmentIndex + 1)[0] == SEG_FALL;
			if (!fallNext)
			{
				checksPassed++;
			}
		}
	}

	private void onSegmentStart(int[] segment)
	{
		switch (segment[0])
		{
			case SEG_GRIP:
				if (segment[2] == 1)
				{
					message("Halfway across... steady...");
				}
				else if (segment[2] == 2)
				{
					message("Last handhold... it's holding... it's holding...");
				}
				break;

			case SEG_FALL:
				applyFailure();
				break;

			case SEG_CHEER:
				applySuccess();
				break;

			default:
				break;
		}
	}

	private void applySuccess()
	{
		if (outcomeApplied)
		{
			return;
		}
		outcomeApplied = true;
		SpawnedCreature s = target;
		int essence = store.recordCatch(s.getDef(), s.isShiny());
		spawnManager.despawn(s);
		if (s.getDef().isSecret())
		{
			// Secret Dex fanfare — the moment the whole channel exists for
			message("You feel like you've found something you weren't supposed to...");
			message(ColorUtil.wrapWithColorTag(
				"SECRET DEX: " + s.getDef().getDisplayName() + " is yours.", SECRET_GOLD));
		}
		else if (s.isShiny())
		{
			message("✨ Incredible! You caught a " + s.displayName() + "! ✨");
		}
		else
		{
			message("Gotcha! " + s.getDef().getNpcName() + " was caught!");
		}
		if (essence > 0)
		{
			message("Duplicate converted: +" + essence + " essence.");
		}
		uiRefresh.run();
	}

	private void applyFailure()
	{
		if (outcomeApplied)
		{
			return;
		}
		outcomeApplied = true;
		SpawnedCreature s = target;
		s.setFailedAttempts(s.getFailedAttempts() + 1);
		boolean fleeProtected = s.isShiny() && s.getFailedAttempts() <= 1;
		double fleeChance = s.getDef().getTier().fleeChance(s.getFailedAttempts());
		if (!fleeProtected && ThreadLocalRandom.current().nextDouble() < fleeChance)
		{
			spawnManager.despawn(s);
			message("He lost his grip! The " + s.displayName() + " fled!");
		}
		else
		{
			message("He lost his grip! The " + s.displayName() + " broke free... try again.");
		}
		uiRefresh.run();
	}

	private void cleanupSequence()
	{
		phase = Phase.IDLE;
		segments = null;
		segmentIndex = 0;
		target = null;
		thrownOrb = null;
	}

	private void message(String msg)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
	}
}
