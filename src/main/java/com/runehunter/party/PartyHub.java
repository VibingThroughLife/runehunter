package com.runehunter.party;

import com.runehunter.RuneHunterConfig;
import com.runehunter.data.CreatureDef;
import com.runehunter.data.CreatureRoster;
import com.runehunter.game.BattleManager;
import com.runehunter.storage.CollectionStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * RuneHunter's Party-service hub: presence beacons, trading and duels — all
 * riding on ordinary RuneLite party membership (no backend, Hub-compliant).
 *
 * The plugin registers this on the EventBus; the @Subscribe handlers fire
 * on the websocket thread and immediately marshal onto the client thread
 * before touching any state. Self-echoed messages (the party server
 * broadcasts to everyone, sender included) and messages targeted at other
 * members are filtered here, so TradeManager/DuelManager only ever see
 * relevant traffic, already on the client thread.
 */
public class PartyHub
{
	/** Presence info for one party member running RuneHunter. */
	public static final class MemberInfo
	{
		private final long id;
		private final String name;
		private final boolean hasRuneHunter;
		private final String companionDesc;

		MemberInfo(long id, String name, boolean hasRuneHunter, String companionDesc)
		{
			this.id = id;
			this.name = name;
			this.hasRuneHunter = hasRuneHunter;
			this.companionDesc = companionDesc;
		}

		public long getId()
		{
			return id;
		}

		public String getName()
		{
			return name;
		}

		public boolean isHasRuneHunter()
		{
			return hasRuneHunter;
		}

		public String getCompanionDesc()
		{
			return companionDesc;
		}
	}

	private static final class Beacon
	{
		final String companionKey;
		final int companionLevel;

		Beacon(String companionKey, int companionLevel)
		{
			this.companionKey = companionKey;
			this.companionLevel = companionLevel;
		}
	}

	private static final int HELLO_EVERY_TICKS = 50; // ~30s presence refresh

	private final Client client;
	private final ClientThread clientThread;
	private final PartyService partyService;
	private final WSClient wsClient;
	private final CollectionStore store;
	private final BattleManager battleManager;
	private final Runnable uiRefresh;

	private final TradeLedger ledger;
	private final TradeManager tradeManager;
	private final DuelManager duelManager;
	private TradeWindow tradeWindow;

	private final Map<Long, Beacon> beacons = new ConcurrentHashMap<>();
	private int helloCountdown;

	public PartyHub(Client client, ClientThread clientThread, PartyService partyService, WSClient wsClient,
		ConfigManager configManager, CollectionStore store, BattleManager battleManager,
		Supplier<RuneHunterConfig.BattlePace> paceSupplier, Runnable uiRefresh)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.partyService = partyService;
		this.wsClient = wsClient;
		this.store = store;
		this.battleManager = battleManager;
		this.uiRefresh = uiRefresh;

		ledger = new TradeLedger(configManager, store);
		tradeManager = new TradeManager(store, ledger, this::sendMessage, this::chat,
			clientThread::invokeLater, uiRefresh);
		duelManager = new DuelManager(store, paceSupplier, this::sendMessage, this::chat, uiRefresh);
	}

	// ------------------------------------------------------------------
	// lifecycle (wired from RuneHunterPlugin)
	// ------------------------------------------------------------------

	public void startUp()
	{
		wsClient.registerMessage(RuneHunterHello.class);
		wsClient.registerMessage(RuneHunterTrade.class);
		wsClient.registerMessage(RuneHunterDuel.class);
		wsClient.registerMessage(RuneHunterDuelEvent.class);

		SwingUtilities.invokeLater(() ->
		{
			tradeWindow = new TradeWindow(tradeManager);
			tradeManager.setUi(tradeWindow);
		});

		clientThread.invokeLater(() -> sendHello(true));
	}

	public void shutDown()
	{
		wsClient.unregisterMessage(RuneHunterHello.class);
		wsClient.unregisterMessage(RuneHunterTrade.class);
		wsClient.unregisterMessage(RuneHunterDuel.class);
		wsClient.unregisterMessage(RuneHunterDuelEvent.class);
		beacons.clear();
		SwingUtilities.invokeLater(() ->
		{
			if (tradeWindow != null)
			{
				tradeWindow.dispose();
				tradeWindow = null;
			}
		});
	}

	/** Game tick (client thread). */
	public void tick()
	{
		tradeManager.tick();
		duelManager.tick();
		if (--helloCountdown <= 0)
		{
			helloCountdown = HELLO_EVERY_TICKS;
			sendHello(false);
		}
	}

	/** Client tick (client thread). */
	public void clientTick()
	{
		duelManager.clientTick();
	}

	/** The overlay's second battle source (wired via setDuelSource). */
	public DuelManager getDuelManager()
	{
		return duelManager;
	}

	public TradeManager getTradeManager()
	{
		return tradeManager;
	}

	// ------------------------------------------------------------------
	// presence
	// ------------------------------------------------------------------

	/** Party roster with RuneHunter presence, for the Trophy Room strip. */
	public List<MemberInfo> getMembers()
	{
		List<MemberInfo> out = new ArrayList<>();
		if (!partyService.isInParty())
		{
			return out;
		}
		long self = localId();
		for (PartyMember member : partyService.getMembers())
		{
			if (member.getMemberId() == self)
			{
				continue;
			}
			Beacon beacon = beacons.get(member.getMemberId());
			String desc = "";
			if (beacon != null && beacon.companionKey != null && !beacon.companionKey.isEmpty())
			{
				CreatureDef def = CreatureRoster.byKey(beacon.companionKey);
				if (def != null)
				{
					desc = def.getNpcName() + " Lv " + beacon.companionLevel;
				}
			}
			String name = member.getDisplayName();
			out.add(new MemberInfo(member.getMemberId(),
				name == null || name.isEmpty() ? ("Member " + member.getMemberId()) : name,
				beacon != null, desc));
		}
		return out;
	}

	public boolean isInParty()
	{
		return partyService.isInParty();
	}

	// ------------------------------------------------------------------
	// public actions (UI / dev commands — safe from any thread)
	// ------------------------------------------------------------------

	/** Start a trade with a member (by id, from the Trophy Room strip). */
	public void startTrade(long memberId, String memberName)
	{
		tradeManager.startTrade(memberId, memberName, localName());
	}

	/** Dev command: trade with a member matched by display-name fragment. */
	public boolean tradeOffer(String nameFragment)
	{
		MemberInfo member = findMember(nameFragment);
		if (member == null)
		{
			return false;
		}
		startTrade(member.getId(), member.getName());
		return true;
	}

	/** Challenge a member to a duel (by id, from the Trophy Room strip). */
	public void challengeDuel(long memberId, String memberName)
	{
		clientThread.invokeLater(() ->
		{
			if (battleManager.getState() != BattleManager.State.IDLE)
			{
				chat("Finish the wild battle first.");
				return;
			}
			if (!duelManager.challenge(memberId, memberName, localName()))
			{
				chat("Can't duel right now — set a companion and finish any current duel.");
			}
		});
	}

	/** Dev command: duel a member matched by display-name fragment. */
	public boolean duelChallenge(String nameFragment)
	{
		MemberInfo member = findMember(nameFragment);
		if (member == null)
		{
			return false;
		}
		challengeDuel(member.getId(), member.getName());
		return true;
	}

	/** Loopback duel vs your own companion's mirror — no party needed. */
	public void practiceDuel()
	{
		clientThread.invokeLater(() ->
		{
			if (battleManager.getState() != BattleManager.State.IDLE)
			{
				chat("Finish the wild battle first.");
				return;
			}
			if (!duelManager.practiceDuel(localName()))
			{
				chat("Set a companion first (and finish any current duel).");
			}
		});
	}

	/** Dev command: one-line party/trade/duel status. */
	public String partyStatus()
	{
		StringBuilder sb = new StringBuilder();
		sb.append(partyService.isInParty()
			? "In party " + partyService.getPartyId() + " as id " + localId()
			: "Not in a party");
		List<MemberInfo> members = getMembers();
		sb.append(" | peers: ").append(members.size());
		for (MemberInfo m : members)
		{
			sb.append(' ').append(m.getName()).append(m.isHasRuneHunter() ? "[RH]" : "[--]");
		}
		sb.append(" | trade: ").append(tradeManager.getStage());
		sb.append(" | duel: ").append(duelManager.getState());
		return sb.toString();
	}

	private MemberInfo findMember(String nameFragment)
	{
		if (nameFragment == null || nameFragment.isEmpty())
		{
			return null;
		}
		String frag = nameFragment.toLowerCase(Locale.ROOT);
		for (MemberInfo member : getMembers())
		{
			if (member.getName().toLowerCase(Locale.ROOT).contains(frag))
			{
				return member;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------
	// message plumbing
	// ------------------------------------------------------------------

	/** Outgoing send — client thread, party-guarded. */
	private void sendMessage(PartyMemberMessage msg)
	{
		if (partyService.isInParty())
		{
			partyService.send(msg);
		}
	}

	/** Chat line, marshalled onto the client thread. */
	private void chat(String text)
	{
		clientThread.invokeLater(() ->
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", text, null));
	}

	private long localId()
	{
		PartyMember local = partyService.getLocalMember();
		return local == null ? -2L : local.getMemberId();
	}

	private String localName()
	{
		PartyMember local = partyService.getLocalMember();
		String name = local == null ? null : local.getDisplayName();
		return name == null || name.isEmpty() ? "Me" : name;
	}

	private void sendHello(boolean wantReply)
	{
		if (!partyService.isInParty())
		{
			return;
		}
		RuneHunterHello hello = new RuneHunterHello();
		hello.setPlayerName(localName());
		String key = store.getCompanionKey();
		hello.setCompanionKey(key == null ? "" : key);
		if (key != null)
		{
			CreatureDef def = CreatureRoster.byKey(key);
			hello.setCompanionLevel(def == null ? 1 : store.getLevel(def));
		}
		hello.setWantReply(wantReply);
		sendMessage(hello);
	}

	/** targetId 0 = broadcast; otherwise only the addressed member reacts. */
	private boolean notForMe(long targetId)
	{
		return targetId != 0 && targetId != localId();
	}

	// ------------------------------------------------------------------
	// event bus (websocket thread → marshal → client thread)
	// ------------------------------------------------------------------

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		clientThread.invokeLater(() -> sendHello(true));
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		beacons.remove(event.getMemberId());
		clientThread.invokeLater(uiRefresh);
	}

	@Subscribe
	public void onRuneHunterHello(RuneHunterHello msg)
	{
		clientThread.invokeLater(() ->
		{
			if (msg.getMemberId() == localId())
			{
				return;
			}
			boolean firstSeen = beacons.put(msg.getMemberId(),
				new Beacon(msg.getCompanionKey(), msg.getCompanionLevel())) == null;
			if (msg.isWantReply())
			{
				sendHello(false);
			}
			if (firstSeen)
			{
				uiRefresh.run();
			}
		});
	}

	@Subscribe
	public void onRuneHunterTrade(RuneHunterTrade msg)
	{
		clientThread.invokeLater(() ->
		{
			if (msg.getMemberId() == localId() || notForMe(msg.getTargetId()))
			{
				return;
			}
			tradeManager.handleTrade(msg, msg.getMemberId(), localName());
		});
	}

	@Subscribe
	public void onRuneHunterDuel(RuneHunterDuel msg)
	{
		clientThread.invokeLater(() ->
		{
			if (msg.getMemberId() == localId() || notForMe(msg.getTargetId()))
			{
				return;
			}
			if (msg.getKind() != null && msg.getKind().equals("challenge")
				&& battleManager.getState() != BattleManager.State.IDLE)
			{
				return; // mid wild-battle — let the challenge time out
			}
			duelManager.handleDuel(msg, msg.getMemberId(), localName());
		});
	}

	@Subscribe
	public void onRuneHunterDuelEvent(RuneHunterDuelEvent msg)
	{
		clientThread.invokeLater(() ->
		{
			if (msg.getMemberId() == localId() || notForMe(msg.getTargetId()))
			{
				return;
			}
			duelManager.handleDuelEvent(msg);
		});
	}
}
