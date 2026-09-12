package com.discordlootkeys;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

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

    @Inject private Client client;
    @Inject private ItemManager itemManager;
    @Inject private DrawManager drawManager;
    @Inject private ScheduledExecutorService executor;
    @Inject private DiscordLootKeysConfig config;

    private boolean pendingKey;
    private boolean keyOpen;

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.WILDY_LOOT_CHEST && !keyOpen)
        {
            keyOpen = true;
            pendingKey = true;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (client.getWidget(InterfaceID.WILDY_LOOT_CHEST) == null)
        {
            keyOpen = false;
        }

        if (!pendingKey)
        {
            return;
        }

        pendingKey = false;
        if (!config.enabled())
        {
            return;
        }

        String webhook = config.webhookUrl().trim();
        if (!isDiscordWebhook(webhook))
        {
            return;
        }

        long totalValue = getLootKeyValue();
        if (totalValue < Math.max(0, config.minimumValue()))
        {
            return;
        }

        drawManager.requestNextFrameListener(image -> captureAndUpload(image, totalValue, webhook));
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
                total += (long) price * item.getQuantity();
            }
        }
        return total;
    }

    private void captureAndUpload(Image image, long totalValue, String webhook)
    {
        BufferedImage screenshot = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
        var graphics = screenshot.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();

        executor.submit(() ->
        {
            try
            {
                byte[] png = encodePng(screenshot);
                sendWebhook(webhook, totalValue, png);
                log.debug("Sent Loot Key screenshot to Discord ({} GP)", totalValue);
            }
            catch (Exception ex)
            {
                log.warn("Unable to send Loot Key screenshot to Discord", ex);
            }
        });
    }

    private static byte[] encodePng(BufferedImage image) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static void sendWebhook(String webhook, long totalValue, byte[] image) throws IOException
    {
        String boundary = "----RuneLiteLootKey" + System.nanoTime();
        URL url = URI.create(webhook + (webhook.contains("?") ? "&" : "?") + "wait=true").toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("User-Agent", "RuneLite Discord Loot Keys");

        try (OutputStream out = connection.getOutputStream())
        {
            writePart(out, boundary, "payload_json", "application/json; charset=UTF-8", "{\"content\":\"Loot Key value: " + String.format("%,d", totalValue) + " GP\"}");
            writeFile(out, boundary, image);
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300)
        {
            throw new IOException("Discord webhook returned HTTP " + status);
        }
        connection.disconnect();
    }

    private static void writePart(OutputStream out, String boundary, String name, String contentType, String value) throws IOException
    {
        String header = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"" + name + "\"\r\n"
            + "Content-Type: " + contentType + "\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFile(OutputStream out, String boundary, byte[] image) throws IOException
    {
        String header = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"files[0]\"; filename=\"loot-key.png\"\r\n"
            + "Content-Type: image/png\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(image);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isDiscordWebhook(String value)
    {
        return value.startsWith("https://discord.com/api/webhooks/")
            || value.startsWith("https://discordapp.com/api/webhooks/");
    }
}
