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

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final BookmarkService bookmarkService;
    private final FolderService folderService;
    private final UserService userService;
    private final FavoritesService favoritesService;
    private final UrlLibraryService urlLibraryService;

    @GetMapping({"", "/index"})
    public String dashboard() {
        return "redirect:/admin/bookmark/list";
    }

    @GetMapping("/bookmark/list")
    public String bookmarkList(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userDetails != null ? userService.findByUserName(userDetails.getUsername()) : null;
        model.addAttribute("folderTree", user != null ? folderService.listTreeByUserId(user.getId()) : folderService.listPublicTree());
        return "admin/bookmark/list";
    }

    @GetMapping("/addbookmark")
    public String bookmarkCreate(@RequestParam(required = false) Long folderId,
                                 @RequestParam(required = false) String url,
                                 @RequestParam(required = false) String title,
                                 @RequestParam(required = false) String description,
                                 @AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userDetails != null ? userService.findByUserName(userDetails.getUsername()) : null;
        model.addAttribute("folderTree", user != null ? folderService.listTreeByUserId(user.getId()) : folderService.listPublicTree());
        model.addAttribute("bookmark", null);
        model.addAttribute("selectedFolderId", folderId);
        // bookmarklet 预填参数
        model.addAttribute("prefillUrl", url);
        model.addAttribute("prefillTitle", title);
        model.addAttribute("prefillDescription", description);
        return "admin/bookmark/editbookmark";
    }

    @GetMapping("/bookmark/edit/{id}")
    public String bookmarkEdit(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userDetails != null ? userService.findByUserName(userDetails.getUsername()) : null;
        model.addAttribute("folderTree", user != null ? folderService.listTreeByUserId(user.getId()) : folderService.listPublicTree());
        model.addAttribute("bookmark", bookmarkService.getById(id));
        return "admin/bookmark/editbookmark";
    }

    @GetMapping("/folder/list")
    public String folderList(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userDetails != null ? userService.findByUserName(userDetails.getUsername()) : null;
        model.addAttribute("folderTree", user != null ? folderService.listTreeByUserId(user.getId()) : folderService.listPublicTree());
        return "admin/folder/list";
    }

    @GetMapping("/user/list")
    public String userList(@RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "10") int size,
                           @RequestParam(required = false) String keyword,
                           Model model) {
        PageResult<User> result = userService.pageList(page, size, keyword);
        model.addAttribute("page", result);
        model.addAttribute("keyword", keyword);
        return "admin/user/list";
    }

    @GetMapping("/favorites/list")
    public String favoritesList(@RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "10") int size,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(required = false) Long userId,
                                Model model) {
        PageResult<Favorites> result = favoritesService.adminPage(page, size, keyword, userId);
        model.addAttribute("page", result);
        model.addAttribute("keyword", keyword);
        return "admin/favorites/list";
    }

    @GetMapping("/urlLibrary/list")
    public String urlLibraryList(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(required = false) String keyword,
                                  Model model) {
        PageResult<UrlLibrary> result = urlLibraryService.adminPage(page, size, keyword);
        model.addAttribute("page", result);
        model.addAttribute("keyword", keyword);
        return "admin/urlLibrary/list";
    }

    @GetMapping("/config")
    public String config(Model model) {
        return "admin/config";
    }

    @GetMapping("/tool/collect")
    public String collectPage(HttpServletRequest request, Model model) {
        // 使用当前请求的协议+域名+端口，确保 bookmarklet 打开的地址与当前 session 的 cookie 域一致
        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort()) + "/";
        String path="javascript:(function()%7Bvar%20description;var%20desString=%22%22;var%20metas=document.getElementsByTagName('meta');for(var%20x=0,y=metas.length;x%3Cy;x++)%7Bif(metas%5Bx%5D.name.toLowerCase()==%22description%22)%7Bdescription=metas%5Bx%5D;%7D%7Dif(description)%7BdesString=%22&amp;description=%22+encodeURIComponent(description.content);%7Dvar%20win=window.open(%22"
                + baseUrl
                +"admin/addbookmark?from=webtool&url=%22+encodeURIComponent(document.URL)+desString+%22&title=%22+encodeURIComponent(document.title)+%22&charset=%22+document.charset,'_blank');win.focus();%7D)();";
        model.addAttribute("path",path);

        return "admin/tool/collect";
    }

    @GetMapping("/icon/list")
    public String iconList() {
        return "admin/icon/list";
    }


    @GetMapping("/tool/import")
    public String importPage() {
        return "admin/tool/import";
    }

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
                // 按目录结构导入（树形）
                List<BookmarkNode> tree = HtmlUtil.parseTree(htmlFile.getInputStream());
                savedCount = importTree(tree, user, type);
            } else {
                // 平铺导入 → 全部放到"未读列表"
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

    // ==================== 按目录结构导入 ====================

    private int importTree(List<BookmarkNode> roots, User user, String type) {
        int total = 0;
        for (BookmarkNode root : roots) {
            if ("书签栏".equals(root.name)) {
                // 书签栏的子文件夹 → 一级文件夹
                for (BookmarkNode child : root.children) {
                    Folder folder = createTopFolder(user.getId(), child.name);
                    total += importNode(child, folder.getId(), user, type);
                }
                // 书签栏的直接链接 → 首页
                if (!root.links.isEmpty()) {
                    Folder home = findHomeFolder(user.getId());
                    if (home != null) {
                        total += saveBookmarks(root.links, home.getId(), user, type);
                    }
                }
            } else {
                // 其他根目录 → 一级文件夹
                Folder folder = createTopFolder(user.getId(), root.name);
                total += importNode(root, folder.getId(), user, type);
            }
        }
        return total;
    }

    /** 递归导入一个节点及其子节点 */
    private int importNode(BookmarkNode node, Long parentFolderId, User user, String type) {
        int count = 0;
        // 本节点的书签
        if (!node.links.isEmpty()) {
            count += saveBookmarks(node.links, parentFolderId, user, type);
        }
        // 子文件夹
        for (BookmarkNode child : node.children) {
            Folder sub = createSubFolder(user.getId(), child.name, parentFolderId);
            count += importNode(child, sub.getId(), user, type);
        }
        return count;
    }

    // ==================== 平铺导入 ====================

    private int importFlat(Map<String, String> urlTitleMap, User user, String type) {
        Folder home = findHomeFolder(user.getId());
        if (home == null) return 0;
        return saveBookmarks(urlTitleMap, home.getId(), user, type);
    }

    // ==================== 文件夹查询/创建 ====================

    /** 查找或创建顶级文件夹 */
    private Folder createTopFolder(Long userId, String name) {
        Folder exist = folderService.listTreeByUserId(userId).stream()
                .filter(f -> f.getParentId() == null && name.equals(f.getName()))
                .findFirst().orElse(null);
        if (exist != null) return exist;

        Folder f = new Folder();
        f.setUserId(userId);
        f.setParentId(null);
        f.setName(name);
        f.setNameEn("");
        f.setIcon(null);
        f.setSortOrder(99);
        f.setCreateTime(LocalDateTime.now());
        f.setUpdateTime(LocalDateTime.now());
        f.setDeleted(0);
        folderService.save(f);
        return f;
    }

    /** 查找或创建子文件夹 */
    private Folder createSubFolder(Long userId, String name, Long parentId) {
        Folder exist = folderService.listTreeByUserId(userId).stream()
                .flatMap(f -> {
                    List<Folder> list = new ArrayList<>();
                    list.add(f);
                    if (f.getChildren() != null) list.addAll(f.getChildren());
                    return list.stream();
                })
                .filter(f -> name.equals(f.getName()) && parentId.equals(f.getParentId()))
                .findFirst().orElse(null);
        if (exist != null) return exist;

        Folder f = new Folder();
        f.setUserId(userId);
        f.setParentId(parentId);
        f.setName(name);
        f.setNameEn("");
        f.setIcon(null);
        f.setSortOrder(99);
        f.setCreateTime(LocalDateTime.now());
        f.setUpdateTime(LocalDateTime.now());
        f.setDeleted(0);
        folderService.save(f);
        return f;
    }

    /** 查找"首页"文件夹 */
    private Folder findHomeFolder(Long userId) {
        return folderService.listTreeByUserId(userId).stream()
                .flatMap(f -> {
                    List<Folder> list = new ArrayList<>();
                    list.add(f);
                    if (f.getChildren() != null) list.addAll(f.getChildren());
                    return list.stream();
                })
                .filter(f -> "首页".equals(f.getName()))
                .findFirst().orElse(null);
    }

    // ==================== 保存书签（去重） ====================

    private int saveBookmarks(Map<String, String> urlTitleMap, Long folderId, User user, String type) {
        int count = 0;
        Set<String> existingUrls = bookmarkService.listByUserId(user.getId()).stream()
                .filter(b -> folderId.equals(b.getFolderId()))
                .map(Bookmark::getUrl)
                .collect(Collectors.toSet());

        for (Map.Entry<String, String> entry : urlTitleMap.entrySet()) {
            String url = entry.getKey();
            String title = entry.getValue();
            if (existingUrls.contains(url)) continue;

            Bookmark b = new Bookmark();
            b.setUserId(user.getId());
            b.setFolderId(folderId);
            b.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
            b.setUrl(url);
            b.setDescription("");
            b.setLogoUrl("");
            b.setTags("");
            b.setViewCount(0);
            b.setCollectCount(0);
            b.setPublicType("PRIVATE".equals(type) ? 0 : 1);
            b.setReviewStatus(1);
            b.setCreateTime(LocalDateTime.now());
            b.setUpdateTime(LocalDateTime.now());
            b.setDeleted(0);
            bookmarkService.save(b);
            count++;
        }
        return count;
    }

    @GetMapping("/tool/export")
    public String exportPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userDetails != null ? userService.findByUserName(userDetails.getUsername()) : null;
        model.addAttribute("folderTree", user != null ? folderService.listTreeByUserId(user.getId()) : folderService.listPublicTree());
        return "admin/tool/export";
    }

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
                    .filter(b -> b.getPublicType() == 0)
                    .collect(Collectors.toList());
        } else if ("public".equals(scope)) {
            bookmarks = bookmarkService.searchPublic("");
        } else {
            List<Bookmark> myBookmarks = bookmarkService.listByUserId(user.getId());
            List<Bookmark> publicBookmarks = bookmarkService.searchPublic("");
            Set<Long> seen = new HashSet<>();
            bookmarks = new ArrayList<>();
            for (Bookmark b : myBookmarks) {
                if (seen.add(b.getId())) bookmarks.add(b);
            }
            for (Bookmark b : publicBookmarks) {
                if (seen.add(b.getId())) bookmarks.add(b);
            }
        }

        // 按指定文件夹过滤
        if (folderIds != null && !folderIds.isEmpty() && !"all".equals(folderIds)) {
            Set<Long> fids = Arrays.stream(folderIds.split(","))
                    .map(Long::parseLong).collect(Collectors.toSet());
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
                filename = "webmark-bookmarks.json";
                contentType = MediaType.APPLICATION_JSON;
            }
            case "csv" -> {
                content = toCsv(bookmarks);
                filename = "webmark-bookmarks.csv";
                contentType = new MediaType("text", "csv");
            }
            default -> {
                List<Folder> folders = folderService.listTreeByUserId(user.getId());
                content = toHtml(bookmarks, buildFolderNameMap(folders));
                filename = "webmark-bookmarks.html";
                contentType = MediaType.TEXT_HTML;
            }
        }

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + URLEncoder.encode(filename, StandardCharsets.UTF_8) + "\"")
                .contentType(contentType)
                .body(bytes);
    }

    private Map<Long, String> buildFolderNameMap(List<Folder> folders) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (Folder f : folders) {
            map.put(f.getId(), f.getName());
            if (f.getChildren() != null) {
                for (Folder child : f.getChildren()) {
                    map.put(child.getId(), child.getName());
                    if (child.getChildren() != null) {
                        for (Folder gc : child.getChildren()) {
                            map.put(gc.getId(), gc.getName());
                        }
                    }
                }
            }
        }
        return map;
    }

    private String toCsv(List<Bookmark> list) {
        StringBuilder sb = new StringBuilder("title,url,description,tags,logoUrl,publicType\n");
        for (Bookmark b : list) {
            sb.append(csv(b.getTitle())).append(",")
              .append(csv(b.getUrl())).append(",")
              .append(csv(b.getDescription())).append(",")
              .append(csv(b.getTags())).append(",")
              .append(csv(b.getLogoUrl())).append(",")
              .append(b.getPublicType()).append("\n");
        }
        return sb.toString();
    }

    private String csv(String s) {
        if (s == null) return "";
        if (s.contains("\"") || s.contains(",") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private String toHtml(List<Bookmark> list, Map<Long, String> folderMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n");
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n");
        sb.append("<TITLE>本地书签 - 书签导出</TITLE>\n");
        sb.append("<H1>本地书签书签</H1>\n");
        sb.append("<DL><p>\n");

        Map<Long, List<Bookmark>> grouped = list.stream()
                .collect(Collectors.groupingBy(b ->
                        b.getFolderId() != null ? b.getFolderId() : 0L, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<Long, List<Bookmark>> entry : grouped.entrySet()) {
            String folderName = folderMap.getOrDefault(entry.getKey(), "未分类");
            sb.append("    <DT><H3>").append(escHtml(folderName)).append("</H3>\n");
            sb.append("    <DL><p>\n");
            for (Bookmark b : entry.getValue()) {
                sb.append("        <DT><A HREF=\"").append(escHtml(b.getUrl()))
                  .append("\" ADD_DATE=\"0\">")
                  .append(escHtml(b.getTitle())).append("</A>\n");
            }
            sb.append("    </DL><p>\n");
        }
        sb.append("</DL><p>\n");
        return sb.toString();
    }

    private String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
