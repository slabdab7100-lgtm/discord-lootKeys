package com.discordlootkeys;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("lootkeydiscord")
public interface LootKeyDiscordConfig extends Config
{
	@ConfigItem(
		keyName = "enabled",
		name = "Enable plugin",
		description = "Send Loot Key screenshots to Discord"
	)
	default boolean enabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "minimumValue",
		name = "Minimum key value",
		description = "Only send keys whose total Grand Exchange value meets or exceeds this amount"
	)
	default int minimumValue()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "webhookUrl",
		name = "Discord webhook",
		description = "Discord webhook URL. This URL is stored in RuneLite configuration and is used to upload screenshots to Discord.",
		secret = true
	)
	default String webhookUrl()
	{
		return "";
	}
}
