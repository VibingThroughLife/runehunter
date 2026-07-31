package tools;

import java.io.File;
import java.util.Locale;
import net.runelite.cache.NpcManager;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.fs.Store;

/**
 * Dev utility: reads the local OSRS cache and prints, for every RuneHunter
 * roster name, each matching NPC id with its EXACT standing and walking
 * animation ids straight from the cache definition — ground truth, no
 * guessing. Output feeds directly into CreatureAnims.
 *
 *   ./gradlew -q dumpAnims > anims.txt
 */
public final class NpcAnimDump
{
	private static final String[] NAMES = {
		"Chicken", "Cow", "Goblin", "Giant rat", "Imp", "Scorpion", "Wolf", "Seagull",
		"Duck", "Rock Crab", "Unicorn", "Penguin", "Hill Giant", "Moss giant", "Ice giant",
		"Hobgoblin", "Green dragon", "Lesser demon", "Chaos druid", "Kalphite Worker",
		"Crawling Hand", "Cave crawler", "Banshee", "Rockslug", "Cockatrice", "Pyrefiend",
		"TzHaar-Ket", "Blue dragon", "Red dragon", "Black dragon", "Bronze dragon",
		"Iron dragon", "Steel dragon", "Basilisk", "Bloodveld", "Jelly", "Turoth",
		"Aberrant spectre", "Dust devil", "Kurask", "Gargoyle", "Hellhound",
		"Greater demon", "Black demon", "Wyrm", "Ahrim the Blighted", "Dharok the Wretched",
		"Guthan the Infested", "Karil the Tainted", "Torag the Corrupted", "Verac the Defiled",
		"Mithril dragon", "Adamant dragon", "Nechryael", "Abyssal demon", "Cave kraken",
		"Smoke devil", "Drake", "Hydra", "Basilisk Knight", "Demonic gorilla",
		"Lizardman shaman", "Sarachnis", "Giant Mole", "King Black Dragon", "Kalphite Queen",
		"Dagannoth Rex", "Dagannoth Prime", "Dagannoth Supreme", "General Graardor",
		"K'ril Tsutsaroth", "Kree'arra", "Commander Zilyana", "Zulrah", "Vorkath",
		"Cerberus", "Kraken", "Thermonuclear smoke devil", "Alchemical Hydra",
		"Corporeal Beast", "TzTok-Jad", "Callisto", "Venenatis", "Vet'ion", "Scorpia",
		"Chaos Elemental", "Nex",
	};

	private NpcAnimDump()
	{
	}

	public static void main(String[] args) throws Exception
	{
		File base = new File(System.getProperty("user.home"),
			".runelite/jagexcache/oldschool/LIVE");
		if (!base.exists())
		{
			System.err.println("No cache at " + base + " — log into RuneLite once first.");
			return;
		}
		try (Store store = new Store(base))
		{
			store.load();
			NpcManager nm = new NpcManager(store);
			nm.load();

			for (String name : NAMES)
			{
				String want = name.toLowerCase(Locale.ROOT);
				System.out.println("== " + name);
				for (NpcDefinition def : nm.getNpcs())
				{
					if (def == null || def.getName() == null
						|| !def.getName().toLowerCase(Locale.ROOT).equals(want))
					{
						continue;
					}
					System.out.println("   id=" + def.getId()
						+ " cb=" + def.getCombatLevel()
						+ " size=" + def.getSize()
						+ " stand=" + def.getStandingAnimation()
						+ " walk=" + def.getWalkingAnimation()
						+ (def.getModels() == null ? " NO_MODELS" : ""));
				}
			}
		}
	}
}
