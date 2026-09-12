package com.discordlootkeys;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class LootKeyDiscordPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LootKeyDiscordPlugin.class);
		RuneLite.main(args);
	}
}
