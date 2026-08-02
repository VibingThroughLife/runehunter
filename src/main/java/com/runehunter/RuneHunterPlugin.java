package com.runehunter;

import com.google.inject.Provides;
import com.runehunter.game.BattleManager;
import com.runehunter.game.CatchManager;
import com.runehunter.game.CompanionManager;
import com.runehunter.game.OrbDropManager;
import com.runehunter.spawn.AnimationLearner;
import com.runehunter.spawn.NpcModelCache;
import com.runehunter.spawn.SpawnManager;
import com.runehunter.storage.CollectionStore;
import com.runehunter.ui.BattleOverlay;
import com.runehunter.ui.CatchCinematicOverlay;
import com.runehunter.ui.TrophyRoom;
import com.runehunter.ui.RadarOverlay;
import com.runehunter.ui.RuneHunterPanel;
import com.runehunter.ui.SpawnDebugOverlay;
import com.runehunter.data.CreatureDef;
import com.runehunter.data.CreatureRoster;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import javax.inject.Named;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "RuneHunter",
	description = "Hunt and catch miniature monsters hidden across Gielinor, and complete your GoDex",
	tags = {"fun", "minigame", "collection", "pet", "catch", "hunt"}
)
public class RuneHunterPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private net.runelite.client.input.MouseManager mouseManager;

	@Inject
	private net.runelite.client.party.PartyService partyService;

	@Inject
	private net.runelite.client.party.WSClient wsClient;

	@Inject
	private net.runelite.client.eventbus.EventBus eventBus;

	@Inject
	private RuneHunterConfig config;

	/**
	 * True only when RuneLite was launched with --developer-mode (RuneLite binds this
	 * flag itself; see RuneLiteModule). Every dev tool in this plugin hangs off it, so
	 * the Plugin Hub build ships them inert: no PokeDev button on the panel, ::rh and
	 * ::rgo do nothing, and the console cannot be opened by any route.
	 *
	 * Note this is a visibility gate, not a security boundary — any user can pass the
	 * flag. That is fine: the tools only affect that player's own client and their own
	 * collection, and spoiling your own Secret Dex is its own punishment.
	 *
	 * ./gradlew runClient already passes --developer-mode, so dev workflow is unchanged.
	 */
	@Inject
	@Named("developerMode")
	private boolean developerMode;

	private NpcModelCache modelCache;
	private AnimationLearner animations;
	private SpawnManager spawnManager;
	private CollectionStore store;
	private CatchManager catchManager;
	private OrbDropManager orbDropManager;
	private CompanionManager companionManager;
	private RadarOverlay radarOverlay;
	private CatchCinematicOverlay cinematicOverlay;
	private SpawnDebugOverlay spawnDebugOverlay;
	private BattleManager battleManager;
	private BattleOverlay battleOverlay;
	private TrophyRoom trophyRoom;
	private RuneHunterPanel panel;
	private NavigationButton navButton;
	private com.runehunter.ui.DevConsole devConsole;
	private com.runehunter.party.PartyHub partyHub;

	/** Window-hiding state for {@link #syncWindowsToBattle()}. */
	private boolean windowsHiddenForBattle;
	private boolean devConsoleWasVisible;
	private boolean trophyRoomWasVisible;
	private boolean announcedSaveProfile;

	@Provides
	RuneHunterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneHunterConfig.class);
	}

	@Override
	protected void startUp()
	{
		// Pick the save file BEFORE any store reads a key. Dev mode gets its own
		// namespace so testing never touches the collection you actually play.
		com.runehunter.storage.SaveProfile.useDevProfile(developerMode);

		modelCache = new NpcModelCache(client, clientThread);
		animations = new AnimationLearner(configManager);
		animations.setModelCache(modelCache);
		animations.load();
		store = new CollectionStore(configManager);
		spawnManager = new SpawnManager(client, clientThread, config, modelCache, animations);
		panel = new RuneHunterPanel(store, this::setCompanion, this::openTrophyRoom,
			this::openDevConsole, developerMode);
		catchManager = new CatchManager(client, clientThread, spawnManager, store, panel::refresh);
		orbDropManager = new OrbDropManager(client, store, spawnManager, panel::refresh);
		companionManager = new CompanionManager(client, modelCache, animations, store);
		radarOverlay = new RadarOverlay(client, config, spawnManager, store);
		spawnDebugOverlay = new SpawnDebugOverlay(client, config, spawnManager);
		cinematicOverlay = new CatchCinematicOverlay(client, config, catchManager);
		battleManager = new BattleManager(client, clientThread, spawnManager, store, panel::refresh);
		battleManager.setPaceSupplier(config::battlePace);
		battleOverlay = new BattleOverlay(client, battleManager, store);
		companionManager.setFaintCheck(battleManager::isCompanionFainted);
		partyHub = new com.runehunter.party.PartyHub(client, clientThread, partyService, wsClient,
			configManager, store, battleManager, config::battlePace, panel::refresh);
		eventBus.register(partyHub);
		partyHub.startUp();
		battleOverlay.setDuelSource(partyHub.getDuelManager());

		overlayManager.add(radarOverlay);
		overlayManager.add(spawnDebugOverlay);
		overlayManager.add(cinematicOverlay);
		overlayManager.add(battleOverlay);
		mouseManager.registerMouseListener(battleOverlay);

		BufferedImage icon = ImageUtil.loadImageResource(RuneHunterPlugin.class, "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("RuneHunter")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			store.load();
			announceSaveProfile();
			panel.refresh();
			modelCache.startScan(() -> clientThread.invokeLater(spawnManager::reseed));
		}
	}

	@Override
	protected void shutDown()
	{
		clientThread.invokeLater(() ->
		{
			spawnManager.despawnAll();
			companionManager.remove();
		});
		overlayManager.remove(radarOverlay);
		overlayManager.remove(spawnDebugOverlay);
		overlayManager.remove(cinematicOverlay);
		overlayManager.remove(battleOverlay);
		mouseManager.unregisterMouseListener(battleOverlay);
		if (partyHub != null)
		{
			partyHub.shutDown();
			eventBus.unregister(partyHub);
			partyHub = null;
		}
		clientToolbar.removeNavigation(navButton);
		if (trophyRoom != null)
		{
			javax.swing.SwingUtilities.invokeLater(trophyRoom::dispose);
			trophyRoom = null;
		}
		if (devConsole != null)
		{
			javax.swing.SwingUtilities.invokeLater(devConsole::dispose);
			devConsole = null;
		}
	}

	private void setCompanion(String key)
	{
		store.setCompanion(key);
		if (key == null)
		{
			clientThread.invokeLater(companionManager::remove);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			store.load();
			announceSaveProfile();
			panel.refresh();
			companionManager.invalidate();
			modelCache.startScan(() -> clientThread.invokeLater(spawnManager::reseed));
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN
			|| event.getGameState() == GameState.HOPPING)
		{
			clientThread.invokeLater(() ->
			{
				spawnManager.despawnAll();
				companionManager.invalidate();
			});
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		spawnManager.tick();
		battleManager.tick(config.battles());
		if (partyHub != null)
		{
			partyHub.tick();
		}
		syncWindowsToBattle();
		if (config.enableCompanion())
		{
			companionManager.tick();
		}
		else
		{
			companionManager.remove();
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			catchManager.clientTick();
			spawnManager.clientTick();
			companionManager.clientTick();
			battleManager.clientTick();
			if (partyHub != null)
			{
				partyHub.clientTick();
			}
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		animations.observe(event.getNpc());
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (config.enableDrops())
		{
			orbDropManager.onKill(event.getNpc());
		}
	}

	/**
	 * Dev/test chat commands (::rh or ::rgo):
	 *   ::rh             — status (scan/resolved/active spawn count)
	 *   ::rh reseed      — force a fresh spawn roll for the loaded scene
	 *   ::rh here [name] [shiny] — guaranteed spawn next to you (random if no name)
	 *   ::rh swarm [n]   — scatter n random creatures around you
	 *   ::rh lineup      — full roster in a numbered grid for animation QA
	 */
	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		// Dev-mode only. On a normal client ::rh is not a RuneHunter command at all.
		if (!developerMode)
		{
			return;
		}
		if (!"rh".equalsIgnoreCase(event.getCommand()) && !"rgo".equalsIgnoreCase(event.getCommand()))
		{
			return;
		}
		runDevCommand(event.getArguments(), this::chat);
	}

	/** Parse a raw console line ("swarm 10", "::rh anim goblin 6181") and run it. */
	public void runDevCommandLine(String line, java.util.function.Consumer<String> out)
	{
		String[] tokens = line.trim().replaceFirst("^::", "").split("\\s+");
		int skip = tokens.length > 0
			&& ("rh".equalsIgnoreCase(tokens[0]) || "rgo".equalsIgnoreCase(tokens[0])) ? 1 : 0;
		String[] args = java.util.Arrays.copyOfRange(tokens, skip, tokens.length);
		if (args.length == 1 && args[0].isEmpty())
		{
			args = new String[0];
		}
		runDevCommand(args, out);
	}

	/** Shared dev-command dispatcher for chat (::rh) and the PokeDev console. */
	public void runDevCommand(String[] args, java.util.function.Consumer<String> out)
	{

		if (args.length == 0 || "status".equalsIgnoreCase(args[0]))
		{
			out.accept("RuneHunter: scan " + (modelCache.isScanComplete() ? "complete" : "running")
				+ ", resolved " + modelCache.resolvedCount() + "/" + CreatureRoster.ALL.size()
				+ " creatures, " + spawnManager.getActiveSpawns().size() + " active spawns.");
			spawnManager.getActiveSpawns().stream().limit(5).forEach(s ->
				out.accept("  - " + s.getDef().getNpcName() + (s.isShiny() ? " (shiny)" : "")
					+ " @ " + s.getWorldPoint().getX() + "," + s.getWorldPoint().getY()));
			return;
		}

		if ("swarm".equalsIgnoreCase(args[0]))
		{
			int n = 10;
			if (args.length > 1)
			{
				try
				{
					n = Math.max(1, Math.min(30, Integer.parseInt(args[1])));
				}
				catch (NumberFormatException ignored)
				{
				}
			}
			final int count = n;
			clientThread.invokeLater(() ->
			{
				int placed = spawnManager.spawnSwarm(count);
				out.accept("RuneHunter: swarm placed " + placed + "/" + count + " creatures around you.");
			});
			return;
		}

		if ("orbs".equalsIgnoreCase(args[0]))
		{
			int n = args.length > 1 ? parseIntOr(args[1], 25) : 25;
			final int base = Math.max(1, Math.min(100, n));
			store.addOrb(com.runehunter.data.OrbType.UNPOWERED, base);
			store.addOrb(com.runehunter.data.OrbType.ELEMENTAL, Math.max(1, base / 3));
			store.addOrb(com.runehunter.data.OrbType.CRYSTAL, Math.max(1, base / 5));
			store.addOrb(com.runehunter.data.OrbType.ELDRITCH, Math.max(1, base / 12));
			panel.refresh();
			out.accept("RuneHunter: dev orb pack added — " + base + " unpowered, "
				+ Math.max(1, base / 3) + " elemental, " + Math.max(1, base / 5) + " crystal, "
				+ Math.max(1, base / 12) + " eldritch.");
			return;
		}

		if ("lineup".equalsIgnoreCase(args[0]))
		{
			int page = args.length > 1 ? parseIntOr(args[1], 1) : 1;
			final int p = Math.max(1, Math.min(SpawnManager.lineupPages(), page));
			clientThread.invokeLater(() ->
			{
				int placed = spawnManager.spawnLineup(p);
				int from = (p - 1) * SpawnManager.LINEUP_PAGE_SIZE + 1;
				int to = Math.min(CreatureRoster.ALL.size(), p * SpawnManager.LINEUP_PAGE_SIZE);
				out.accept("RuneHunter: lineup page " + p + "/" + SpawnManager.lineupPages()
					+ " — creatures #" + from + "-" + to + " (" + placed + " placed).");
				out.accept("Right-click anything broken -> Report bug. Next batch: ::rh lineup " + (p + 1));
			});
			return;
		}

		if ("anim".equalsIgnoreCase(args[0]))
		{
			if (args.length < 3)
			{
				out.accept("Usage: ::rh anim <name> <animId> — applies an animation to the nearest matching spawn.");
				return;
			}
			final int animId = parseIntOr(args[args.length - 1], -1);
			StringBuilder nb = new StringBuilder();
			for (int i = 1; i < args.length - 1; i++)
			{
				nb.append(i > 1 ? " " : "").append(args[i]);
			}
			final String name = nb.toString();
			if (animId < 0)
			{
				out.accept("RuneHunter: '" + args[args.length - 1] + "' isn't an animation id.");
				return;
			}
			clientThread.invokeLater(() ->
			{
				String applied = spawnManager.applyTestAnim(name, animId);
				out.accept(applied != null
					? "RuneHunter: playing anim " + animId + " on " + applied + "."
					: "RuneHunter: no active spawn matching '" + name + "'.");
			});
			return;
		}

		if ("prop".equalsIgnoreCase(args[0]))
		{
			if (args.length < 2)
			{
				out.accept("Usage: ::rh prop <modelId> | next | prev | off");
				return;
			}
			final String arg = args[1];
			clientThread.invokeLater(() ->
			{
				if ("off".equalsIgnoreCase(arg))
				{
					spawnManager.hideProp();
					out.accept("RuneHunter: prop hidden.");
					return;
				}
				int id;
				if ("next".equalsIgnoreCase(arg))
				{
					id = spawnManager.getPropModelId() + 1;
				}
				else if ("prev".equalsIgnoreCase(arg))
				{
					id = Math.max(0, spawnManager.getPropModelId() - 1);
				}
				else
				{
					id = parseIntOr(arg, -1);
				}
				if (id < 0)
				{
					out.accept("RuneHunter: '" + arg + "' isn't a model id.");
					return;
				}
				out.accept(spawnManager.showProp(id)
					? "RuneHunter: showing model " + id + " east of you (::rh prop next / prev to step)."
					: "RuneHunter: model " + id + " couldn't be loaded.");
			});
			return;
		}

		if ("console".equalsIgnoreCase(args[0]) || "pokedev".equalsIgnoreCase(args[0]))
		{
			openDevConsole();
			out.accept("RuneHunter: PokeDev console opened.");
			return;
		}

		if ("step".equalsIgnoreCase(args[0]))
		{
			if (args.length < 2)
			{
				out.accept("Usage: step <name> — forces one wander step on a matching spawn.");
				return;
			}
			final String name = joinArgs(args, 1);
			clientThread.invokeLater(() ->
			{
				String stepped = spawnManager.forceStep(name);
				out.accept(stepped != null
					? "RuneHunter: stepped " + stepped + "."
					: "RuneHunter: no active spawn matching '" + name + "'.");
			});
			return;
		}

		if ("despawn".equalsIgnoreCase(args[0]))
		{
			if (args.length < 2)
			{
				out.accept("Usage: despawn <name|all>");
				return;
			}
			final String name = joinArgs(args, 1);
			clientThread.invokeLater(() ->
			{
				if ("all".equalsIgnoreCase(name))
				{
					spawnManager.despawnAll();
					out.accept("RuneHunter: all spawns cleared.");
					return;
				}
				int n = spawnManager.despawnMatching(name);
				out.accept("RuneHunter: despawned " + n + " matching '" + name + "'.");
			});
			return;
		}

		if ("battle".equalsIgnoreCase(args[0]))
		{
			final String name = args.length > 1 ? joinArgs(args, 1) : "";
			clientThread.invokeLater(() ->
				out.accept(battleManager.forceEncounter(name)
					? "RuneHunter: a challenger approaches!"
					: "RuneHunter: couldn't start a battle (already in one, or no match for '" + name + "')."));
			return;
		}

		if ("style".equalsIgnoreCase(args[0]))
		{
			if (args.length < 2)
			{
				out.accept("Usage: style <melee|ranged|magic|disable|skull> — forces the next wild telegraph.");
				return;
			}
			out.accept(battleManager.debugForceStyle(args[1])
				? "RuneHunter: next telegraph forced to " + args[1].toLowerCase(Locale.ROOT) + "."
				: "RuneHunter: unknown style '" + args[1] + "' (melee|ranged|magic|disable|skull).");
			return;
		}

		if ("trophy".equalsIgnoreCase(args[0]))
		{
			openTrophyRoom();
			out.accept("RuneHunter: Trophy Room opened.");
			return;
		}

		if ("gear".equalsIgnoreCase(args[0]))
		{
			int n = args.length > 1 ? Math.max(1, Math.min(20, parseIntOr(args[1], 3))) : 3;
			StringBuilder got = new StringBuilder();
			for (int i = 0; i < n; i++)
			{
				com.runehunter.data.GearItem g =
					com.runehunter.data.GearItem.randomDrop(ThreadLocalRandom.current());
				store.addGear(g);
				got.append(got.length() > 0 ? ", " : "").append(g.getDisplayName());
			}
			panel.refresh();
			if (trophyRoom != null)
			{
				trophyRoom.refresh();
			}
			out.accept("RuneHunter: dev gear added — " + got + ".");
			return;
		}

		if ("report".equalsIgnoreCase(args[0]))
		{
			if (args.length > 1 && "clear".equalsIgnoreCase(args[1]))
			{
				store.clearBugReports();
				out.accept("RuneHunter: bug report list cleared.");
				return;
			}
			List<String> reports = store.reportedBugs();
			if (reports.isEmpty())
			{
				out.accept("RuneHunter: no bugs reported yet — right-click a broken creature -> Report bug.");
				return;
			}
			out.accept("RuneHunter: " + reports.size() + " reported (::rh report clear to reset):");
			for (String entry : reports)
			{
				out.accept("  - " + entry);
			}
			return;
		}

		if ("reseed".equalsIgnoreCase(args[0]))
		{
			clientThread.invokeLater(() ->
			{
				spawnManager.reseed();
				out.accept("RuneHunter: reseeded, " + spawnManager.getActiveSpawns().size() + " active spawns.");
			});
			return;
		}

		if ("secret".equalsIgnoreCase(args[0]))
		{
			final String arg = args.length > 1 ? joinArgs(args, 1) : "";
			if (arg.isEmpty() || "list".equalsIgnoreCase(arg) || "status".equalsIgnoreCase(arg))
			{
				clientThread.invokeLater(() ->
				{
					out.accept("RuneHunter secrets:");
					for (String line : spawnManager.secretStatus())
					{
						out.accept(line);
					}
				});
				return;
			}
			if ("reload".equalsIgnoreCase(arg))
			{
				clientThread.invokeLater(() ->
				{
					spawnManager.reloadSecretModels();
					out.accept("RuneHunter: secret models cleared — next spawn rebuilds from SecretIds.");
				});
				return;
			}
			clientThread.invokeLater(() ->
			{
				String placed = spawnManager.spawnSecret(arg);
				out.accept(placed != null
					? "RuneHunter: spawned " + placed + " nearby."
					: "RuneHunter: no secret matching '" + arg + "' — try "
						+ String.join(" | ", SpawnManager.secretNames()));
			});
			return;
		}

		if ("trade".equalsIgnoreCase(args[0]))
		{
			if (args.length < 2)
			{
				out.accept("Usage: trade <player name> — offer a trade to a party member.");
				return;
			}
			final String who = joinArgs(args, 1);
			clientThread.invokeLater(() ->
				out.accept(partyHub != null && partyHub.tradeOffer(who)
					? "RuneHunter: trade invite sent to " + who + "."
					: "RuneHunter: couldn't start a trade with '" + who + "' (same party? RuneHunter installed?)."));
			return;
		}

		if ("duel".equalsIgnoreCase(args[0]))
		{
			final String who = args.length > 1 ? joinArgs(args, 1) : "";
			clientThread.invokeLater(() ->
			{
				if (partyHub == null)
				{
					out.accept("RuneHunter: party system not ready.");
					return;
				}
				if (who.isEmpty() || "practice".equalsIgnoreCase(who))
				{
					partyHub.practiceDuel();
					out.accept("RuneHunter: practice duel starting — your companion's mirror approaches!");
					return;
				}
				out.accept(partyHub.duelChallenge(who)
					? "RuneHunter: duel challenge sent to " + who + "."
					: "RuneHunter: couldn't challenge '" + who + "' (same party? companion set?).");
			});
			return;
		}

		if ("party".equalsIgnoreCase(args[0]))
		{
			out.accept(partyHub != null ? partyHub.partyStatus() : "RuneHunter: party system not ready.");
			return;
		}

		if ("here".equalsIgnoreCase(args[0]))
		{
			boolean shiny = false;
			StringBuilder nameBuf = new StringBuilder();
			for (int i = 1; i < args.length; i++)
			{
				if ("shiny".equalsIgnoreCase(args[i]))
				{
					shiny = true;
				}
				else
				{
					nameBuf.append(nameBuf.length() > 0 ? " " : "").append(args[i]);
				}
			}
			String name = nameBuf.toString().toLowerCase(Locale.ROOT);

			CreatureDef def = null;
			if (!name.isEmpty())
			{
				for (CreatureDef d : CreatureRoster.ALL)
				{
					if (d.getNpcName().toLowerCase(Locale.ROOT).contains(name))
					{
						def = d;
						break;
					}
				}
				if (def == null)
				{
					out.accept("RuneHunter: no creature matching '" + name + "'.");
					return;
				}
			}
			else
			{
				List<CreatureDef> resolved = new ArrayList<>();
				for (CreatureDef d : CreatureRoster.ALL)
				{
					if (modelCache.resolveId(d) != null)
					{
						resolved.add(d);
					}
				}
				if (resolved.isEmpty())
				{
					out.accept("RuneHunter: no creatures resolved to NPC ids yet — scan "
						+ (modelCache.isScanComplete() ? "complete but found nothing (bug!)" : "still running") + ".");
					return;
				}
				def = resolved.get(ThreadLocalRandom.current().nextInt(resolved.size()));
			}

			final CreatureDef spawnDef = def;
			final boolean spawnShiny = shiny;
			clientThread.invokeLater(() ->
			{
				boolean ok = spawnManager.spawnNear(spawnDef, spawnShiny);
				out.accept(ok
					? "RuneHunter: spawned " + (spawnShiny ? "shiny " : "") + spawnDef.getNpcName() + " nearby."
					: "RuneHunter: failed to place " + spawnDef.getNpcName()
						+ " (id " + modelCache.resolveId(spawnDef) + ", model/tile problem).");
			});
		}
	}

	private static String joinArgs(String[] args, int from)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = from; i < args.length; i++)
		{
			sb.append(i > from ? " " : "").append(args[i]);
		}
		return sb.toString();
	}

	/** Lazily create + show the Trophy Room window (EDT-safe). */
	public void openTrophyRoom()
	{
		javax.swing.SwingUtilities.invokeLater(() ->
		{
			if (trophyRoom == null)
			{
				trophyRoom = new TrophyRoom(store, panel::refresh);
				trophyRoom.setLocationRelativeTo(null);
			}
			trophyRoom.setPartyHub(partyHub);
			trophyRoom.refresh();
			trophyRoom.setVisible(true);
			trophyRoom.toFront();
		});
	}

	/**
	 * Tuck the floating Swing windows away while a battle is on screen.
	 *
	 * The battle renders as a full-screen overlay on the game canvas, but PokeDev
	 * and the Trophy Room are separate always-on-top frames, so they sit right on
	 * top of the fight and have to be dragged off manually. Hide them when a
	 * battle starts and restore whichever were open once it ends — the player
	 * should never have to move a window to see their own fight.
	 */
	private void syncWindowsToBattle()
	{
		if (!config.hideWindowsInBattle())
		{
			return;
		}

		final boolean inBattle = isBattleOnScreen();
		if (inBattle == windowsHiddenForBattle)
		{
			return;
		}
		windowsHiddenForBattle = inBattle;

		final com.runehunter.ui.DevConsole dev = devConsole;
		final com.runehunter.ui.TrophyRoom trophy = trophyRoom;

		if (inBattle)
		{
			// Remember what was open so we only restore those.
			devConsoleWasVisible = dev != null && dev.isVisible();
			trophyRoomWasVisible = trophy != null && trophy.isVisible();
			javax.swing.SwingUtilities.invokeLater(() ->
			{
				if (dev != null && devConsoleWasVisible)
				{
					dev.setVisible(false);
				}
				if (trophy != null && trophyRoomWasVisible)
				{
					trophy.setVisible(false);
				}
			});
			return;
		}

		javax.swing.SwingUtilities.invokeLater(() ->
		{
			if (dev != null && devConsoleWasVisible)
			{
				dev.setVisible(true);
			}
			if (trophy != null && trophyRoomWasVisible)
			{
				trophy.setVisible(true);
			}
		});
		devConsoleWasVisible = false;
		trophyRoomWasVisible = false;
	}

	/** True while any battle-like thing is drawing over the game canvas. */
	private boolean isBattleOnScreen()
	{
		if (battleManager != null
			&& battleManager.getState() != com.runehunter.game.BattleManager.State.IDLE)
		{
			return true;
		}
		return partyHub != null
			&& partyHub.getDuelManager() != null
			&& partyHub.getDuelManager().getState()
				!= com.runehunter.game.BattleManager.State.IDLE;
	}

	/**
	 * Tell the player which save they're on, once, when it isn't the normal one.
	 * An unexplained empty GoDex reads as data loss; a one-line notice turns it
	 * into an obviously separate profile.
	 */
	private void announceSaveProfile()
	{
		// Once per session. This also fires on world hops, and a line every hop
		// would be exactly the chat spam the filters exist to prevent.
		if (announcedSaveProfile)
		{
			return;
		}
		final String notice = com.runehunter.storage.SaveProfile.loginNotice();
		if (notice != null)
		{
			announcedSaveProfile = true;
			chat(notice);
		}
	}

	/** Lazily create + show the PokeDev console window (EDT-safe). */
	public void openDevConsole()
	{
		// Second gate. The panel button is already hidden outside dev mode, but this
		// method is also reachable from `::rh console`, so it defends itself.
		if (!developerMode)
		{
			return;
		}
		javax.swing.SwingUtilities.invokeLater(() ->
		{
			if (devConsole == null)
			{
				devConsole = new com.runehunter.ui.DevConsole(this::runDevCommandLine);
				devConsole.setLocationRelativeTo(null);
			}
			devConsole.setVisible(true);
			devConsole.toFront();
		});
	}

	private static int parseIntOr(String s, int fallback)
	{
		try
		{
			return Integer.parseInt(s);
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}

	private void chat(String message)
	{
		clientThread.invokeLater(() ->
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null));
	}
}
