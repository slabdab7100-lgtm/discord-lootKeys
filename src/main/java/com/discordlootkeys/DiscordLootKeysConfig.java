package com.discordlootkeys;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("discordlootkeys")
public interface DiscordLootKeysConfig extends Config
{
    @ConfigItem(keyName = "enabled", name = "Enable plugin", description = "Automatically send qualifying Loot Key screenshots.", position = 0)
    default boolean enabled()
    {
        return true;
    }

    @ConfigItem(keyName = "minimumValue", name = "Minimum key value", description = "Only send keys whose total GE value is at least this many GP. Set to 0 to send every key.", position = 1)
    default int minimumValue()
    {
        return 1_000_000;
    }

    @ConfigItem(keyName = "webhookUrl", name = "Discord webhook URL", description = "Screenshots of qualifying Loot Keys and their total value are sent to this Discord webhook. Treat the webhook URL as a secret.", position = 2, secret = true)
    default String webhookUrl()
    {
        return "";
    }
}
