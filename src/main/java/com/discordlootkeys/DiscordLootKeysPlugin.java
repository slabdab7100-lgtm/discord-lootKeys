package com.discordlootkeys;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.DrawManager;

@Slf4j
@PluginDescriptor(
    name = "Discord Loot Keys",
    description = "Sends screenshots of opened PvP Loot Keys to a Discord webhook when they meet a minimum value.",
    tags = {"loot", "loot key", "pvp", "discord", "screenshot"}
)
public class DiscordLootKeysPlugin extends Plugin
{
    private static final List<Integer> LOOT_KEY_CONTAINERS = List.of(
        InventoryID.DEADMAN_LOOT_INV0,
        InventoryID.DEADMAN_LOOT_INV1,
        InventoryID.DEADMAN_LOOT_INV2,
        InventoryID.DEADMAN_LOOT_INV3
    );

    private static final int MAX_CONTENT_RETRIES = 5;

    @Inject private Client client;
    @Inject private ItemManager itemManager;
    @Inject private DrawManager drawManager;
    @Inject private ScheduledExecutorService executor;
    @Inject private DiscordLootKeysConfig config;
    @Inject private DiscordWebhookClient webhookClient;

    private boolean keyOpen;
    private int contentRetries;

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.WILDY_LOOT_CHEST && !keyOpen)
        {
            keyOpen = true;
            contentRetries = 0;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (client.getWidget(InterfaceID.WILDY_LOOT_CHEST) == null)
        {
            keyOpen = false;
            return;
        }

        if (!keyOpen || !config.enabled())
        {
            return;
        }

        String webhook = config.webhookUrl().trim();
        if (!webhookClient.isValidWebhook(webhook))
        {
            return;
        }

        if (!hasLootKeyContents())
        {
            if (++contentRetries >= MAX_CONTENT_RETRIES)
            {
                keyOpen = false;
            }
            return;
        }

        keyOpen = false;
        long totalValue = getLootKeyValue();
        if (totalValue < Math.max(0, config.minimumValue()))
        {
            return;
        }

        drawManager.requestNextFrameListener(image -> captureAndUpload(image, totalValue, webhook));
    }

    private boolean hasLootKeyContents()
    {
        for (int containerId : LOOT_KEY_CONTAINERS)
        {
            ItemContainer container = client.getItemContainer(containerId);
            if (container != null && container.getItems().length > 0)
            {
                return true;
            }
        }
        return false;
    }

    private long getLootKeyValue()
    {
        long total = 0;
        for (int containerId : LOOT_KEY_CONTAINERS)
        {
            ItemContainer container = client.getItemContainer(containerId);
            if (container == null)
            {
                continue;
            }

            for (var item : container.getItems())
            {
                if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
                {
                    continue;
                }

                int price = itemManager.getItemPrice(item.getId());
                if (price > 0)
                {
                    total += (long) price * item.getQuantity();
                }
            }
        }
        return total;
    }

    private void captureAndUpload(Image image, long totalValue, String webhook)
    {
        if (image == null || image.getWidth(null) <= 0 || image.getHeight(null) <= 0)
        {
            return;
        }

        BufferedImage screenshot = new BufferedImage(
            image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
        var graphics = screenshot.createGraphics();
        try
        {
            graphics.drawImage(image, 0, 0, null);
        }
        finally
        {
            graphics.dispose();
        }

        executor.submit(() ->
        {
            try
            {
                webhookClient.send(webhook, totalValue, screenshot);
                log.debug("Sent Loot Key screenshot to Discord ({} GP)", totalValue);
            }
            catch (Exception ex)
            {
                log.warn("Unable to send Loot Key screenshot to Discord", ex);
            }
        });
    }
}
