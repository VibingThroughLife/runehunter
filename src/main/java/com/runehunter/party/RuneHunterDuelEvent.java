package com.runehunter.party;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Host → guest duel event stream. type is one of:
 *   telegraph | impact | smite | special | log | result
 * side names whose companion the event is about, from the HOST's point of
 * view: "host" = the host's companion, "guest" = the guest's companion.
 * Every event also carries a full state-sync block (both HPs plus the
 * guest's prayer points / lock / special energy) so the guest can simply
 * adopt it — latency-safe, no drift. Gson-serialized — flat fields only.
 */
public class RuneHunterDuelEvent extends PartyMemberMessage
{
	private String type;
	private long nonce;
	private long targetId;
	private String side;
	private String style;     // telegraph style name
	private int impactIn;     // telegraph: game ticks to impact
	private int value;        // impact/special damage
	private boolean deflected;
	private String text;      // log line
	private String winner;    // result: "host" | "guest" | "abort"

	// state sync block
	private int hostHp;
	private int guestHp;
	private int guestPp;
	private int guestLock;
	private int guestEnergy;

	public RuneHunterDuelEvent()
	{
	}

	public String getType()
	{
		return type;
	}

	public void setType(String type)
	{
		this.type = type;
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

	public String getSide()
	{
		return side;
	}

	public void setSide(String side)
	{
		this.side = side;
	}

	public String getStyle()
	{
		return style;
	}

	public void setStyle(String style)
	{
		this.style = style;
	}

	public int getImpactIn()
	{
		return impactIn;
	}

	public void setImpactIn(int impactIn)
	{
		this.impactIn = impactIn;
	}

	public int getValue()
	{
		return value;
	}

	public void setValue(int value)
	{
		this.value = value;
	}

	public boolean isDeflected()
	{
		return deflected;
	}

	public void setDeflected(boolean deflected)
	{
		this.deflected = deflected;
	}

	public String getText()
	{
		return text;
	}

	public void setText(String text)
	{
		this.text = text;
	}

	public String getWinner()
	{
		return winner;
	}

	public void setWinner(String winner)
	{
		this.winner = winner;
	}

	public int getHostHp()
	{
		return hostHp;
	}

	public void setHostHp(int hostHp)
	{
		this.hostHp = hostHp;
	}

	public int getGuestHp()
	{
		return guestHp;
	}

	public void setGuestHp(int guestHp)
	{
		this.guestHp = guestHp;
	}

	public int getGuestPp()
	{
		return guestPp;
	}

	public void setGuestPp(int guestPp)
	{
		this.guestPp = guestPp;
	}

	public int getGuestLock()
	{
		return guestLock;
	}

	public void setGuestLock(int guestLock)
	{
		this.guestLock = guestLock;
	}

	public int getGuestEnergy()
	{
		return guestEnergy;
	}

	public void setGuestEnergy(int guestEnergy)
	{
		this.guestEnergy = guestEnergy;
	}
}
