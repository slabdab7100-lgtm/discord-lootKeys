package com.discordlootkeys;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

public class DiscordWebhookClient
{
    private static final String DISCORD_WEBHOOK_PREFIX = "https://discord.com/api/webhooks/";
    private static final String LEGACY_DISCORD_WEBHOOK_PREFIX = "https://discordapp.com/api/webhooks/";

    public boolean isValidWebhook(String value)
    {
        return value.startsWith(DISCORD_WEBHOOK_PREFIX) || value.startsWith(LEGACY_DISCORD_WEBHOOK_PREFIX);
    }

    public void send(String webhook, long totalValue, BufferedImage screenshot) throws IOException
    {
        byte[] image = encodePng(screenshot);
        String boundary = "----RuneLiteLootKey" + System.nanoTime();
        URL url = URI.create(webhook + (webhook.contains("?") ? "&" : "?") + "wait=true").toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("User-Agent", "RuneLite Discord Loot Keys");

        try
        {
            try (OutputStream out = connection.getOutputStream())
            {
                writePart(out, boundary, "payload_json", "application/json; charset=UTF-8",
                    "{\"content\":\"Loot Key value: " + String.format("%,d", totalValue) + " GP\"}");
                writeFile(out, boundary, image);
                out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300)
            {
                throw new IOException("Discord webhook returned HTTP " + status);
            }
        }
        finally
        {
            connection.disconnect();
        }
    }

    private static byte[] encodePng(BufferedImage image) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
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
}
