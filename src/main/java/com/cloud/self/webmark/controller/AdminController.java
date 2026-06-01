package com.cloud.self.webmark.controller;

import com.cloud.self.webmark.store.PageResult;
import com.cloud.self.webmark.entity.*;
import com.cloud.self.webmark.service.*;
import com.cloud.self.webmark.utils.HtmlUtil;
import com.cloud.self.webmark.utils.HtmlUtil.BookmarkNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理功能：仅保留页面跳转到 /index，API 功能不变
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final BookmarkService bookmarkService;
    private final FolderService folderService;
    private final UserService userService;
    // ========== 页面路由统一重定向到 /index（图标管理除外） ==========

    @GetMapping({"", "/index", "/bookmark/edit/**", "/folder/list",
            "/user/list", "/favorites/list", "/urlLibrary/list", "/config",
            "/tool/collect", "/tool/import", "/tool/export"})
    public String redirectToIndex() {
        return "redirect:/index";
    }

    @GetMapping("/icon/list")
    public String iconList() {
        return "admin/icon/list";
    }

    // ========== 保留的 API 功能 ==========

    /** bookmarklet 打开的新建书签页面（独立窗口） */
    @GetMapping("/addbookmark")
    public String bookmarkCreate(@RequestParam(required = false) Long folderId,
                                 @RequestParam(required = false) String url,
                                 @RequestParam(required = false) String title,
                                 @RequestParam(required = false) String description,
                                 @AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userDetails != null ? userService.findByUserName(userDetails.getUsername()) : null;
        model.addAttribute("folderTree", user != null ? folderService.listTreeByUserId(user.getId()) : folderService.listPublicTree());
        model.addAttribute("bookmark", null);
        model.addAttribute("selectedFolderId", folderId != null ? folderId : 40);
        model.addAttribute("prefillUrl", url);
        model.addAttribute("prefillTitle", title);
        model.addAttribute("prefillDescription", description);
        return "admin/bookmark/editbookmark";
    }

    /** 导入书签 */
    @PostMapping("/tool/import")
    @ResponseBody
    public Map<String, Object> importBookmarks(
            @RequestParam("htmlFile") MultipartFile htmlFile,
            @RequestParam(required = false) String structure,
            @RequestParam(required = false) String type,
            @AuthenticationPrincipal UserDetails userDetails) {
        Map<String, Object> result = new HashMap<>();
        User user = userService.findByUserName(userDetails.getUsername());
        try {
            int savedCount;
            if ("YES".equals(structure)) {
                List<BookmarkNode> tree = HtmlUtil.parseTree(htmlFile.getInputStream());
                savedCount = importTree(tree, user, type);
            } else {
                Map<String, String> flat = HtmlUtil.parseFlat(htmlFile.getInputStream());
                savedCount = importFlat(flat, user, type);
            }
            result.put("success", true);
            result.put("message", "成功导入 " + savedCount + " 条书签");
            result.put("count", savedCount);
        } catch (Exception e) {
            log.debug("书签导入失败", e);
            result.put("success", false);
            result.put("message", "导入失败：" + e.getMessage());
        }
        return result;
    }

    /** 导出书签下载 */
    @GetMapping("/tool/export/download")
    public ResponseEntity<byte[]> exportDownload(
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(defaultValue = "html") String format,
            @RequestParam(required = false) String folderIds,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {
        User user = userService.findByUserName(userDetails.getUsername());
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
        MediaType contentType;
        switch (format) {
            case "json" -> {
                content = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(bookmarks);
                filename = "webmark-bookmarks.json"; contentType = MediaType.APPLICATION_JSON;
            }
            case "csv" -> {
                content = toCsv(bookmarks);
                filename = "webmark-bookmarks.csv"; contentType = new MediaType("text", "csv");
            }
            default -> {
                List<Folder> folders = folderService.listTreeByUserId(user.getId());
                content = toHtml(bookmarks, buildFolderNameMap(folders));
                filename = "webmark-bookmarks.html"; contentType = MediaType.TEXT_HTML;
            }
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + URLEncoder.encode(filename, StandardCharsets.UTF_8) + "\"")
                .contentType(contentType).body(bytes);
    }

    // ========== 导入逻辑（保持不变） ==========

    private int importTree(List<BookmarkNode> roots, User user, String type) {
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

    private int importNode(BookmarkNode node, Long parentFolderId, User user, String type) {
        int count = 0;
        if (!node.links.isEmpty()) count += saveBookmarks(node.links, parentFolderId, user, type);
        for (BookmarkNode child : node.children) {
            Folder sub = createSubFolder(user.getId(), child.name, parentFolderId);
            count += importNode(child, sub.getId(), user, type);
        }
        return count;
    }

    private int importFlat(Map<String, String> urlTitleMap, User user, String type) {
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

    // ========== 导出工具方法 ==========

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
}
