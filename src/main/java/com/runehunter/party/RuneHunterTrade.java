package com.runehunter.party;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * One trade-protocol step. kind is one of:
 *   invite | accept_invite | offer | accept1 | confirm2 | cancel | complete
 * targetId addresses one member (party messages broadcast to everyone);
 * nonce identifies the trade session (challenger-generated). The creature
 * fields carry the sender's current offer for kind=offer (empty creatureKey
 * = offer cleared). Gson-serialized — flat fields only.
 */
public class RuneHunterTrade extends PartyMemberMessage
{
	private String kind;
	private long nonce;
	private long targetId;
	private String playerName;
	private String creatureKey;
	private int level;
	private int xp;
	private String gearCsv;
	private String reason;

	public RuneHunterTrade()
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

	public String getReason()
	{
		return reason;
	}

	public void setReason(String reason)
	{
		this.reason = reason;
	}
}
