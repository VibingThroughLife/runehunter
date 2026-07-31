package com.runehunter.party;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Duel control + guest input. kind is one of:
 *   challenge | accept | decline | input | forfeit | ping | abort
 * challenge/accept carry the sender's companion snapshot (used by the host
 * to simulate both sides). input carries the guest's prayer state and
 * special press; the host applies it on arrival and uses last-known state
 * at each impact. Gson-serialized — flat fields only.
 */
public class RuneHunterDuel extends PartyMemberMessage
{
	private String kind;
	private long nonce;
	private long targetId;
	private String playerName;
	private String creatureKey;
	private int level;
	private int xp;
	private String gearCsv;
	private String prayer;   // "MELEE" | "RANGED" | "MAGIC" | "" (none)
	private boolean special; // guest pressed SPECIAL
	private String reason;

	public RuneHunterDuel()
	{
	}

	public String getKind()
	{
		return kind;
	}

	public void setKind(String kind)
	{
		this.kind = kind;
	}

	public long getNonce()
	{
		return nonce;
	}

	public void setNonce(long nonce)
	{
		this.nonce = nonce;
	}

	public long getTargetId()
	{
		return targetId;
	}

	public void setTargetId(long targetId)
	{
		this.targetId = targetId;
	}

	public String getPlayerName()
	{
		return playerName;
	}

	public void setPlayerName(String playerName)
	{
		this.playerName = playerName;
	}

	public String getCreatureKey()
	{
		return creatureKey;
	}

	public void setCreatureKey(String creatureKey)
	{
		this.creatureKey = creatureKey;
	}

	public int getLevel()
	{
		return level;
	}

	public void setLevel(int level)
	{
		this.level = level;
	}

	public int getXp()
	{
		return xp;
	}

	public void setXp(int xp)
	{
		this.xp = xp;
	}

	public String getGearCsv()
	{
		return gearCsv;
	}

	public void setGearCsv(String gearCsv)
	{
		this.gearCsv = gearCsv;
	}

	public String getPrayer()
	{
		return prayer;
	}

	public void setPrayer(String prayer)
	{
		this.prayer = prayer;
	}

	public boolean isSpecial()
	{
		return special;
	}

	public void setSpecial(boolean special)
	{
		this.special = special;
	}

	public String getReason()
	{
		return reason;
	}

	public void setReason(String reason)
	{
		this.reason = reason;
	}
}
