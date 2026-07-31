package com.runehunter.party;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Presence beacon: "I run RuneHunter, and this is my companion." Broadcast on
 * join/startup and whenever the companion changes; wantReply asks everyone to
 * beacon back so a late joiner learns the room. Gson-serialized — keep flat
 * fields only.
 */
public class RuneHunterHello extends PartyMemberMessage
{
	private String playerName;
	private String companionKey;
	private int companionLevel;
	private boolean wantReply;

	public RuneHunterHello()
	{
	}

	public String getPlayerName()
	{
		return playerName;
	}

	public void setPlayerName(String playerName)
	{
		this.playerName = playerName;
	}

	public String getCompanionKey()
	{
		return companionKey;
	}

	public void setCompanionKey(String companionKey)
	{
		this.companionKey = companionKey;
	}

	public int getCompanionLevel()
	{
		return companionLevel;
	}

	public void setCompanionLevel(int companionLevel)
	{
		this.companionLevel = companionLevel;
	}

	public boolean isWantReply()
	{
		return wantReply;
	}

	public void setWantReply(boolean wantReply)
	{
		this.wantReply = wantReply;
	}
}
