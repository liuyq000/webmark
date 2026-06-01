package com.cloud.self.webmark.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FaviconService {

    private static final Logger log = LoggerFactory.getLogger(FaviconService.class);
    private final Path faviconDir;

    public FaviconService(@Value("${webmark.data.dir:./data}") String dataDir) {
        this.faviconDir = Paths.get(dataDir, "favicons");
        try {
            Files.createDirectories(faviconDir);
        } catch (IOException e) {
            log.debug("Failed to create favicon directory", e);
        }
    }

    /**
     * 异步抓取网站 favicon，保存到 data/favicons/，返回本地路径 /favicons/xxx.png
     * @return 本地 favicon 路径，失败返回 null
     */
    public String fetchAndSave(String pageUrl) {
        try {
            URL url = new URL(pageUrl);
            String domain = url.getHost();

            // 1. 先检查是否已缓存（同域名复用）
            String existing = findCached(domain);
            if (existing != null) return existing;

            // 2. 尝试直接下载 favicon.ico
            String directUrl = url.getProtocol() + "://" + domain + "/favicon.ico";
            byte[] iconData = downloadFavicon(directUrl);
            String ext = "ico";

            // 3. 如果直接下载失败，Jsoup 解析页面找 <link rel="icon">
            if (iconData == null) {
                String faviconUrl = extractFaviconFromPage(pageUrl);
                if (faviconUrl != null) {
                    iconData = downloadFavicon(faviconUrl);
                    ext = getExtension(faviconUrl);
                }
            }

            if (iconData == null || iconData.length == 0) return null;

            // 4. 保存到 data/favicons/
            String filename = domain.replaceAll("[^a-zA-Z0-9.-]", "_") + "." + ext;
            Path dest = faviconDir.resolve(filename);
            Files.write(dest, iconData);

            return "/favicons/" + filename;

        } catch (Exception e) {
            log.debug("Favicon fetch failed for {}: {}", pageUrl, e.getMessage());
            return null;
        }
    }

    /** 检查域名是否已有缓存 */
    private String findCached(String domain) {
        try {
            for (String file : faviconDir.toFile().list()) {
                if (file.startsWith(domain.replaceAll("[^a-zA-Z0-9.-]", "_") + ".")) {
                    return "/favicons/" + file;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** HTTP GET 下载 favicon 二进制 */
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

    /** Jsoup 解析页面找 favicon 链接 */
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
