package tools;

import java.io.File;
import java.util.Arrays;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.fs.Store;

/**
 * Dev utility (never ships with the plugin): reads the local OSRS cache and
 * prints the model ids used by object/loc definitions. Used to hunt down prop
 * models like the MEP2 handholds.
 *
 *   ./gradlew -q dumpLoc                    -> dumps the default handhold ids
 *   ./gradlew -q dumpLoc -Pids=2236,3583    -> dumps specific loc ids
 */
public final class LocDump
{
	private LocDump()
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
			ObjectManager om = new ObjectManager(store);
			om.load();
			for (String a : args)
			{
				int id;
				try
				{
					id = Integer.parseInt(a.trim());
				}
				catch (NumberFormatException e)
				{
					continue;
				}
				ObjectDefinition def = om.getObject(id);
				if (def == null)
				{
					System.out.println("loc " + id + ": <not found>");
					continue;
				}
				System.out.println("loc " + id + " \"" + def.getName() + "\""
					+ " models=" + Arrays.toString(def.getObjectModels())
					+ " modelTypes=" + Arrays.toString(def.getObjectTypes()));
			}
		}
	}
}
