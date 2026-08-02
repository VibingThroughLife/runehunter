package com.runehunter.storage;

/**
 * Which save file we're reading and writing.
 *
 * <h2>Why this exists</h2>
 *
 * Developer mode spawns creatures on demand, hands out orb packs, forces battle
 * outcomes and force-spawns Secret Dex entries. All of that lands in the same
 * RSProfile-scoped config as real play, so testing the plugin quietly destroys
 * the collection you were building on your own account.
 *
 * So dev mode gets its own save. Same RS account, same config group, different key
 * namespace — a developer profile and a main profile that never see each other.
 * Launch with {@code --developer-mode} and you're on the dev save; launch normally
 * (or {@code ./gradlew runClient -PnoDev}) and your real collection is untouched.
 *
 * <h2>Why it's static</h2>
 *
 * Developer mode is fixed for the lifetime of the process — RuneLite reads it once
 * at startup and it cannot change while running. Threading a boolean through
 * PartyHub, TradeManager and TradeLedger to express a process-wide constant would
 * be noise. It is set exactly once, from the plugin's startUp, before anything
 * reads a key.
 *
 * <h2>What is NOT split</h2>
 *
 * Learned animation data ({@code AnimationLearner}) stays shared. It is knowledge
 * about the game's cache, not player progress, and re-learning it on every profile
 * switch would be pure waste.
 */
public final class SaveProfile
{
	/** Key namespace for the developer save. Never change this — it would orphan data. */
	private static final String DEV_PREFIX = "dev_";

	private static volatile String prefix = "";

	private SaveProfile()
	{
	}

	/**
	 * Select the save file. Call once from the plugin's startUp, before any store
	 * loads. Passing the injected {@code developerMode} flag is the intended use.
	 */
	public static void useDevProfile(boolean devMode)
	{
		prefix = devMode ? DEV_PREFIX : "";
	}

	/** Namespace a config key for the active profile. */
	public static String key(String baseKey)
	{
		return prefix + baseKey;
	}

	public static boolean isDev()
	{
		return !prefix.isEmpty();
	}

	/** For the panel header and the login message, so nobody loses track. */
	public static String label()
	{
		return isDev() ? "Developer save" : "Main save";
	}

	/**
	 * Told to the player on login when they're on the dev save.
	 *
	 * Silently swapping someone's save file is worse than not splitting it at all —
	 * you want to know instantly that the empty GoDex you're looking at is the test
	 * profile and not a wipe.
	 */
	public static String loginNotice()
	{
		return isDev()
			? "RuneHunter: developer save active. Your main collection is untouched."
			: null;
	}
}
