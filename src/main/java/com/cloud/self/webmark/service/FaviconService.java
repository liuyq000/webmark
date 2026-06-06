package com.cloud.self.webmark.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FaviconService {

    private static final Logger log = LoggerFactory.getLogger(FaviconService.class);
    private final Path faviconDir;

    public FaviconService(String dataDir) {
        this.faviconDir = Paths.get(dataDir, "favicons");
        try {
            Files.createDirectories(faviconDir);
        } catch (IOException e) {
            log.debug("Failed to create favicon directory", e);
        }
    }

    public String fetchAndSave(String pageUrl) {
        try {
            URL url = new URL(pageUrl);
            String domain = url.getHost();

            String existing = findCached(domain);
            if (existing != null) return existing;

            String directUrl = url.getProtocol() + "://" + domain + "/favicon.ico";
            byte[] iconData = downloadFavicon(directUrl);
            String ext = "ico";

            if (iconData == null) {
                String faviconUrl = extractFaviconFromPage(pageUrl);
                if (faviconUrl != null) {
                    iconData = downloadFavicon(faviconUrl);
                    ext = getExtension(faviconUrl);
                }
            }

            if (iconData == null || iconData.length == 0) return null;

            String filename = domain.replaceAll("[^a-zA-Z0-9.-]", "_") + "." + ext;
            Path dest = faviconDir.resolve(filename);
            Files.write(dest, iconData);

            return "/favicons/" + filename;

        } catch (Exception e) {
            log.debug("Favicon fetch failed for {}: {}", pageUrl, e.getMessage());
            return null;
        }
    }

    private String findCached(String domain) {
        try {
            String[] files = faviconDir.toFile().list();
            if (files == null) return null;
            String prefix = domain.replaceAll("[^a-zA-Z0-9.-]", "_") + ".";
            for (String file : files) {
                if (file.startsWith(prefix)) {
                    return "/favicons/" + file;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private byte[] downloadFavicon(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code != 200) return null;

            try (InputStream in = conn.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractFaviconFromPage(String pageUrl) {
        try {
            Document doc = Jsoup.connect(pageUrl)
                    .timeout(3000)
                    .userAgent("Mozilla/5.0")
                    .get();
            Element link = doc.selectFirst("link[rel~=icon]");
            if (link == null) link = doc.selectFirst("link[rel~=shortcut]");
            if (link != null) {
                String href = link.attr("href");
                URL base = new URL(pageUrl);
                if (href.startsWith("//")) {
                    return base.getProtocol() + ":" + href;
                } else if (href.startsWith("/")) {
                    return base.getProtocol() + "://" + base.getHost() + href;
                } else if (href.startsWith("http")) {
                    return href;
                } else {
                    return base.getProtocol() + "://" + base.getHost() + "/" + href;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getExtension(String url) {
        String path = url.contains("?") ? url.substring(0, url.indexOf("?")) : url;
        int dot = path.lastIndexOf('.');
        if (dot > 0 && dot < path.length() - 1) {
            String ext = path.substring(dot + 1).toLowerCase();
            if (ext.matches("^(ico|png|svg|jpg|jpeg|gif|webp)$")) return ext;
        }
        return "ico";
    }
}
