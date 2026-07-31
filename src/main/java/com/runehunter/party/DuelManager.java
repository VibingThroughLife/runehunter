package com.runehunter.party;

import com.runehunter.RuneHunterConfig;
import com.runehunter.data.GearItem;
import com.runehunter.data.Tier;
import com.runehunter.game.BattleManager;
import com.runehunter.game.BattleSource;
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
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Player-vs-player duels over the RuneLite Party service, rendered through
 * BattleOverlay via the BattleSource interface.
 *
 * AUTHORITY: the CHALLENGER's client is the HOST and simulates the whole
 * fight — both companions' attack cycles, telegraphs, prayers, impacts —
 * using both companions' real stats+gear (exchanged in challenge/accept).
 * Players only ever send their prayer state and special/forfeit presses;
 * the host applies inputs on arrival and uses last-known state at each
 * impact (latency-tolerant), then broadcasts events with a full state-sync
 * block that the GUEST simply adopts and renders. A 10s silence from the
 * peer aborts the duel cleanly.
 *
 * Friendly stakes: winner/loser W/L records only — no xp, no drops, no
 * faint, no orb window. Forfeit (the RUN slot) counts as a loss.
 *
 * PRACTICE MODE: a loopback duel against a mirror of your own companion.
 * The full message path runs — the mirror "player" (member id -1) answers
 * with real RuneHunterDuel messages fed through the same handlers, flicking
 * a plausible prayer at each telegraph — only the websocket is skipped.
 *
 * Threading: PartyHub marshals every message onto the client thread before
 * calling in here; tick()/clientTick() run on the client thread; the
 * BattleSource input methods are AWT-safe (atomic request fields only).
 */
public class DuelManager implements BattleSource
{
	private static final long PEER_TIMEOUT_MS = 10_000L;
	private static final long CHALLENGE_TIMEOUT_MS = 30_000L;
	private static final int PROMPT_TIMEOUT_TICKS = 50;   // 30s
	private static final int RESULT_TICKS = 220;          // client ticks
	private static final int PING_EVERY_TICKS = 3;        // ~1.8s heartbeat
	private static final long PRACTICE_MEMBER_ID = -1L;

	private enum Role
	{
		NONE, HOST, GUEST
	}

	/** One attack lane: attacker's cycle toward a defender. */
	private static final class Lane
	{
		int cycle;
		int nextIn;
		BattleManager.AttackStyle base;
		volatile BattleManager.AttackStyle incoming;
	}

	/** One side's live combat state (host simulates both). */
	private static final class Fighter
	{
		CompanionSnapshot snap;
		volatile int hp;
		int maxHp;
		volatile int pp;
		int maxPp;
		volatile int lock;
		volatile int energy;
		volatile BattleManager.Prayer prayer;
		int drainCounter;
	}

	private final CollectionStore store;
	private final Supplier<RuneHunterConfig.BattlePace> paceSupplier;
	private final Consumer<PartyMemberMessage> sender;
	private final Consumer<String> chat;
	private final Runnable uiRefresh;

	// session
	private volatile BattleManager.State state = BattleManager.State.IDLE;
	private Role role = Role.NONE;
	private volatile long nonce;
	private volatile long peerId;
	private volatile String peerName = "";
	private boolean practice;
	private boolean recorded;
	private long lastPeerMsgMs;
	private int pingCountdown;
	private int promptTicks;
	private int resultTicks;
	private String verdictNote = "";

	// pending outgoing challenge (host side, before accept)
	private long pendingNonce;
	private long pendingPeerId;
	private String pendingPeerName;
	private long pendingSinceMs;

	// combat state, perspective-local: mine = my companion, theirs = opponent
	private final Fighter mine = new Fighter();
	private final Fighter theirs = new Fighter();
	private final Lane laneAtMe = new Lane();    // theirs attacking mine
	private final Lane laneAtThem = new Lane();  // mine attacking theirs (host only)
	private int pacePercent = 100;
	private volatile int telegraphTicks = BattleManager.TELEGRAPH_TICKS;
	private int prayerLockLen = 3;
	private int prayerDrainEvery = 1;

	// hit/flash events for the overlay
	private volatile int myHitDmg;
	private volatile int myHitSeq;
	private volatile int wildHitDmg;
	private volatile int wildHitSeq;
	private volatile boolean wildHitDeflect;
	private volatile int deflectSeq;
	private volatile int smiteSeq;

	// log
	private final Deque<String> log = new ArrayDeque<>();
	private volatile int logVersion;

	// AWT input requests
	private final AtomicReference<BattleManager.Prayer> prayerClick = new AtomicReference<>();
	private final AtomicBoolean specialRequested = new AtomicBoolean();
	private final AtomicBoolean forfeitRequested = new AtomicBoolean();
	private final AtomicBoolean acceptRequested = new AtomicBoolean();
	private final AtomicBoolean declineRequested = new AtomicBoolean();
	private final AtomicBoolean dismissRequested = new AtomicBoolean();

	public DuelManager(CollectionStore store, Supplier<RuneHunterConfig.BattlePace> paceSupplier,
		Consumer<PartyMemberMessage> sender, Consumer<String> chat, Runnable uiRefresh)
	{
		this.store = store;
		this.paceSupplier = paceSupplier;
		this.sender = sender;
		this.chat = chat;
		this.uiRefresh = uiRefresh;
	}

	public boolean isBusy()
	{
		return state != BattleManager.State.IDLE || pendingNonce != 0;
	}

	public String getPeerName()
	{
		return peerName;
	}

	// ------------------------------------------------------------------
	// BattleSource — snapshot getters
	// ------------------------------------------------------------------

	@Override
	public BattleManager.State getState()
	{
		return state;
	}

	@Override
	public boolean isDuel()
	{
		return true;
	}

	@Override
	public String getWildOwnerName()
	{
		return peerName;
	}

	@Override
	public String getPromptText()
	{
		return peerName + " challenges you to a DUEL!";
	}

	@Override
	public com.runehunter.data.CreatureDef getWild()
	{
		CompanionSnapshot s = theirs.snap;
		return s == null ? null : s.getDef();
	}

	@Override
	public int getWildLevel()
	{
		CompanionSnapshot s = theirs.snap;
		return s == null ? 1 : s.getLevel();
	}

	@Override
	public com.runehunter.data.CreatureDef getCompanion()
	{
		CompanionSnapshot s = mine.snap;
		return s == null ? null : s.getDef();
	}

	@Override
	public int getCompanionLevel()
	{
		CompanionSnapshot s = mine.snap;
		return s == null ? 1 : s.getLevel();
	}

	@Override
	public int getMyHp()
	{
		return mine.hp;
	}

	@Override
	public int getMyMaxHp()
	{
		return mine.maxHp;
	}

	@Override
	public int getWildHp()
	{
		return theirs.hp;
	}

	@Override
	public int getWildMaxHp()
	{
		return theirs.maxHp;
	}

	@Override
	public BattleManager.Prayer getActivePrayer()
	{
		return mine.prayer;
	}

	@Override
	public int getPrayerPoints()
	{
		return mine.pp;
	}

	@Override
	public int getMaxPrayerPoints()
	{
		return mine.maxPp;
	}

	@Override
	public int getPrayerLockTicks()
	{
		return mine.lock;
	}

	@Override
	public BattleManager.AttackStyle getIncomingStyle()
	{
		return laneAtMe.incoming;
	}

	@Override
	public int getImpactTicks()
	{
		return laneAtMe.incoming == null ? -1 : laneAtMe.nextIn;
	}

	@Override
	public int getTelegraphTicks()
	{
		return telegraphTicks;
	}

	@Override
	public int getSpecialEnergy()
	{
		return mine.energy;
	}

	@Override
	public boolean isSpecialReady()
	{
		return mine.energy >= BattleManager.SPECIAL_MAX;
	}

	@Override
	public int getRunLockTicks()
	{
		return 0; // forfeit is always available
	}

	@Override
	public int getMyHitSeq()
	{
		return myHitSeq;
	}

	@Override
	public int getMyHitDmg()
	{
		return myHitDmg;
	}

	@Override
	public int getWildHitSeq()
	{
		return wildHitSeq;
	}

	@Override
	public int getWildHitDmg()
	{
		return wildHitDmg;
	}

	@Override
	public boolean isWildHitDeflect()
	{
		return wildHitDeflect;
	}

	@Override
	public int getDeflectSeq()
	{
		return deflectSeq;
	}

	@Override
	public int getSmiteSeq()
	{
		return smiteSeq;
	}

	@Override
	public int getLogVersion()
	{
		return logVersion;
	}

	@Override
	public List<String> getLog()
	{
		synchronized (log)
		{
			return new ArrayList<>(log);
		}
	}

	@Override
	public GearItem getLastDrop()
	{
		return null; // friendly duels drop nothing
	}

	// ------------------------------------------------------------------
	// BattleSource — AWT-safe inputs
	// ------------------------------------------------------------------

	@Override
	public void accept()
	{
		acceptRequested.set(true);
	}

	@Override
	public void decline()
	{
		declineRequested.set(true);
	}

	@Override
	public void clickPrayer(BattleManager.Prayer prayer)
	{
		prayerClick.set(prayer);
	}

	@Override
	public void clickSpecial()
	{
		specialRequested.set(true);
	}

	@Override
	public void clickRun()
	{
		forfeitRequested.set(true); // the RUN slot forfeits in a duel
	}

	@Override
	public void dismiss()
	{
		dismissRequested.set(true);
	}

	// ------------------------------------------------------------------
	// session control (client thread — called by PartyHub)
	// ------------------------------------------------------------------

	/** Challenge a party member. Returns false when unable. */
	public boolean challenge(long memberId, String memberName, String myName)
	{
		if (isBusy() || store.getCompanionKey() == null)
		{
			return false;
		}
		CompanionSnapshot snap = mySnapshot(myName);
		if (snap == null)
		{
			return false;
		}
		pendingNonce = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
		pendingPeerId = memberId;
		pendingPeerName = memberName;
		pendingSinceMs = System.currentTimeMillis();
		practice = false;

		RuneHunterDuel msg = control("challenge", pendingNonce, memberId, snap, myName);
		sender.accept(msg);
		chat.accept("Duel challenge sent to " + memberName + "...");
		return true;
	}

	/** Practice: loopback duel vs a mirror of your own companion. */
	public boolean practiceDuel(String myName)
	{
		if (isBusy() || store.getCompanionKey() == null)
		{
			return false;
		}
		CompanionSnapshot snap = mySnapshot(myName);
		if (snap == null)
		{
			return false;
		}
		practice = true;
		pendingNonce = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
		pendingPeerId = PRACTICE_MEMBER_ID;
		pendingPeerName = "Mirror " + snap.getDef().getNpcName();
		pendingSinceMs = System.currentTimeMillis();

		// the mirror answers through the full message path immediately
		RuneHunterDuel accept = control("accept", pendingNonce, 0, snap, pendingPeerName);
		accept.setMemberId(PRACTICE_MEMBER_ID);
		handleDuel(accept, PRACTICE_MEMBER_ID, myName);
		chat.accept("Practice duel started — flick those prayers!");
		return true;
	}

	private CompanionSnapshot mySnapshot(String myName)
	{
		com.runehunter.data.CreatureDef def = com.runehunter.data.CreatureRoster.byKey(store.getCompanionKey());
		return def == null ? null : CompanionSnapshot.of(store, def, myName);
	}

	private RuneHunterDuel control(String kind, long n, long target, CompanionSnapshot snap, String name)
	{
		RuneHunterDuel msg = new RuneHunterDuel();
		msg.setKind(kind);
		msg.setNonce(n);
		msg.setTargetId(target);
		msg.setPlayerName(name);
		if (snap != null)
		{
			msg.setCreatureKey(snap.getDef().key());
			msg.setLevel(snap.getLevel());
			msg.setXp(snap.getXp());
			msg.setGearCsv(snap.gearCsv());
		}
		return msg;
	}

	// ------------------------------------------------------------------
	// message handling (client thread — PartyHub marshals)
	// ------------------------------------------------------------------

	public void handleDuel(RuneHunterDuel msg, long fromId, String myName)
	{
		String kind = msg.getKind() == null ? "" : msg.getKind();
		lastPeerMsgMs = System.currentTimeMillis();

		switch (kind)
		{
			case "challenge":
			{
				if (isBusy() || store.getCompanionKey() == null)
				{
					// busy — decline so the challenger isn't left hanging
					sender.accept(control("decline", msg.getNonce(), fromId, null, ""));
					return;
				}
				CompanionSnapshot their = CompanionSnapshot.fromWire(
					msg.getPlayerName(), msg.getCreatureKey(), msg.getLevel(), msg.getXp(), msg.getGearCsv());
				CompanionSnapshot my = mySnapshot(myName);
				if (their == null || my == null)
				{
					return;
				}
				role = Role.GUEST;
				nonce = msg.getNonce();
				peerId = fromId;
				peerName = msg.getPlayerName() == null ? "?" : msg.getPlayerName();
				practice = false;
				mine.snap = my;
				theirs.snap = their;
				state = BattleManager.State.PROMPT;
				promptTicks = PROMPT_TIMEOUT_TICKS;
				chat.accept(peerName + " challenges you to a duel! (" + their.describe() + ")");
				break;
			}

			case "accept":
			{
				if (pendingNonce == 0 || msg.getNonce() != pendingNonce)
				{
					return;
				}
				CompanionSnapshot their = CompanionSnapshot.fromWire(
					msg.getPlayerName(), msg.getCreatureKey(), msg.getLevel(), msg.getXp(), msg.getGearCsv());
				CompanionSnapshot my = mySnapshot(myName);
				if (their == null || my == null)
				{
					clearPending();
					return;
				}
				role = Role.HOST;
				nonce = pendingNonce;
				peerId = practice ? PRACTICE_MEMBER_ID : fromId;
				peerName = msg.getPlayerName() == null ? pendingPeerName : msg.getPlayerName();
				clearPending();
				mine.snap = my;
				theirs.snap = their;
				beginDuel();
				break;
			}

			case "decline":
				if (pendingNonce != 0 && msg.getNonce() == pendingNonce)
				{
					chat.accept(pendingPeerName + " declined the duel.");
					clearPending();
				}
				break;

			case "input":
				if (role == Role.HOST && state == BattleManager.State.FIGHT && msg.getNonce() == nonce)
				{
					applyGuestInput(msg);
				}
				break;

			case "forfeit":
				if (state == BattleManager.State.FIGHT && msg.getNonce() == nonce)
				{
					logLine(peerName + " forfeits the duel!");
					finishDuel(true, "forfeit");
				}
				break;

			case "abort":
				if (msg.getNonce() == nonce && state != BattleManager.State.IDLE)
				{
					abortDuel("The duel was aborted.");
				}
				break;

			case "ping":
			default:
				break; // heartbeat only refreshes lastPeerMsgMs (above)
		}
	}

	public void handleDuelEvent(RuneHunterDuelEvent evt)
	{
		if (role != Role.GUEST || evt.getNonce() != nonce)
		{
			return;
		}
		lastPeerMsgMs = System.currentTimeMillis();

		// adopt the sync block: host-perspective guest* fields are MY side
		mine.hp = evt.getGuestHp();
		theirs.hp = evt.getHostHp();
		mine.pp = evt.getGuestPp();
		mine.lock = evt.getGuestLock();
		mine.energy = evt.getGuestEnergy();
		if (mine.pp <= 0)
		{
			mine.prayer = null;
		}

		String type = evt.getType() == null ? "" : evt.getType();
		switch (type)
		{
			case "telegraph":
				if ("guest".equals(evt.getSide()))
				{
					laneAtMe.incoming = styleOf(evt.getStyle());
					laneAtMe.nextIn = Math.max(1, evt.getImpactIn());
					telegraphTicks = Math.max(2, evt.getImpactIn());
				}
				break;

			case "impact":
				if ("guest".equals(evt.getSide()))
				{
					// my companion was attacked
					laneAtMe.incoming = null;
					myHitDmg = evt.isDeflected() ? 0 : evt.getValue();
					myHitSeq++;
					if (evt.isDeflected())
					{
						deflectSeq++;
						logLine("Deflected!");
					}
					else if (evt.getValue() > 0)
					{
						logLine(peerName + "'s " + theirsName() + " hits for " + evt.getValue() + "!");
					}
				}
				else
				{
					// their companion was attacked (by mine)
					wildHitDmg = evt.isDeflected() ? 0 : evt.getValue();
					wildHitDeflect = evt.isDeflected();
					wildHitSeq++;
					if (evt.isDeflected())
					{
						logLine(peerName + " deflects your " + mineName() + "!");
					}
				}
				break;

			case "smite":
				if ("guest".equals(evt.getSide()))
				{
					laneAtMe.incoming = null;
					mine.prayer = null;
					smiteSeq++;
					logLine("Your prayers were smited!");
				}
				break;

			case "special":
				logLine(evt.getText() == null ? "SPECIAL!" : evt.getText());
				break;

			case "log":
				if (evt.getText() != null)
				{
					logLine(evt.getText());
				}
				break;

			case "result":
				resolveResult(evt.getWinner());
				break;

			default:
				break;
		}
	}

	private void resolveResult(String winner)
	{
		if ("abort".equals(winner))
		{
			abortDuel("The duel was aborted.");
			return;
		}
		boolean iWon = role == Role.HOST ? "host".equals(winner) : "guest".equals(winner);
		finishDuel(iWon, null);
	}

	// ------------------------------------------------------------------
	// ticking (client thread)
	// ------------------------------------------------------------------

	/** Game tick (0.6s). */
	public void tick()
	{
		// stale outgoing challenge
		if (pendingNonce != 0 && System.currentTimeMillis() - pendingSinceMs > CHALLENGE_TIMEOUT_MS)
		{
			chat.accept(pendingPeerName + " didn't answer the duel challenge.");
			clearPending();
		}

		if (state == BattleManager.State.PROMPT && --promptTicks <= 0)
		{
			doDecline();
			return;
		}
		if (state != BattleManager.State.FIGHT)
		{
			return;
		}

		// heartbeat + peer timeout (practice mirror never times out)
		if (!practice)
		{
			if (--pingCountdown <= 0)
			{
				pingCountdown = PING_EVERY_TICKS;
				RuneHunterDuel ping = control("ping", nonce, peerId, null, "");
				sender.accept(ping);
			}
			if (System.currentTimeMillis() - lastPeerMsgMs > PEER_TIMEOUT_MS)
			{
				sender.accept(control("abort", nonce, peerId, null, ""));
				abortDuel("Connection lost — duel abandoned.");
				return;
			}
		}

		if (role == Role.HOST)
		{
			hostTick();
		}
		else
		{
			// guest: count the visible telegraph down locally between events
			if (laneAtMe.incoming != null && laneAtMe.nextIn > 0)
			{
				laneAtMe.nextIn--;
			}
		}
	}

	/** Client tick (~20ms): consume inputs fast; result linger. */
	public void clientTick()
	{
		BattleManager.Prayer prayer = prayerClick.getAndSet(null);
		boolean special = specialRequested.getAndSet(false);
		boolean forfeit = forfeitRequested.getAndSet(false);
		boolean acceptReq = acceptRequested.getAndSet(false);
		boolean declineReq = declineRequested.getAndSet(false);
		boolean dismissReq = dismissRequested.getAndSet(false);

		switch (state)
		{
			case PROMPT:
				if (acceptReq)
				{
					doAccept();
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
				if (forfeit && state == BattleManager.State.FIGHT)
				{
					doForfeit();
				}
				break;

			case VICTORY:
			case DEFEAT:
			case FLED:
				if (dismissReq || --resultTicks <= 0)
				{
					endDuel();
				}
				break;

			default:
				break;
		}
	}

	private void doAccept()
	{
		if (role != Role.GUEST)
		{
			return;
		}
		RuneHunterDuel accept = control("accept", nonce, peerId, mine.snap, mine.snap.getPlayerName());
		sender.accept(accept);
		beginDuel();
	}

	private void doDecline()
	{
		if (role == Role.GUEST)
		{
			sender.accept(control("decline", nonce, peerId, null, ""));
		}
		endDuel();
	}

	private void doForfeit()
	{
		if (!practice)
		{
			sender.accept(control("forfeit", nonce, peerId, null, ""));
		}
		logLine("You forfeit the duel.");
		finishDuel(false, "forfeit");
	}

	// ------------------------------------------------------------------
	// duel lifecycle
	// ------------------------------------------------------------------

	private void beginDuel()
	{
		state = BattleManager.State.FIGHT;
		recorded = false;
		verdictNote = "";
		lastPeerMsgMs = System.currentTimeMillis();
		pingCountdown = PING_EVERY_TICKS;
		synchronized (log)
		{
			log.clear();
		}
		logVersion++;

		pacePercent = Math.max(50, paceSupplier.get().getPercent());
		telegraphTicks = Math.max(2, Math.round(BattleManager.TELEGRAPH_TICKS * pacePercent / 100f));
		prayerLockLen = Math.max(2, Math.round(3 * pacePercent / 100f));
		prayerDrainEvery = Math.max(1, (pacePercent + 99) / 100);

		initFighter(mine);
		initFighter(theirs);
		initLane(laneAtMe, theirs.snap);
		initLane(laneAtThem, mine.snap);

		myHitSeq = 0;
		wildHitSeq = 0;
		deflectSeq = 0;
		smiteSeq = 0;
		wildHitDeflect = false;

		logLine("DUEL: " + mineName() + " vs " + peerName + "'s " + theirsName() + "!");
		chat.accept("Duel started against " + peerName + "!");
		uiRefresh.run();
	}

	private void initFighter(Fighter f)
	{
		Tier tier = f.snap.getDef().getTier();
		f.maxHp = BattleManager.hpOf(tier, f.snap.getLevel()) + f.snap.gearDef() / 2;
		f.hp = f.maxHp;
		f.maxPp = 20 + f.snap.getLevel() / 2 + tier.ordinal() * 5;
		f.pp = f.maxPp;
		f.lock = 0;
		f.energy = 0;
		f.prayer = null;
		f.drainCounter = 0;
	}

	private void initLane(Lane lane, CompanionSnapshot attacker)
	{
		lane.cycle = Math.max(2, Math.round(
			BattleManager.baseCycleTicks(attacker.getDef().getTier()) * pacePercent / 100f));
		lane.nextIn = lane.cycle + 2; // opening breath
		lane.base = BattleManager.AttackStyle.values()[ThreadLocalRandom.current().nextInt(3)];
		lane.incoming = null;
	}

	private void finishDuel(boolean iWon, String note)
	{
		if (state != BattleManager.State.FIGHT)
		{
			return;
		}
		state = iWon ? BattleManager.State.VICTORY : BattleManager.State.DEFEAT;
		resultTicks = RESULT_TICKS;
		verdictNote = note == null ? "" : note;
		if (!recorded && !practice && mine.snap != null)
		{
			recorded = true;
			store.recordBattle(mine.snap.getDef(), iWon); // W/L only — no faint, no xp
		}
		logLine(iWon ? "You win the duel against " + peerName + "!" : peerName + " wins the duel.");
		chat.accept(iWon ? "You won the duel against " + peerName + "!" : "You lost the duel against " + peerName + ".");
		uiRefresh.run();
	}

	private void abortDuel(String reason)
	{
		state = BattleManager.State.FLED; // renders as a neutral "duel ended"
		resultTicks = RESULT_TICKS / 2;
		verdictNote = reason;
		logLine(reason);
		chat.accept(reason);
	}

	private void endDuel()
	{
		state = BattleManager.State.IDLE;
		role = Role.NONE;
		nonce = 0;
		peerId = 0;
		peerName = "";
		practice = false;
		laneAtMe.incoming = null;
		laneAtThem.incoming = null;
	}

	private void clearPending()
	{
		pendingNonce = 0;
		pendingPeerId = 0;
		pendingPeerName = null;
	}

	// ------------------------------------------------------------------
	// HOST simulation
	// ------------------------------------------------------------------

	private void hostTick()
	{
		drainPrayer(mine);
		drainPrayer(theirs);
		if (mine.lock > 0)
		{
			mine.lock--;
		}
		if (theirs.lock > 0)
		{
			theirs.lock--;
		}

		// lane: opponent's companion attacking me (I defend)
		stepLane(laneAtMe, theirs, mine, true);
		if (state != BattleManager.State.FIGHT)
		{
			return;
		}
		// lane: my companion attacking them (guest defends)
		stepLane(laneAtThem, mine, theirs, false);
	}

	private void drainPrayer(Fighter f)
	{
		if (f.prayer != null && ++f.drainCounter >= prayerDrainEvery)
		{
			f.drainCounter = 0;
			f.pp--;
			if (f.pp <= 0)
			{
				f.pp = 0;
				f.prayer = null;
				if (f == mine)
				{
					logLine("Out of prayer points!");
				}
			}
		}
	}

	/** Advance one attack lane; attackerIsTheirs = true when I'm defending. */
	private void stepLane(Lane lane, Fighter attacker, Fighter defender, boolean iDefend)
	{
		lane.nextIn--;
		if (lane.nextIn == telegraphTicks)
		{
			rollLaneTelegraph(lane, attacker, defender);
			if (!iDefend)
			{
				sendTelegraphEvent(lane);
				if (practice)
				{
					practiceBotReact(lane);
				}
			}
		}
		if (lane.nextIn <= 0)
		{
			resolveLaneImpact(lane, attacker, defender, iDefend);
			lane.incoming = null;
			lane.nextIn = lane.cycle;
		}
	}

	private void rollLaneTelegraph(Lane lane, Fighter attacker, Fighter defender)
	{
		Random rng = ThreadLocalRandom.current();
		Tier tier = attacker.snap.getDef().getTier();
		boolean strong = tier.ordinal() >= Tier.EPIC.ordinal()
			|| attacker.snap.getLevel() >= defender.snap.getLevel() + 2;
		if (strong && rng.nextDouble() < 0.20)
		{
			lane.incoming = BattleManager.AttackStyle.UNAVOIDABLE;
			return;
		}
		if (tier.ordinal() >= Tier.RARE.ordinal() && rng.nextDouble() < 0.15)
		{
			lane.incoming = BattleManager.AttackStyle.DISABLE;
			return;
		}
		if (rng.nextDouble() < 0.30 + 0.10 * tier.ordinal())
		{
			BattleManager.AttackStyle next = lane.base;
			while (next == lane.base)
			{
				next = BattleManager.AttackStyle.values()[rng.nextInt(3)];
			}
			lane.base = next;
		}
		lane.incoming = lane.base;
	}

	private void resolveLaneImpact(Lane lane, Fighter attacker, Fighter defender, boolean iDefend)
	{
		Random rng = ThreadLocalRandom.current();
		BattleManager.AttackStyle style = lane.incoming == null ? lane.base : lane.incoming;
		int atk = BattleManager.atkOf(attacker.snap.getDef().getTier(), attacker.snap.getLevel())
			+ attacker.snap.gearAtk();
		int def = BattleManager.defOf(defender.snap.getDef().getTier(), defender.snap.getLevel())
			+ defender.snap.gearDef();
		int roll = BattleManager.damage(rng, atk, def, 1.0);

		if (style == BattleManager.AttackStyle.DISABLE)
		{
			defender.prayer = null;
			defender.lock = prayerLockLen;
			if (iDefend)
			{
				smiteSeq++;
				logLine("Your prayers were smited!");
			}
			else
			{
				logLine("You smite " + peerName + "'s prayers!");
			}
			sendEvent(event("smite", iDefend ? "host" : "guest", 0, false));
			return;
		}

		if (style == BattleManager.AttackStyle.UNAVOIDABLE)
		{
			int dmg = Math.max(1, (int) Math.round(roll * BattleManager.UNAVOIDABLE_FRACTION));
			if (iDefend)
			{
				logLine("It powers through your prayer for " + dmg + "!");
			}
			applyDamage(defender, dmg, iDefend);
			return;
		}

		BattleManager.Prayer prayer = defender.prayer;
		if (prayer != null && prayer.deflects(style))
		{
			defender.energy = Math.min(BattleManager.SPECIAL_MAX, defender.energy + BattleManager.SPECIAL_PER_DEFLECT);
			int chip = Math.max(1, (int) Math.round(roll * BattleManager.CHIP_FRACTION));
			if (iDefend)
			{
				// my deflect: blue 0-splat on me, chip reflected at them
				myHitDmg = 0;
				myHitSeq++;
				deflectSeq++;
				logLine("Deflected the " + style.name().toLowerCase(Locale.ROOT) + " attack!"
					+ (isSpecialReady() ? " SPECIAL READY!" : ""));
			}
			else
			{
				// the guest deflected my companion: blue 0 on their side
				wildHitDmg = 0;
				wildHitDeflect = true;
				wildHitSeq++;
				logLine(peerName + " deflects your " + mineName() + "!");
			}
			sendEvent(impactEvent(iDefend, 0, true));
			applyDamage(attacker, chip, !iDefend);
		}
		else
		{
			int dmg = Math.max(1, roll);
			if (iDefend)
			{
				logLine(peerName + "'s " + theirsName() + " hits for " + dmg + "!");
			}
			applyDamage(defender, dmg, iDefend);
		}
	}

	/** Damage a fighter, emit splat + event, check for the end. */
	private void applyDamage(Fighter victim, int dmg, boolean victimIsMine)
	{
		victim.hp = Math.max(0, victim.hp - dmg);
		if (victimIsMine)
		{
			myHitDmg = dmg;
			myHitSeq++;
		}
		else
		{
			wildHitDmg = dmg;
			wildHitDeflect = false;
			wildHitSeq++;
		}
		sendEvent(impactEvent(victimIsMine, dmg, false));

		if (victim.hp == 0)
		{
			boolean iWon = !victimIsMine;
			RuneHunterDuelEvent result = event("result", null, 0, false);
			result.setWinner(iWon ? "host" : "guest");
			sendEvent(result);
			finishDuel(iWon, null);
		}
	}

	private void applyGuestInput(RuneHunterDuel msg)
	{
		// last-known prayer state; applied at the next impact check
		if (theirs.lock <= 0)
		{
			String p = msg.getPrayer();
			if (p == null || p.isEmpty())
			{
				theirs.prayer = null;
			}
			else if (theirs.pp > 0)
			{
				try
				{
					theirs.prayer = BattleManager.Prayer.valueOf(p);
				}
				catch (IllegalArgumentException ignored)
				{
				}
			}
		}
		if (msg.isSpecial() && theirs.energy >= BattleManager.SPECIAL_MAX)
		{
			theirs.energy = 0;
			Random rng = ThreadLocalRandom.current();
			int atk = BattleManager.atkOf(theirs.snap.getDef().getTier(), theirs.snap.getLevel())
				+ theirs.snap.gearAtk();
			int def = BattleManager.defOf(mine.snap.getDef().getTier(), mine.snap.getLevel())
				+ mine.snap.gearDef();
			int dmg = BattleManager.damage(rng, atk, def, BattleManager.SPECIAL_MULT);
			logLine(peerName + "'s " + theirsName() + " unleashes a SPECIAL for " + dmg + "!");
			RuneHunterDuelEvent special = event("special", null, dmg, false);
			special.setText(theirsName() + " unleashes a SPECIAL for " + dmg + "!");
			sendEvent(special);
			applyDamage(mine, dmg, true);
		}
	}

	private void togglePrayer(BattleManager.Prayer prayer)
	{
		if (mine.lock > 0)
		{
			return;
		}
		if (mine.prayer == prayer)
		{
			mine.prayer = null;
		}
		else if (mine.pp > 0)
		{
			mine.prayer = prayer;
		}
		if (role == Role.GUEST)
		{
			sendGuestInput(false);
		}
	}

	private void trySpecial()
	{
		if (!isSpecialReady())
		{
			return;
		}
		if (role == Role.GUEST)
		{
			sendGuestInput(true); // the host validates and resolves it
			return;
		}
		// host: resolve immediately against the guest's companion
		mine.energy = 0;
		Random rng = ThreadLocalRandom.current();
		int atk = BattleManager.atkOf(mine.snap.getDef().getTier(), mine.snap.getLevel())
			+ mine.snap.gearAtk();
		int def = BattleManager.defOf(theirs.snap.getDef().getTier(), theirs.snap.getLevel())
			+ theirs.snap.gearDef();
		int dmg = BattleManager.damage(rng, atk, def, BattleManager.SPECIAL_MULT);
		logLine(mineName() + " unleashes a SPECIAL for " + dmg + "!");
		RuneHunterDuelEvent special = event("special", null, dmg, false);
		special.setText(mineName() + " unleashes a SPECIAL for " + dmg + "!");
		sendEvent(special);
		applyDamage(theirs, dmg, false);
	}

	private void sendGuestInput(boolean special)
	{
		RuneHunterDuel input = control("input", nonce, peerId, null, "");
		input.setPrayer(mine.prayer == null ? "" : mine.prayer.name());
		input.setSpecial(special);
		sender.accept(input);
	}

	// ------------------------------------------------------------------
	// host → guest events
	// ------------------------------------------------------------------

	private RuneHunterDuelEvent event(String type, String side, int value, boolean deflected)
	{
		RuneHunterDuelEvent evt = new RuneHunterDuelEvent();
		evt.setType(type);
		evt.setNonce(nonce);
		evt.setTargetId(peerId);
		evt.setSide(side);
		evt.setValue(value);
		evt.setDeflected(deflected);
		// sync block — host perspective: mine = host, theirs = guest
		evt.setHostHp(mine.hp);
		evt.setGuestHp(theirs.hp);
		evt.setGuestPp(theirs.pp);
		evt.setGuestLock(theirs.lock);
		evt.setGuestEnergy(theirs.energy);
		return evt;
	}

	/** victimIsMine is host-perspective; the guest's companion is "guest". */
	private RuneHunterDuelEvent impactEvent(boolean victimIsMine, int dmg, boolean deflected)
	{
		return event("impact", victimIsMine ? "host" : "guest", dmg, deflected);
	}

	private void sendTelegraphEvent(Lane lane)
	{
		RuneHunterDuelEvent evt = event("telegraph", "guest", 0, false);
		evt.setStyle(lane.incoming == null ? "" : lane.incoming.name());
		evt.setImpactIn(telegraphTicks);
		sendEvent(evt);
	}

	private void sendEvent(RuneHunterDuelEvent evt)
	{
		if (role != Role.HOST || practice)
		{
			return; // guests never emit events; the mirror needs none
		}
		sender.accept(evt);
	}

	// ------------------------------------------------------------------
	// practice mirror bot — answers through the real message path
	// ------------------------------------------------------------------

	private void practiceBotReact(Lane lane)
	{
		Random rng = ThreadLocalRandom.current();
		RuneHunterDuel input = new RuneHunterDuel();
		input.setKind("input");
		input.setNonce(nonce);
		input.setTargetId(0);
		input.setMemberId(PRACTICE_MEMBER_ID);

		BattleManager.AttackStyle style = lane.incoming;
		double roll = rng.nextDouble();
		if (style != null && style != BattleManager.AttackStyle.DISABLE
			&& style != BattleManager.AttackStyle.UNAVOIDABLE && roll < 0.55)
		{
			input.setPrayer(style.name()); // correct flick
		}
		else if (roll < 0.75)
		{
			input.setPrayer(BattleManager.Prayer.values()[rng.nextInt(3)].name()); // guess
		}
		else
		{
			input.setPrayer(""); // asleep at the wheel
		}
		input.setSpecial(theirs.energy >= BattleManager.SPECIAL_MAX && rng.nextInt(2) == 0);
		handleDuel(input, PRACTICE_MEMBER_ID, mine.snap.getPlayerName());
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private String mineName()
	{
		return mine.snap == null ? "?" : mine.snap.getDef().getNpcName();
	}

	private String theirsName()
	{
		return theirs.snap == null ? "?" : theirs.snap.getDef().getNpcName();
	}

	private static BattleManager.AttackStyle styleOf(String name)
	{
		if (name == null || name.isEmpty())
		{
			return null;
		}
		try
		{
			return BattleManager.AttackStyle.valueOf(name);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
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
}
