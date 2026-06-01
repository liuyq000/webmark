package com.cloud.self.webmark.controller;

import com.cloud.self.webmark.entity.Bookmark;
import com.cloud.self.webmark.entity.Folder;
import com.cloud.self.webmark.entity.User;
import com.cloud.self.webmark.service.BookmarkService;
import com.cloud.self.webmark.service.FaviconService;
import com.cloud.self.webmark.service.FolderService;
import com.cloud.self.webmark.service.UserService;
import com.cloud.self.webmark.store.PageResult;
import com.cloud.self.webmark.utils.HtmlUtil;
import com.cloud.self.webmark.utils.HtmlUtil.BookmarkNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
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
    private final FaviconService faviconService;
    private final PasswordEncoder passwordEncoder;
    // ========== 页面路由统一重定向到 /index ==========

    @GetMapping({"", "/index", "/bookmark/edit/**", "/folder/list",
            "/user/list", "/favorites/list", "/urlLibrary/list", "/config",
            "/tool/collect", "/tool/import", "/tool/export", "/icon/list"})
    public String redirectToIndex() {
        return "redirect:/index";
    }

    // ========== 保留的 API 功能 ==========

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
            if (savedCount > 0) {
                final User savedUser = user;
                new Thread(() -> batchFetchFavicons(savedUser)).start();
            }
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

    // ========== 用户管理 API ==========

    @GetMapping("/api/user/list")
    @ResponseBody
    public Map<String, Object> listUsers(@RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String keyword) {
        Map<String, Object> result = new HashMap<>();
        try {
            PageResult<User> page = userService.pageList(pageNum, pageSize, keyword);
            List<Map<String, Object>> list = page.getRecords().stream().map(u -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", u.getId());
                m.put("userName", u.getUserName());
                m.put("email", u.getEmail());
                m.put("role", u.getRole());
                m.put("introduction", u.getIntroduction());
                m.put("createTime", u.getCreateTime() != null ? u.getCreateTime().toString().substring(0, 19) : "");
                return m;
            }).collect(java.util.stream.Collectors.toList());
            result.put("success", true);
            Map<String, Object> data = new HashMap<>();
            data.put("list", list);
            data.put("total", page.getTotal());
            result.put("data", data);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/api/user/detail")
    @ResponseBody
    public Map<String, Object> userDetail(@RequestParam Long id) {
        Map<String, Object> result = new HashMap<>();
        User user = userService.getById(id);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("userName", user.getUserName());
        data.put("email", user.getEmail());
        data.put("role", user.getRole());
        data.put("introduction", user.getIntroduction());
        result.put("success", true);
        result.put("data", data);
        return result;
    }

    @PostMapping("/api/user/save")
    @ResponseBody
    public Map<String, Object> saveUser(@RequestBody Map<String, String> body,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        Map<String, Object> result = new HashMap<>();
        try {
            String idStr = body.get("id");
            String userName = body.getOrDefault("userName", "").trim();
            String password = body.getOrDefault("password", "").trim();
            String email = body.getOrDefault("email", "").trim();
            String role = body.getOrDefault("role", "ROLE_USER");
            String introduction = body.getOrDefault("introduction", "");

            if (userName.isEmpty()) {
                result.put("success", false);
                result.put("message", "用户名不能为空");
                return result;
            }

            if (idStr == null || idStr.isEmpty()) {
                // 新建
                if (password.isEmpty()) {
                    result.put("success", false);
                    result.put("message", "密码不能为空");
                    return result;
                }
                if (userService.findByUserName(userName) != null) {
                    result.put("success", false);
                    result.put("message", "用户名已存在");
                    return result;
                }
                User u = new User();
                u.setUserName(userName);
                u.setPassword(passwordEncoder.encode(password));
                u.setEmail(email);
                u.setRole(role);
                u.setIntroduction(introduction);
                userService.save(u);
                // 为新用户创建默认文件夹
                folderService.createDefaultFolders(u.getId());
            } else {
                // 编辑
                User u = userService.getById(Long.valueOf(idStr));
                if (u == null) {
                    result.put("success", false);
                    result.put("message", "用户不存在");
                    return result;
                }
                if (!u.getUserName().equals(userName) && userService.findByUserName(userName) != null) {
                    result.put("success", false);
                    result.put("message", "用户名已存在");
                    return result;
                }
                u.setUserName(userName);
                if (!password.isEmpty()) {
                    u.setPassword(passwordEncoder.encode(password));
                }
                u.setEmail(email);
                u.setRole(role);
                u.setIntroduction(introduction);
                u.setUpdateTime(LocalDateTime.now());
                userService.updateById(u);
            }
            result.put("success", true);
            result.put("message", "保存成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/api/user/delete")
    @ResponseBody
    public Map<String, Object> deleteUser(@RequestParam Long id,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        Map<String, Object> result = new HashMap<>();
        // 不允许删除自己
        User current = userService.findByUserName(userDetails.getUsername());
        if (current != null && current.getId().equals(id)) {
            result.put("success", false);
            result.put("message", "不能删除当前登录用户");
            return result;
        }
        boolean ok = userService.removeById(id);
        result.put("success", ok);
        result.put("message", ok ? "删除成功" : "删除失败");
        return result;
    }

    // ========== favicon 批量抓取 ==========

    /** 后台批量抓取用户所有无图标的书签 favicon */
    private void batchFetchFavicons(User user) {
        try {
            Thread.sleep(500); // 等待导入写入完成
        } catch (InterruptedException ignored) { return; }
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
            log.debug("批量抓取 favicon 异常: {}", e.getMessage());
        }
    }
}
