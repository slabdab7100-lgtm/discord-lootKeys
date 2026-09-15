package com.discordlootkeys;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.imageio.ImageIO;

public class DiscordWebhookClient
{
    private static final String DISCORD_HOST = "discord.com";
    private static final String LEGACY_DISCORD_HOST = "discordapp.com";
    private static final String WEBHOOK_PATH = "/api/webhooks/";

    public boolean isValidWebhook(String value)
    {
        try
        {
            URI uri = URI.create(value);
            String host = uri.getHost();
            String path = uri.getPath();
            return "https".equalsIgnoreCase(uri.getScheme())
                && (DISCORD_HOST.equalsIgnoreCase(host) || LEGACY_DISCORD_HOST.equalsIgnoreCase(host))
                && uri.getPort() == -1
                && uri.getUserInfo() == null
                && uri.getFragment() == null
                && path != null
                && path.startsWith(WEBHOOK_PATH);
        }
        catch (IllegalArgumentException ex)
        {
            return false;
        }
    }

    public void send(String webhook, long totalValue, BufferedImage screenshot) throws IOException
    {
        byte[] image = encodePng(screenshot);
        String boundary = "----RuneLiteLootKey" + System.nanoTime();
        URL url = URI.create(webhook).toURL();
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
                    "{\"content\":\"Loot Key value: " + String.format(Locale.US, "%,d", totalValue) + " GP\"}");
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
        if (!ImageIO.write(image, "png", out))
        {
            throw new IOException("No PNG image writer available");
        }
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
