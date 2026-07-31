package com.runehunter.party;

import com.runehunter.data.CreatureDef;
import com.runehunter.storage.CollectionStore;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * OSRS-style two-screen trade over the Party service.
 *
 * Flow: invite → both sides offer (any offer change resets all accepts —
 * classic anti-scam) → both ACCEPT (screen 1) → both CONFIRM (screen 2)
 * → apply. Each client applies its own half through TradeLedger only after
 * BOTH confirm2 messages exist, guarded by a per-session nonce recorded in
 * appliedNonces so a duplicate/replayed confirm can never double-apply.
 * 90s of inactivity cancels a stale session on both ends independently.
 *
 * One-sided offers are allowed (gifts). The shiny rule lives in
 * TradeLedger: only spare plain copies are tradeable; shinies never move.
 *
 * Threading: incoming messages arrive via PartyHub already on the client
 * thread; UI calls (from Swing) go through the clientRun marshal. The Ui
 * bridge marshals back onto the EDT itself.
 */
public class TradeManager
{
	public enum Stage
	{
		IDLE, NEGOTIATE, CONFIRM, DONE
	}

	/** Implemented by TradeWindow; every call must self-marshal to the EDT. */
	public interface Ui
	{
		void open();

		void refresh();
	}

	private static final long TIMEOUT_MS = 90_000L;

	private final CollectionStore store;
	private final TradeLedger ledger;
	private final Consumer<PartyMemberMessage> sender;
	private final Consumer<String> chat;
	private final Consumer<Runnable> clientRun;
	private final Runnable uiRefresh;

	private Ui ui;

	// session (mutated on the client thread; volatile for EDT display reads)
	private volatile Stage stage = Stage.IDLE;
	private volatile long nonce;
	private volatile long peerId;
	private volatile String peerName = "";
	private volatile boolean peerJoined;
	private volatile CompanionSnapshot myOffer;
	private volatile CompanionSnapshot theirOffer;
	private volatile boolean myAccept;
	private volatile boolean theirAccept;
	private volatile boolean myConfirm;
	private volatile boolean theirConfirm;
	private volatile String banner = "";
	private String myName = "";
	private long lastActivityMs;
	private final Set<Long> appliedNonces = new HashSet<>();

	public TradeManager(CollectionStore store, TradeLedger ledger, Consumer<PartyMemberMessage> sender,
		Consumer<String> chat, Consumer<Runnable> clientRun, Runnable uiRefresh)
	{
		this.store = store;
		this.ledger = ledger;
		this.sender = sender;
		this.chat = chat;
		this.clientRun = clientRun;
		this.uiRefresh = uiRefresh;
	}

	public void setUi(Ui ui)
	{
		this.ui = ui;
	}

	public TradeLedger getLedger()
	{
		return ledger;
	}

	// ---- display getters (safe from the EDT) ----

	public Stage getStage()
	{
		return stage;
	}

	public String getPeerName()
	{
		return peerName;
	}

	public boolean isPeerJoined()
	{
		return peerJoined;
	}

	public CompanionSnapshot getMyOffer()
	{
		return myOffer;
	}

	public CompanionSnapshot getTheirOffer()
	{
		return theirOffer;
	}

	public boolean isMyAccept()
	{
		return myAccept;
	}

	public boolean isTheirAccept()
	{
		return theirAccept;
	}

	public boolean isMyConfirm()
	{
		return myConfirm;
	}

	public boolean isTheirConfirm()
	{
		return theirConfirm;
	}

	public String getBanner()
	{
		return banner;
	}

	public boolean isBusy()
	{
		return stage != Stage.IDLE && stage != Stage.DONE;
	}

	// ---- UI-side actions (marshalled onto the client thread) ----

	/** Start a trade with a party member. */
	public void startTrade(long memberId, String memberName, String localName)
	{
		clientRun.accept(() ->
		{
			if (isBusy())
			{
				chat.accept("Finish the current trade first.");
				return;
			}
			resetSession();
			stage = Stage.NEGOTIATE;
			nonce = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
			peerId = memberId;
			peerName = memberName;
			peerJoined = false;
			myName = localName;
			banner = "Waiting for " + memberName + " to answer...";
			touch();
			send("invite", null);
			chat.accept("Trade offer sent to " + memberName + ".");
			openUi();
		});
	}

	/** Set (or clear, with null) my offered creature. Resets all accepts. */
	public void setMyOffer(CreatureDef def)
	{
		clientRun.accept(() ->
		{
			if (stage != Stage.NEGOTIATE && stage != Stage.CONFIRM)
			{
				return;
			}
			if (def != null)
			{
				String block = ledger.tradeBlockReason(def);
				if (block != null)
				{
					banner = block;
					refreshUi();
					return;
				}
				myOffer = CompanionSnapshot.of(store, def, myName);
			}
			else
			{
				myOffer = null;
			}
			resetAgreements();
			stage = Stage.NEGOTIATE;
			banner = "Offer changed — both sides must accept again.";
			touch();
			send("offer", myOffer);
			refreshUi();
		});
	}

	/** Screen 1: accept the current pair of offers. */
	public void acceptOffer()
	{
		clientRun.accept(() ->
		{
			if (stage != Stage.NEGOTIATE || myAccept)
			{
				return;
			}
			if (myOffer == null && theirOffer == null)
			{
				banner = "Nothing offered yet.";
				refreshUi();
				return;
			}
			myAccept = true;
			touch();
			send("accept1", null);
			maybeAdvance();
			refreshUi();
		});
	}

	/** Screen 2: final confirmation. Both confirms apply the trade. */
	public void confirmTrade()
	{
		clientRun.accept(() ->
		{
			if (stage != Stage.CONFIRM || myConfirm)
			{
				return;
			}
			myConfirm = true;
			touch();
			send("confirm2", null);
			maybeApply();
			refreshUi();
		});
	}

	/** Cancel from either screen. */
	public void cancel()
	{
		clientRun.accept(() ->
		{
			if (stage == Stage.IDLE)
			{
				return;
			}
			send("cancel", null);
			closeSession("Trade cancelled.");
		});
	}

	// ---- incoming (client thread, via PartyHub) ----

	public void handleTrade(RuneHunterTrade msg, long fromId, String localName)
	{
		String kind = msg.getKind() == null ? "" : msg.getKind();
		switch (kind)
		{
			case "invite":
				if (isBusy())
				{
					RuneHunterTrade busy = base("cancel");
					busy.setNonce(msg.getNonce());
					busy.setTargetId(fromId);
					busy.setReason("busy");
					sender.accept(busy);
					return;
				}
				resetSession();
				stage = Stage.NEGOTIATE;
				nonce = msg.getNonce();
				peerId = fromId;
				peerName = msg.getPlayerName() == null ? "?" : msg.getPlayerName();
				peerJoined = true;
				myName = localName;
				banner = peerName + " wants to trade!";
				touch();
				send("accept_invite", null);
				chat.accept(peerName + " wants to trade — the trade window is open.");
				openUi();
				break;

			case "accept_invite":
				if (sessionMatches(msg))
				{
					peerJoined = true;
					banner = "Trading with " + peerName + ".";
					touch();
					refreshUi();
				}
				break;

			case "offer":
				if (sessionMatches(msg))
				{
					theirOffer = CompanionSnapshot.fromWire(peerName,
						msg.getCreatureKey(), msg.getLevel(), msg.getXp(), msg.getGearCsv());
					resetAgreements();
					stage = Stage.NEGOTIATE;
					banner = peerName + (theirOffer == null
						? " cleared their offer." : " offers " + theirOffer.describe() + ".");
					touch();
					refreshUi();
				}
				break;

			case "accept1":
				if (sessionMatches(msg))
				{
					theirAccept = true;
					touch();
					maybeAdvance();
					refreshUi();
				}
				break;

			case "confirm2":
				if (sessionMatches(msg))
				{
					theirConfirm = true;
					touch();
					maybeApply();
					refreshUi();
				}
				break;

			case "cancel":
				if (sessionMatches(msg))
				{
					closeSession("busy".equals(msg.getReason())
						? peerName + " is busy right now." : peerName + " cancelled the trade.");
				}
				break;

			case "complete":
			default:
				break;
		}
	}

	/** Game tick — stale-session timeout. */
	public void tick()
	{
		if (isBusy() && System.currentTimeMillis() - lastActivityMs > TIMEOUT_MS)
		{
			send("cancel", null);
			closeSession("Trade timed out.");
		}
	}

	// ---- internals (client thread) ----

	private boolean sessionMatches(RuneHunterTrade msg)
	{
		return stage != Stage.IDLE && msg.getNonce() == nonce && msg.getMemberId() == peerId;
	}

	private void maybeAdvance()
	{
		if (stage == Stage.NEGOTIATE && myAccept && theirAccept)
		{
			stage = Stage.CONFIRM;
			banner = "FINAL CHECK — confirm to complete the trade.";
		}
	}

	private void maybeApply()
	{
		if (stage != Stage.CONFIRM || !myConfirm || !theirConfirm || appliedNonces.contains(nonce))
		{
			return;
		}
		appliedNonces.add(nonce);

		// last-second revalidation of my outgoing offer
		if (myOffer != null && ledger.tradeBlockReason(myOffer.getDef()) != null)
		{
			send("cancel", null);
			closeSession("Trade failed — your offer is no longer tradeable.");
			return;
		}

		if (myOffer != null)
		{
			ledger.applyOutgoing(myOffer.getDef());
			chat.accept("You traded away " + myOffer.describe() + ".");
		}
		if (theirOffer != null)
		{
			ledger.applyIncoming(theirOffer);
			chat.accept("You received " + theirOffer.describe() + " from " + peerName + "!");
		}
		send("complete", null);
		stage = Stage.DONE;
		banner = "Trade complete!";
		uiRefresh.run();
		refreshUi();
	}

	private void resetAgreements()
	{
		myAccept = false;
		theirAccept = false;
		myConfirm = false;
		theirConfirm = false;
	}

	private void resetSession()
	{
		resetAgreements();
		myOffer = null;
		theirOffer = null;
		peerJoined = false;
		banner = "";
	}

	private void closeSession(String reason)
	{
		stage = Stage.IDLE;
		resetSession();
		banner = reason;
		chat.accept(reason);
		refreshUi();
	}

	private void touch()
	{
		lastActivityMs = System.currentTimeMillis();
	}

	private RuneHunterTrade base(String kind)
	{
		RuneHunterTrade msg = new RuneHunterTrade();
		msg.setKind(kind);
		msg.setNonce(nonce);
		msg.setTargetId(peerId);
		msg.setPlayerName(myName);
		return msg;
	}

	private void send(String kind, CompanionSnapshot offer)
	{
		RuneHunterTrade msg = base(kind);
		if ("offer".equals(kind))
		{
			if (offer != null)
			{
				msg.setCreatureKey(offer.getDef().key());
				msg.setLevel(offer.getLevel());
				msg.setXp(offer.getXp());
				msg.setGearCsv(offer.gearCsv());
			}
			else
			{
				msg.setCreatureKey("");
			}
		}
		sender.accept(msg);
	}

	private void openUi()
	{
		if (ui != null)
		{
			ui.open();
		}
	}

	private void refreshUi()
	{
		if (ui != null)
		{
			ui.refresh();
		}
	}
}
