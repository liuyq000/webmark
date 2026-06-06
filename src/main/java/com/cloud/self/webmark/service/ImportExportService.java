package com.cloud.self.webmark.service;

import com.cloud.self.webmark.entity.Bookmark;
import com.cloud.self.webmark.entity.Folder;
import com.cloud.self.webmark.entity.User;
import com.cloud.self.webmark.utils.HtmlUtil;
import com.cloud.self.webmark.utils.HtmlUtil.BookmarkNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class ImportExportService {

    private final BookmarkService bookmarkService;
    private final FolderService folderService;
    private final FaviconService faviconService;

    public ImportExportService(BookmarkService bookmarkService, FolderService folderService, FaviconService faviconService) {
        this.bookmarkService = bookmarkService;
        this.folderService = folderService;
        this.faviconService = faviconService;
    }

    // ==================== 导入 ====================

    public Map<String, Object> importBookmarks(InputStream htmlStream, String structure, String type, User user) throws Exception {
        Map<String, Object> result = new HashMap<>();
        int savedCount;

        if ("YES".equals(structure)) {
            List<BookmarkNode> tree = HtmlUtil.parseTree(htmlStream);
            savedCount = importTree(tree, user, type);
        } else {
            Map<String, String> flat = HtmlUtil.parseFlat(htmlStream);
            savedCount = importFlat(flat, user, type);
        }

        result.put("success", true);
        result.put("message", "成功导入 " + savedCount + " 条书签");
        result.put("count", savedCount);

        if (savedCount > 0) {
            final User savedUser = user;
            new Thread(() -> batchFetchFavicons(savedUser)).start();
        }

        return result;
    }

    public int importTree(List<BookmarkNode> roots, User user, String type) {
        int total = 0;
        for (BookmarkNode root : roots) {
            if ("书签栏".equals(root.name)) {
                for (BookmarkNode child : root.children) {
                    Folder folder = createTopFolder(user.getId(), child.name);
                    total += importNode(child, folder.getId(), user, type);
                }
                if (!root.links.isEmpty()) {
                    Folder home = findHomeFolder(user.getId());
                    if (home != null) total += saveBookmarks(root.links, home.getId(), user, type);
                }
            } else {
                Folder folder = createTopFolder(user.getId(), root.name);
                total += importNode(root, folder.getId(), user, type);
            }
        }
        return total;
    }

    public int importNode(BookmarkNode node, Long parentFolderId, User user, String type) {
        int count = 0;
        if (!node.links.isEmpty()) count += saveBookmarks(node.links, parentFolderId, user, type);
        for (BookmarkNode child : node.children) {
            Folder sub = createSubFolder(user.getId(), child.name, parentFolderId);
            count += importNode(child, sub.getId(), user, type);
        }
        return count;
    }

    public int importFlat(Map<String, String> urlTitleMap, User user, String type) {
        Folder home = findHomeFolder(user.getId());
        if (home == null) return 0;
        return saveBookmarks(urlTitleMap, home.getId(), user, type);
    }

    private Folder createTopFolder(Long userId, String name) {
        Folder exist = folderService.listTreeByUserId(userId).stream()
                .filter(f -> f.getParentId() == null && name.equals(f.getName())).findFirst().orElse(null);
        if (exist != null) return exist;
        Folder f = new Folder(); f.setUserId(userId); f.setParentId(null); f.setName(name);
        f.setNameEn(""); f.setIcon(null); f.setSortOrder(99);
        f.setCreateTime(LocalDateTime.now()); f.setUpdateTime(LocalDateTime.now()); f.setDeleted(0);
        folderService.save(f); return f;
    }

    private Folder createSubFolder(Long userId, String name, Long parentId) {
        Folder exist = folderService.listTreeByUserId(userId).stream()
                .flatMap(f -> { List<Folder> list = new ArrayList<>(); list.add(f);
                    if (f.getChildren() != null) list.addAll(f.getChildren()); return list.stream(); })
                .filter(f -> name.equals(f.getName()) && parentId.equals(f.getParentId())).findFirst().orElse(null);
        if (exist != null) return exist;
        Folder f = new Folder(); f.setUserId(userId); f.setParentId(parentId); f.setName(name);
        f.setNameEn(""); f.setIcon(null); f.setSortOrder(99);
        f.setCreateTime(LocalDateTime.now()); f.setUpdateTime(LocalDateTime.now()); f.setDeleted(0);
        folderService.save(f); return f;
    }

    private Folder findHomeFolder(Long userId) {
        return folderService.listTreeByUserId(userId).stream()
                .flatMap(f -> { List<Folder> list = new ArrayList<>(); list.add(f);
                    if (f.getChildren() != null) list.addAll(f.getChildren()); return list.stream(); })
                .filter(f -> "首页".equals(f.getName())).findFirst().orElse(null);
    }

    private int saveBookmarks(Map<String, String> urlTitleMap, Long folderId, User user, String type) {
        int count = 0;
        Set<String> existingUrls = bookmarkService.listByUserId(user.getId()).stream()
                .filter(b -> folderId.equals(b.getFolderId()))
                .map(Bookmark::getUrl).collect(Collectors.toSet());
        for (Map.Entry<String, String> entry : urlTitleMap.entrySet()) {
            String url = entry.getKey(), title = entry.getValue();
            if (existingUrls.contains(url)) continue;
            Bookmark b = new Bookmark();
            b.setUserId(user.getId()); b.setFolderId(folderId);
            b.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
            b.setUrl(url); b.setDescription(""); b.setLogoUrl(""); b.setTags("");
            b.setPublicType("PRIVATE".equals(type) ? 0 : 1);
            b.setReviewStatus(1); b.setCreateTime(LocalDateTime.now());
            b.setUpdateTime(LocalDateTime.now()); b.setDeleted(0);
            bookmarkService.save(b); count++;
        }
        return count;
    }

    // ==================== 导出 ====================

    public ExportResult prepareExport(String scope, String format, String folderIds, User user) throws Exception {
        List<Bookmark> bookmarks;
        if ("private".equals(scope)) {
            bookmarks = bookmarkService.listByUserId(user.getId()).stream()
                    .filter(b -> b.getPublicType() == 0).collect(Collectors.toList());
        } else if ("public".equals(scope)) {
            bookmarks = bookmarkService.searchPublic("");
        } else {
            List<Bookmark> myBookmarks = bookmarkService.listByUserId(user.getId());
            List<Bookmark> publicBookmarks = bookmarkService.searchPublic("");
            Set<Long> seen = new HashSet<>();
            bookmarks = new ArrayList<>();
            for (Bookmark b : myBookmarks) { if (seen.add(b.getId())) bookmarks.add(b); }
            for (Bookmark b : publicBookmarks) { if (seen.add(b.getId())) bookmarks.add(b); }
        }
        if (folderIds != null && !folderIds.isEmpty() && !"all".equals(folderIds)) {
            Set<Long> fids = Arrays.stream(folderIds.split(",")).map(Long::parseLong).collect(Collectors.toSet());
            bookmarks = bookmarks.stream()
                    .filter(b -> b.getFolderId() != null && fids.contains(b.getFolderId()))
                    .collect(Collectors.toList());
        }

        String content;
        String filename;
        String contentType;

        switch (format) {
            case "json" -> {
                content = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(bookmarks);
                filename = "webmark-bookmarks.json";
                contentType = "application/json";
            }
            case "csv" -> {
                content = toCsv(bookmarks);
                filename = "webmark-bookmarks.csv";
                contentType = "text/csv";
            }
            default -> {
                List<Folder> folders = folderService.listTreeByUserId(user.getId());
                content = toHtml(bookmarks, buildFolderNameMap(folders));
                filename = "webmark-bookmarks.html";
                contentType = "text/html";
            }
        }

        return new ExportResult(content, filename, contentType);
    }

    public static class ExportResult {
        private final String content;
        private final String filename;
        private final String contentType;
        public ExportResult(String content, String filename, String contentType) {
            this.content = content; this.filename = filename; this.contentType = contentType;
        }
        public String getContent() { return content; }
        public String getFilename() { return filename; }
        public String getContentType() { return contentType; }
    }

    private Map<Long, String> buildFolderNameMap(List<Folder> folders) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (Folder f : folders) {
            map.put(f.getId(), f.getName());
            if (f.getChildren() != null) {
                for (Folder child : f.getChildren()) {
                    map.put(child.getId(), child.getName());
                    if (child.getChildren() != null)
                        for (Folder gc : child.getChildren()) map.put(gc.getId(), gc.getName());
                }
            }
        }
        return map;
    }

    private String toCsv(List<Bookmark> list) {
        StringBuilder sb = new StringBuilder("title,url,description,tags,logoUrl,publicType\n");
        for (Bookmark b : list) sb.append(csv(b.getTitle())).append(",").append(csv(b.getUrl())).append(",")
                .append(csv(b.getDescription())).append(",").append(csv(b.getTags())).append(",")
                .append(csv(b.getLogoUrl())).append(",").append(b.getPublicType()).append("\n");
        return sb.toString();
    }

    private String csv(String s) {
        if (s == null) return "";
        if (s.contains("\"") || s.contains(",") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private String toHtml(List<Bookmark> list, Map<Long, String> folderMap) {
        StringBuilder sb = new StringBuilder("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n<TITLE>云收藏 - 书签导出</TITLE>\n<H1>云收藏书签</H1>\n<DL><p>\n");
        Map<Long, List<Bookmark>> grouped = list.stream().collect(Collectors.groupingBy(b ->
                b.getFolderId() != null ? b.getFolderId() : 0L, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Long, List<Bookmark>> entry : grouped.entrySet()) {
            String folderName = folderMap.getOrDefault(entry.getKey(), "未分类");
            sb.append("    <DT><H3>").append(escHtml(folderName)).append("</H3>\n    <DL><p>\n");
            for (Bookmark b : entry.getValue())
                sb.append("        <DT><A HREF=\"").append(escHtml(b.getUrl())).append("\" ADD_DATE=\"0\">").append(escHtml(b.getTitle())).append("</A>\n");
            sb.append("    </DL><p>\n");
        }
        sb.append("</DL><p>\n");
        return sb.toString();
    }

    private String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public void batchFetchFavicons(User user) {
        try { Thread.sleep(500); } catch (InterruptedException ignored) { return; }
        try {
            List<Bookmark> bookmarks = bookmarkService.listByUserId(user.getId());
            for (Bookmark b : bookmarks) {
                if (b.getLogoUrl() != null && !b.getLogoUrl().isEmpty()) continue;
                try {
                    String logoUrl = faviconService.fetchAndSave(b.getUrl());
                    if (logoUrl != null) {
                        b.setLogoUrl(logoUrl);
                        bookmarkService.updateById(b);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            // ignored
        }
    }
}
