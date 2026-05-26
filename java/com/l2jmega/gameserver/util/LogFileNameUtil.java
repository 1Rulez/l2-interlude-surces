package com.l2jmega.gameserver.util;

public final class LogFileNameUtil
{
	private static final String INVALID_FILE_NAME_CHARS = "\\/:*?\"<>|";
	
	private LogFileNameUtil()
	{
	}
	
	public static String sanitize(String value)
	{
		if (value == null || value.isEmpty())
			return "unknown";
		
		final StringBuilder result = new StringBuilder(value.length());
		for (char character : value.toCharArray())
		{
			if (INVALID_FILE_NAME_CHARS.indexOf(character) >= 0 || Character.isISOControl(character))
				result.append('_');
			else
				result.append(character);
		}
		
		return result.toString();
	}
}
