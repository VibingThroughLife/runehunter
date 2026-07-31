package com.runehunter.game;

import com.runehunter.RuneHunterConfig;
import com.runehunter.data.CreatureDef;
import com.runehunter.data.CreatureRoster;
import com.runehunter.data.GearItem;
import com.runehunter.data.Tier;
import com.runehunter.spawn.SpawnManager;
import com.runehunter.storage.CollectionStore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;

/**
 * Random wild encounters and the REAL-TIME battle core. Fully client-side.
 *
 * No turns: the wild NPC attacks on a fixed game-tick cycle, telegraphing
 * each hit 3 ticks ahead with its attack style. Defense is OSRS overhead
 * prayers — click the matching protection prayer before impact to DEFLECT
 * (0 damage, chip reflected, special energy gained). Wilds switch styles
 * between attacks, RARE+ wilds occasionally smite your prayers off and lock
 * them for a few ticks, and EPIC+/overleveled wilds throw skull-marked
 * UNAVOIDABLE hits that punch through prayer for partial damage. Prayer
 * points drain while a prayer is lit, so true OSRS prayer flicking is the
 * skilled play.
 *
 * Offense is automatic (the companion auto-attacks on its own cycle, fed by
 * the existing stat/gear math); deflects charge a 2.5x SPECIAL. RUN
 * mid-fight uses the flee-chance math, with a short lockout on failure.
 *
 * THREADING: all game logic runs on the client thread (tick / clientTick).
 * Input methods called from the AWT mouse thread only set atomic request
 * fields — never touch client APIs or game state directly. All chat
 * messages go through ClientThread.invokeLater.
 *
 * PACING: every combat timing scales with RuneHunterConfig.BattlePace percent
 * (FAST = 100 = the original numbers; higher = slower). The pace supplier
 * is re-read at battle start, so config flips apply to the next fight.
 */
public class BattleManager implements BattleSource
{
	// ---- encounter pacing (game ticks, 0.6s each) ----
	private static final int ENCOUNTER_ONE_IN = 900;      // ~1 per 9 min of play
	private static final int POST_BATTLE_COOLDOWN_TICKS = 200; // ~2 min
	private static final long FAINT_MILLIS = 3 * 60_000L;
	private static final int PROMPT_TIMEOUT_TICKS = 50;   // 30s to decide
	private static final int RESULT_TICKS = 220;          // result linger (client ticks)

	// ---- real-time combat tuning (base numbers at FAST/100% pace) ----
	/** Game ticks between wild attacks, by tier ordinal (COMMON..LEGENDARY). */
	private static final int[] ATTACK_CYCLE = {7, 6, 5, 5, 4};
	/** Base telegraph length in game ticks before impact (pace-scaled). */
	public static final int TELEGRAPH_TICKS = 3;
	private static final int COMPANION_ATTACK_TICKS = 5;
	private static final int PRAYER_LOCK_TICKS = 3;       // smite lockout
	private static final int RUN_LOCK_TICKS = 5;          // failed-escape lockout
	private static final double STYLE_SWITCH_BASE = 0.30; // +0.10 per tier ordinal
	private static final double DISABLE_CHANCE = 0.15;    // RARE+ only
	private static final double UNAVOIDABLE_CHANCE = 0.20; // EPIC+ or overleveled
	public static final double UNAVOIDABLE_FRACTION = 0.40; // through-prayer damage
	public static final double CHIP_FRACTION = 0.15;      // deflect reflect damage
	public static final int SPECIAL_PER_DEFLECT = 25;
	public static final int SPECIAL_MAX = 100;
	public static final double SPECIAL_MULT = 2.5;

	public enum State
	{
		IDLE, PROMPT, FIGHT, VICTORY, DEFEAT, FLED
	}

	/** What the wild is about to throw (telegraphed 3 ticks ahead). */
	public enum AttackStyle
	{
		MELEE, RANGED, MAGIC, DISABLE, UNAVOIDABLE
	}

	/** OSRS overhead protection prayers — one lit at a time. */
	public enum Prayer
	{
		MELEE, RANGED, MAGIC;

		public boolean deflects(AttackStyle style)
		{
			return style.name().equals(name());
		}
	}

	private final Client client;
	private final ClientThread clientThread;
	private final SpawnManager spawnManager;
	private final CollectionStore store;
	private final Runnable uiRefresh;

	// ---- battle state (mutated on the client thread only) ----
	private volatile State state = State.IDLE;
	private CreatureDef wild;
	private int wildLevel;
	private CreatureDef companion;
	private volatile int myHp;
	private int myMaxHp;
	private volatile int wildHp;
	private int wildMaxHp;
	private int cooldownTicks;
	private int promptTicks;
	private int resultTicks;
	private long faintedUntil;
	private GearItem lastDrop;

	// pace scaling — resolved at battle start from the supplier
	private Supplier<RuneHunterConfig.BattlePace> paceSupplier = () -> RuneHunterConfig.BattlePace.FAST;
	private int pacePercent = 100;
	private volatile int telegraphTicks = TELEGRAPH_TICKS;
	private int companionCycle = COMPANION_ATTACK_TICKS;
	private int prayerLockLen = PRAYER_LOCK_TICKS;
	private int runLockLen = RUN_LOCK_TICKS;
	private int prayerDrainEvery = 1;
	private int prayerDrainCounter;

	// real-time combat
	private int attackCycle;
	private int nextAttackIn;                  // game ticks until impact
	private AttackStyle baseStyle;             // wild's current basic style
	private volatile AttackStyle incoming;     // null until telegraphed
	private int companionAttackIn;
	private volatile Prayer activePrayer;
	private volatile int prayerPoints;
	private int maxPrayerPoints;
	private volatile int prayerLockTicks;
	private volatile int specialEnergy;
	private volatile int runLockTicks;

	// hit events for the overlay (value written before its seq bumps)
	private volatile int myHitDmg;
	private volatile int myHitSeq;
	private volatile int wildHitDmg;
	private volatile int wildHitSeq;
	private volatile int deflectSeq;
	private volatile int smiteSeq;

	// log
	private final Deque<String> log = new ArrayDeque<>();
	private volatile int logVersion;

	// ---- input requests (written from AWT, consumed on the client thread) ----
	private final AtomicReference<Prayer> prayerClick = new AtomicReference<>();
	private final AtomicBoolean specialRequested = new AtomicBoolean();
	private final AtomicBoolean runRequested = new AtomicBoolean();
	private final AtomicBoolean acceptRequested = new AtomicBoolean();
	private final AtomicBoolean declineRequested = new AtomicBoolean();
	private final AtomicBoolean dismissRequested = new AtomicBoolean();
	private volatile AttackStyle debugForcedStyle;

	public BattleManager(Client client, ClientThread clientThread, SpawnManager spawnManager,
		CollectionStore store, Runnable uiRefresh)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.spawnManager = spawnManager;
		this.store = store;
		this.uiRefresh = uiRefresh;
	}

	/** Wired by the plugin: battleManager.setPaceSupplier(config::battlePace). */
	public void setPaceSupplier(Supplier<RuneHunterConfig.BattlePace> supplier)
	{
		if (supplier != null)
		{
			this.paceSupplier = supplier;
		}
	}

	// ------------------------------------------------------------------
	// snapshot getters for the overlay (read-only)
	// ------------------------------------------------------------------

	@Override
	public State getState()
	{
		return state;
	}

	@Override
	public boolean isDuel()
	{
		return false;
	}

	@Override
	public String getWildOwnerName()
	{
		return null; // wild encounters have no owner
	}

	@Override
	public String getPromptText()
	{
		CreatureDef w = wild;
		return w == null ? "" : "A wild " + w.getNpcName() + " challenges you!";
	}

	@Override
	public boolean isWildHitDeflect()
	{
		return false; // wilds never deflect the companion
	}

	/** Telegraph length at the current pace (pip count for the overlay). */
	@Override
	public int getTelegraphTicks()
	{
		return telegraphTicks;
	}

	public CreatureDef getWild()
	{
		return wild;
	}

	public int getWildLevel()
	{
		return wildLevel;
	}

	public CreatureDef getCompanion()
	{
		return companion;
	}

	public int getCompanionLevel()
	{
		return companion == null ? 1 : store.getLevel(companion);
	}

	public int getMyHp()
	{
		return myHp;
	}

	public int getMyMaxHp()
	{
		return myMaxHp;
	}

	public int getWildHp()
	{
		return wildHp;
	}

	public int getWildMaxHp()
	{
		return wildMaxHp;
	}

	/** Currently lit protection prayer, or null. */
	public Prayer getActivePrayer()
	{
		return activePrayer;
	}

	public int getPrayerPoints()
	{
		return prayerPoints;
	}

	public int getMaxPrayerPoints()
	{
		return maxPrayerPoints;
	}

	/** Game ticks the prayers stay smite-locked; 0 = usable. */
	public int getPrayerLockTicks()
	{
		return prayerLockTicks;
	}

	/** The telegraphed incoming attack, or null between telegraphs. */
	public AttackStyle getIncomingStyle()
	{
		return incoming;
	}

	/** Game ticks until the telegraphed attack lands (TELEGRAPH_TICKS..1). */
	public int getImpactTicks()
	{
		return incoming == null ? -1 : nextAttackIn;
	}

	/** 0..SPECIAL_MAX; the SPECIAL button lights at SPECIAL_MAX. */
	public int getSpecialEnergy()
	{
		return specialEnergy;
	}

	public boolean isSpecialReady()
	{
		return specialEnergy >= SPECIAL_MAX;
	}

	/** Game ticks the RUN button stays locked after a failed escape. */
	public int getRunLockTicks()
	{
		return runLockTicks;
	}

	/** Bumps whenever the companion takes a hit (0 damage = deflect splat). */
	public int getMyHitSeq()
	{
		return myHitSeq;
	}

	public int getMyHitDmg()
	{
		return myHitDmg;
	}

	/** Bumps whenever the wild takes a hit (auto-attack, chip, or special). */
	public int getWildHitSeq()
	{
		return wildHitSeq;
	}

	public int getWildHitDmg()
	{
		return wildHitDmg;
	}

	/** Bumps on every successful deflect. */
	public int getDeflectSeq()
	{
		return deflectSeq;
	}

	/** Bumps when a prayer-disable special lands. */
	public int getSmiteSeq()
	{
		return smiteSeq;
	}

	/** Bumps whenever the battle log changes — cheap dirty-check for the UI. */
	public int getLogVersion()
	{
		return logVersion;
	}

	public List<String> getLog()
	{
		synchronized (log)
		{
			return new ArrayList<>(log);
		}
	}

	public GearItem getLastDrop()
	{
		return lastDrop;
	}

	public boolean isCompanionFainted()
	{
		return System.currentTimeMillis() < faintedUntil;
	}

	// ------------------------------------------------------------------
	// inputs — AWT-safe: ONLY set request fields, consumed on client thread
	// ------------------------------------------------------------------

	/** PROMPT: accept the challenge. */
	public void accept()
	{
		acceptRequested.set(true);
	}

	/** PROMPT: slip away — always works before the fight starts. */
	public void decline()
	{
		declineRequested.set(true);
	}

	/** FIGHT: toggle an overhead prayer (click again to flick off). */
	public void clickPrayer(Prayer prayer)
	{
		prayerClick.set(prayer);
	}

	/** FIGHT: unleash the special once energy is full. */
	public void clickSpecial()
	{
		specialRequested.set(true);
	}

	/** FIGHT: attempt to run using the flee-chance math. */
	public void clickRun()
	{
		runRequested.set(true);
	}

	/** Result screens: dismiss early. */
	public void dismiss()
	{
		dismissRequested.set(true);
	}

	/**
	 * Dev: force the NEXT telegraphed attack.
	 * Accepts melee | ranged | magic | disable | unavoidable (alias "skull").
	 */
	public boolean debugForceStyle(String name)
	{
		if (name == null)
		{
			return false;
		}
		String n = name.trim().toLowerCase(Locale.ROOT);
		if (n.equals("skull"))
		{
			n = "unavoidable";
		}
		for (AttackStyle s : AttackStyle.values())
		{
			if (s.name().toLowerCase(Locale.ROOT).equals(n))
			{
				debugForcedStyle = s;
				return true;
			}
		}
		return false;
	}

	/** Dev: force an encounter (random roster wild, or by name fragment). */
	public boolean forceEncounter(String nameFragment)
	{
		if (state != State.IDLE)
		{
			return false;
		}
		CreatureDef pick = null;
		if (nameFragment != null && !nameFragment.isEmpty())
		{
			String frag = nameFragment.toLowerCase(Locale.ROOT);
			for (CreatureDef d : CreatureRoster.ALL)
			{
				if (d.getNpcName().toLowerCase(Locale.ROOT).contains(frag))
				{
					pick = d;
					break;
				}
			}
			if (pick == null)
			{
				return false;
			}
		}
		startEncounter(pick);
		return true;
	}

	// ------------------------------------------------------------------
	// ticking (client thread)
	// ------------------------------------------------------------------

	/** Game tick (0.6s): encounter rolls, prompt timeout, combat heartbeat. */
	public void tick(boolean battlesEnabled)
	{
		if (cooldownTicks > 0)
		{
			cooldownTicks--;
		}
		if (state == State.PROMPT && --promptTicks <= 0)
		{
			doDecline();
			return;
		}
		if (state == State.FIGHT)
		{
			fightTick();
			return;
		}
		if (state != State.IDLE || !battlesEnabled || cooldownTicks > 0
			|| client.getGameState() != GameState.LOGGED_IN
			|| store.getCompanionKey() == null || isCompanionFainted())
		{
			return;
		}
		if (ThreadLocalRandom.current().nextInt(ENCOUNTER_ONE_IN) == 0)
		{
			startEncounter(null);
		}
	}

	/** Client tick (~20ms): consume input requests fast; result linger. */
	public void clientTick()
	{
		// drain every request each pass so stale clicks can never fire later
		Prayer prayer = prayerClick.getAndSet(null);
		boolean special = specialRequested.getAndSet(false);
		boolean run = runRequested.getAndSet(false);
		boolean acceptReq = acceptRequested.getAndSet(false);
		boolean declineReq = declineRequested.getAndSet(false);
		boolean dismissReq = dismissRequested.getAndSet(false);

		switch (state)
		{
			case PROMPT:
				if (acceptReq)
				{
					beginFight();
				}
				else if (declineReq)
				{
					doDecline();
				}
				break;

			case FIGHT:
				if (prayer != null)
				{
					togglePrayer(prayer);
				}
				if (special)
				{
					trySpecial();
				}
				if (run && state == State.FIGHT)
				{
					tryRun();
				}
				break;

			case VICTORY:
			case DEFEAT:
			case FLED:
				if (dismissReq || --resultTicks <= 0)
				{
					endBattle();
				}
				break;

			default:
				break;
		}
	}

	// ------------------------------------------------------------------
	// combat heartbeat (game ticks)
	// ------------------------------------------------------------------

	private void fightTick()
	{
		// prayer drain — 1 point per prayerDrainEvery ticks while lit, so the
		// pool lasts through slower-paced (longer) fights
		if (activePrayer != null && ++prayerDrainCounter >= prayerDrainEvery)
		{
			prayerDrainCounter = 0;
			prayerPoints--;
			if (prayerPoints <= 0)
			{
				prayerPoints = 0;
				activePrayer = null;
				logLine("Out of prayer points!");
			}
		}
		if (prayerLockTicks > 0)
		{
			prayerLockTicks--;
		}
		if (runLockTicks > 0)
		{
			runLockTicks--;
		}

		// companion offense is automatic — the player's skill is defense
		if (--companionAttackIn <= 0)
		{
			companionAttackIn = companionCycle;
			companionStrike(1.0, false);
			if (state != State.FIGHT)
			{
				return; // that hit ended it
			}
		}

		// wild attack cycle: telegraph, then impact
		nextAttackIn--;
		if (nextAttackIn == telegraphTicks)
		{
			rollTelegraph();
		}
		if (nextAttackIn <= 0)
		{
			resolveWildAttack();
			incoming = null;
			nextAttackIn = attackCycle;
		}
	}

	/** Decide what the wild throws next — shown to the player immediately. */
	private void rollTelegraph()
	{
		Random rng = ThreadLocalRandom.current();

		AttackStyle forced = debugForcedStyle;
		debugForcedStyle = null;
		if (forced != null)
		{
			if (forced != AttackStyle.DISABLE && forced != AttackStyle.UNAVOIDABLE)
			{
				baseStyle = forced;
			}
			incoming = forced;
			return;
		}

		boolean strong = wild.getTier().ordinal() >= Tier.EPIC.ordinal()
			|| wildLevel >= getCompanionLevel() + 2;
		if (strong && rng.nextDouble() < UNAVOIDABLE_CHANCE)
		{
			incoming = AttackStyle.UNAVOIDABLE;
			return;
		}
		if (wild.getTier().ordinal() >= Tier.RARE.ordinal() && rng.nextDouble() < DISABLE_CHANCE)
		{
			incoming = AttackStyle.DISABLE;
			return;
		}

		double switchChance = STYLE_SWITCH_BASE + 0.10 * wild.getTier().ordinal();
		if (rng.nextDouble() < switchChance)
		{
			AttackStyle next = baseStyle;
			while (next == baseStyle)
			{
				next = AttackStyle.values()[rng.nextInt(3)]; // MELEE / RANGED / MAGIC
			}
			baseStyle = next;
		}
		incoming = baseStyle;
	}

	private void resolveWildAttack()
	{
		Random rng = ThreadLocalRandom.current();
		AttackStyle style = incoming == null ? baseStyle : incoming;
		int myLevel = getCompanionLevel();
		int atk = atkOf(wild.getTier(), wildLevel);
		int def = defOf(companion.getTier(), myLevel) + gearDef(companion);
		int roll = damage(rng, atk, def, 1.0);

		switch (style)
		{
			case UNAVOIDABLE:
			{
				// skull hit — punches through prayer for partial damage
				int dmg = Math.max(1, (int) Math.round(roll * UNAVOIDABLE_FRACTION));
				hurtCompanion(dmg);
				logLine("It powers through your prayer for " + dmg + "!");
				break;
			}
			case DISABLE:
			{
				// Sotetseg-style smite: prayer knocked off + short lockout
				activePrayer = null;
				prayerLockTicks = prayerLockLen;
				smiteSeq++;
				logLine("Your prayers were smited!");
				break;
			}
			default:
			{
				Prayer prayer = activePrayer;
				if (prayer != null && prayer.deflects(style))
				{
					// DEFLECT: no damage, chip reflected, special energy earned
					myHitDmg = 0;
					myHitSeq++;
					deflectSeq++;
					specialEnergy = Math.min(SPECIAL_MAX, specialEnergy + SPECIAL_PER_DEFLECT);
					int chip = Math.max(1, (int) Math.round(roll * CHIP_FRACTION));
					logLine("Deflected the " + styleWord(style) + " attack!"
						+ (isSpecialReady() ? " SPECIAL READY!" : ""));
					hurtWild(chip);
				}
				else
				{
					int dmg = Math.max(1, roll);
					logLine("Wild " + wild.getNpcName() + "'s " + styleWord(style)
						+ " attack hits for " + dmg + "!");
					hurtCompanion(dmg);
				}
				break;
			}
		}
	}

	private static String styleWord(AttackStyle style)
	{
		return style.name().toLowerCase(Locale.ROOT);
	}

	private void companionStrike(double mult, boolean isSpecial)
	{
		Random rng = ThreadLocalRandom.current();
		int atk = atkOf(companion.getTier(), getCompanionLevel()) + gearAtk(companion);
		int def = defOf(wild.getTier(), wildLevel);
		int dmg = damage(rng, atk, def, mult);
		if (isSpecial)
		{
			logLine(companion.getNpcName() + " unleashes a SPECIAL for " + dmg + "!");
		}
		hurtWild(dmg);
	}

	private void hurtWild(int dmg)
	{
		wildHp = Math.max(0, wildHp - dmg);
		wildHitDmg = dmg;
		wildHitSeq++;
		if (wildHp == 0)
		{
			victory();
		}
	}

	private void hurtCompanion(int dmg)
	{
		myHp = Math.max(0, myHp - dmg);
		myHitDmg = dmg;
		myHitSeq++;
		if (myHp == 0)
		{
			defeat();
		}
	}

	// ------------------------------------------------------------------
	// consumed input actions (client thread)
	// ------------------------------------------------------------------

	private void togglePrayer(Prayer prayer)
	{
		if (prayerLockTicks > 0)
		{
			return; // smite-locked — wait it out, then re-click
		}
		if (activePrayer == prayer)
		{
			activePrayer = null; // flick off
		}
		else if (prayerPoints > 0)
		{
			activePrayer = prayer; // flick on / switch — one overhead at a time
		}
	}

	private void trySpecial()
	{
		if (!isSpecialReady())
		{
			return;
		}
		specialEnergy = 0;
		companionStrike(SPECIAL_MULT, true);
	}

	private void tryRun()
	{
		if (runLockTicks > 0)
		{
			return;
		}
		Random rng = ThreadLocalRandom.current();
		double fleeChance = 0.55
			+ 0.10 * (companion.getTier().ordinal() - wild.getTier().ordinal())
			+ 0.005 * (getCompanionLevel() - wildLevel);
		if (rng.nextDouble() < Math.max(0.15, Math.min(0.95, fleeChance)))
		{
			logLine("You got away safely!");
			state = State.FLED;
			resultTicks = RESULT_TICKS / 2;
			return;
		}
		runLockTicks = runLockLen;
		logLine("Couldn't escape!");
	}

	private void beginFight()
	{
		state = State.FIGHT;

		// resolve pace once per battle — mid-battle config flips wait
		pacePercent = Math.max(50, paceSupplier.get().getPercent());
		attackCycle = scaled(baseCycleTicks(wild.getTier()));
		telegraphTicks = Math.min(attackCycle - 1, Math.max(2, scaled(TELEGRAPH_TICKS)));
		companionCycle = Math.max(2, scaled(COMPANION_ATTACK_TICKS));
		prayerLockLen = Math.max(2, scaled(PRAYER_LOCK_TICKS));
		runLockLen = Math.max(3, scaled(RUN_LOCK_TICKS));
		prayerDrainEvery = Math.max(1, (pacePercent + 99) / 100); // ceil(pct/100)
		prayerDrainCounter = 0;

		nextAttackIn = attackCycle + 1; // one breath before the first telegraph
		companionAttackIn = companionCycle;
		baseStyle = AttackStyle.values()[ThreadLocalRandom.current().nextInt(3)];
		incoming = null;
		activePrayer = null;
		maxPrayerPoints = 20 + getCompanionLevel() / 2 + companion.getTier().ordinal() * 5;
		prayerPoints = maxPrayerPoints;
		prayerLockTicks = 0;
		specialEnergy = 0;
		runLockTicks = 0;
		logLine("The wild " + wild.getNpcName() + " squares up — watch its attacks!");
	}

	private int scaled(int baseTicks)
	{
		return Math.max(1, Math.round(baseTicks * pacePercent / 100f));
	}

	private void doDecline()
	{
		message("You slip away from the wild " + wild.getNpcName() + ".");
		endBattle();
	}

	// ------------------------------------------------------------------
	// internals
	// ------------------------------------------------------------------

	private void startEncounter(CreatureDef forced)
	{
		companion = CreatureRoster.byKey(store.getCompanionKey());
		if (companion == null)
		{
			return;
		}
		Random rng = ThreadLocalRandom.current();
		wild = forced != null ? forced : rollWild(rng);
		int myLevel = store.getLevel(companion);
		wildLevel = Math.max(1, Math.min(99, myLevel + rng.nextInt(7) - 3));

		myMaxHp = hpOf(companion.getTier(), myLevel) + gearDef(companion) / 2;
		myHp = myMaxHp;
		wildMaxHp = hpOf(wild.getTier(), wildLevel);
		wildHp = wildMaxHp;
		specialEnergy = 0;
		activePrayer = null;
		incoming = null;
		lastDrop = null;
		synchronized (log)
		{
			log.clear();
		}
		logVersion++;

		state = State.PROMPT;
		promptTicks = PROMPT_TIMEOUT_TICKS;
		message("A wild " + wild.getNpcName() + " (lvl " + wildLevel
			+ ") challenges your " + companion.getNpcName() + "!");
	}

	private CreatureDef rollWild(Random rng)
	{
		// commons often, legendaries rarely — reuse spawn weights
		int total = 0;
		for (CreatureDef d : CreatureRoster.ALL)
		{
			total += d.getTier().getSpawnWeight();
		}
		int roll = rng.nextInt(total);
		for (CreatureDef d : CreatureRoster.ALL)
		{
			roll -= d.getTier().getSpawnWeight();
			if (roll < 0)
			{
				return d;
			}
		}
		return CreatureRoster.ALL.get(0);
	}

	// ---- stat math, shared with the duel simulator (com.runehunter.party) ----

	/** Base game ticks between attacks for a tier (before pace scaling). */
	public static int baseCycleTicks(Tier tier)
	{
		return ATTACK_CYCLE[Math.min(ATTACK_CYCLE.length - 1, tier.ordinal())];
	}

	public static int hpOf(Tier tier, int level)
	{
		int base = 20 + tier.ordinal() * 12;
		return base + (int) (level * 1.6);
	}

	public static int atkOf(Tier tier, int level)
	{
		return 6 + tier.ordinal() * 4 + (int) (level * 0.55);
	}

	public static int defOf(Tier tier, int level)
	{
		return 3 + tier.ordinal() * 3 + (int) (level * 0.4);
	}

	private int gearAtk(CreatureDef d)
	{
		int sum = 0;
		for (GearItem g : store.getEquipped(d))
		{
			if (g != null)
			{
				sum += g.getAtk();
			}
		}
		return sum;
	}

	private int gearDef(CreatureDef d)
	{
		int sum = 0;
		for (GearItem g : store.getEquipped(d))
		{
			if (g != null)
			{
				sum += g.getDef();
			}
		}
		return sum;
	}

	public static int damage(Random rng, int atk, int def, double mult)
	{
		double raw = atk * (0.75 + rng.nextDouble() * 0.5) * mult - def * 0.45;
		return Math.max(1, (int) Math.round(raw));
	}

	private void victory()
	{
		state = State.VICTORY;
		resultTicks = RESULT_TICKS;

		int xp = 40 + wild.getTier().ordinal() * 30 + Math.max(0, (wildLevel - store.getLevel(companion)) * 8);
		int before = store.getLevel(companion);
		store.addXp(companion, xp);
		store.recordBattle(companion, true);
		int after = store.getLevel(companion);

		logLine("Victory! +" + xp + " xp for " + companion.getNpcName() + ".");
		message("Your " + companion.getNpcName() + " defeated the wild "
			+ wild.getNpcName() + "! (+" + xp + " xp)");
		if (after > before)
		{
			message("🎉 " + companion.getNpcName() + " advanced to level " + after + "!");
			logLine(companion.getNpcName() + " advanced to level " + after + "!");
		}

		Random rng = ThreadLocalRandom.current();
		if (rng.nextInt(100) < 30)
		{
			lastDrop = GearItem.randomDrop(rng);
			store.addGear(lastDrop);
			message("The wild " + wild.getNpcName() + " dropped: " + lastDrop.getDisplayName() + "!");
			logLine("It dropped " + lastDrop.getDisplayName() + "!");
		}

		// the beaten wild limps off nearby — bonus catch window
		spawnManager.spawnNear(wild, false);
		message("The weakened " + wild.getNpcName() + " collapses nearby — quick, throw an orb!");

		uiRefresh.run();
	}

	private void defeat()
	{
		state = State.DEFEAT;
		resultTicks = RESULT_TICKS;
		faintedUntil = System.currentTimeMillis() + FAINT_MILLIS;
		int xp = 10 + wild.getTier().ordinal() * 5;
		store.addXp(companion, xp);
		store.recordBattle(companion, false);
		logLine(companion.getNpcName() + " fainted... (+" + xp + " xp for the effort)");
		message("Your " + companion.getNpcName() + " fainted! It needs 3 minutes to recover.");
		uiRefresh.run();
	}

	private void endBattle()
	{
		state = State.IDLE;
		wild = null;
		incoming = null;
		activePrayer = null;
		cooldownTicks = POST_BATTLE_COOLDOWN_TICKS;
	}

	private void logLine(String line)
	{
		synchronized (log)
		{
			log.addLast(line);
			while (log.size() > 4)
			{
				log.removeFirst();
			}
		}
		logVersion++;
	}

	/** Chat output — ALWAYS marshalled onto the client thread. */
	private void message(String msg)
	{
		clientThread.invokeLater(() ->
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null));
	}
}
