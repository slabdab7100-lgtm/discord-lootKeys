package com.discordlootkeys;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Loot Key Discord",
	description = "Sends qualifying Wilderness Loot Key screenshots to Discord",
	tags = {"loot", "key", "pvp", "discord", "screenshot"}
)
public class LootKeyDiscordPlugin extends Plugin
{
	private static final List<Integer> LOOT_KEY_CONTAINERS = List.of(
		InventoryID.DEADMAN_LOOT_INV0,
		InventoryID.DEADMAN_LOOT_INV1,
		InventoryID.DEADMAN_LOOT_INV2,
		InventoryID.DEADMAN_LOOT_INV3,
		InventoryID.DEADMAN_LOOT_INV4
	);

	@Inject
	private Client client;

	@Inject
	private LootKeyDiscordConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private DrawManager drawManager;

	@Inject
	private ScheduledExecutorService executor;

	private final HttpClient httpClient = HttpClient.newHttpClient();
	private boolean keyInterfaceOpen;

	@Provides
	LootKeyDiscordConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LootKeyDiscordConfig.class);
	}

	@Override
	protected void startUp()
	{
		keyInterfaceOpen = false;
	}

	@Override
	protected void shutDown()
	{
		keyInterfaceOpen = false;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.WILDY_LOOT_CHEST)
		{
			return;
		}

		if (!config.enabled() || config.webhookUrl().isBlank() || keyInterfaceOpen)
		{
			return;
		}

		keyInterfaceOpen = true;
		long totalValue = calculateTotalValue();
		if (totalValue >= Math.max(0, config.minimumValue()))
		{
			captureAndSend(totalValue);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (keyInterfaceOpen && client.getWidget(InterfaceID.WILDY_LOOT_CHEST) == null)
		{
			keyInterfaceOpen = false;
		}
	}

	private long calculateTotalValue()
	{
		long total = 0;
		for (int containerId : LOOT_KEY_CONTAINERS)
		{
			ItemContainer container = client.getItemContainer(containerId);
			if (container == null)
			{
				continue;
			}
			for (net.runelite.api.Item item : container.getItems())
			{
				if (item.getId() >= 0 && item.getQuantity() > 0)
				{
					total += (long) itemManager.getItemPrice(item.getId()) * item.getQuantity();
				}
			}
		}
		return total;
	}

	private void captureAndSend(long totalValue)
	{
		drawManager.requestNextFrameListener(image ->
		{
			BufferedImage screenshot = ImageUtil.bufferedImageFromImage(image);
			executor.submit(() -> sendToDiscord(screenshot, totalValue));
		});
	}

	private void sendToDiscord(BufferedImage screenshot, long totalValue)
	{
		String webhook = config.webhookUrl().trim();
		if (webhook.isEmpty())
		{
			return;
		}
		try
		{
			byte[] imageBytes = toPng(screenshot);
			String boundary = "----LootKeyDiscord" + System.nanoTime();
			String payload = "{\"content\":\"Loot Key: " + formatNumber(totalValue) + " gp\"}";
			byte[] body = multipartBody(boundary, payload, imageBytes);
			HttpRequest request = HttpRequest.newBuilder(URI.create(webhook))
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body))
				.build();
			httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
				.thenAccept(response ->
				{
					if (response.statusCode() < 200 || response.statusCode() >= 300)
					{
						log.warn("Discord webhook returned HTTP {}", response.statusCode());
					}
				})
				.exceptionally(error ->
				{
					log.warn("Unable to send Loot Key screenshot to Discord", error);
					return null;
				});
		}
		catch (IllegalArgumentException | IOException e)
		{
			log.warn("Unable to prepare Loot Key screenshot for Discord", e);
		}
	}

	private static byte[] toPng(BufferedImage image) throws IOException
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		javax.imageio.ImageIO.write(image, "PNG", output);
		return output.toByteArray();
	}

	private static byte[] multipartBody(String boundary, String payload, byte[] image) throws IOException
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		String prefix = "--" + boundary + "\r\n";
		output.write(prefix.getBytes(StandardCharsets.UTF_8));
		output.write("Content-Disposition: form-data; name=\"payload_json\"\r\n".getBytes(StandardCharsets.UTF_8));
		output.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".getBytes(StandardCharsets.UTF_8));
		output.write(payload.getBytes(StandardCharsets.UTF_8));
		output.write("\r\n".getBytes(StandardCharsets.UTF_8));
		output.write(prefix.getBytes(StandardCharsets.UTF_8));
		output.write("Content-Disposition: form-data; name=\"files[0]\"; filename=\"loot-key.png\"\r\n".getBytes(StandardCharsets.UTF_8));
		output.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
		output.write(image);
		output.write("\r\n--".getBytes(StandardCharsets.UTF_8));
		output.write(boundary.getBytes(StandardCharsets.UTF_8));
		output.write("--\r\n".getBytes(StandardCharsets.UTF_8));
		return output.toByteArray();
	}

	private static String formatNumber(long value)
	{
		return String.format("%,d", value);
	}
}
