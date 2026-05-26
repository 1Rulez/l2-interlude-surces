package phantom.ai.party;

public enum PartyMode
{
	FOLLOW("Follow Leader"),
	ASSIST("Assist Leader"),
	DEFEND("Defend Party"),
	FARM("Free Farm"),
	STAND("Stand Ground");

	private final String _description;

	PartyMode(String description)
	{
		_description = description;
	}

	public String getDescription()
	{
		return _description;
	}
}
