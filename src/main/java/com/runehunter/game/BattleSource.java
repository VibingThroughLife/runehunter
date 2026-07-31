package com.runehunter.game;

import com.runehunter.data.CreatureDef;
import com.runehunter.data.GearItem;
import java.util.List;

/**
 * Everything BattleOverlay needs to render and drive one battle-like thing.
 * BattleManager implements it for wild encounters; com.runehunter.party's
 * DuelManager implements it for player-vs-player duels, so both render
 * through the exact same overlay.
 *
 * Threading contract: getters are snapshot reads (client thread); the input
 * methods are AWT-safe and must only set atomic request fields.
 */
public interface BattleSource
{
	BattleManager.State getState();

	/** True for PvP duels — the overlay swaps in the DUEL treatment. */
	boolean isDuel();

	/** Owning player's name for the far combatant, or null for wild NPCs. */
	String getWildOwnerName();

	/** Line shown on the PROMPT screen. */
	String getPromptText();

	CreatureDef getWild();

	int getWildLevel();

	CreatureDef getCompanion();

	int getCompanionLevel();

	int getMyHp();

	int getMyMaxHp();

	int getWildHp();

	int getWildMaxHp();

	BattleManager.Prayer getActivePrayer();

	int getPrayerPoints();

	int getMaxPrayerPoints();

	int getPrayerLockTicks();

	BattleManager.AttackStyle getIncomingStyle();

	int getImpactTicks();

	/** Telegraph length in game ticks for the current pace (pip count). */
	int getTelegraphTicks();

	int getSpecialEnergy();

	boolean isSpecialReady();

	int getRunLockTicks();

	int getMyHitSeq();

	int getMyHitDmg();

	int getWildHitSeq();

	int getWildHitDmg();

	/** True when the far combatant's latest hit was a deflect (blue 0). */
	boolean isWildHitDeflect();

	int getDeflectSeq();

	int getSmiteSeq();

	int getLogVersion();

	List<String> getLog();

	GearItem getLastDrop();

	// ---- inputs (AWT-safe request setters) ----

	void accept();

	void decline();

	void clickPrayer(BattleManager.Prayer prayer);

	void clickSpecial();

	void clickRun();

	void dismiss();
}
