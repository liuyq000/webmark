package com.cloud.self.webmark;

import com.cloud.self.webmark.config.DataStore;
import com.cloud.self.webmark.entity.Bookmark;
import com.cloud.self.webmark.entity.Folder;
import com.cloud.self.webmark.entity.User;
import com.cloud.self.webmark.middleware.JwtAuthMiddleware;
import com.cloud.self.webmark.security.JwtUtil;
import com.cloud.self.webmark.service.*;
import com.cloud.self.webmark.service.ImportExportService.ExportResult;
import com.cloud.self.webmark.store.PageResult;
import io.javalin.Javalin;
import io.javalin.http.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class WebmarkApp {

    private static final Logger log = LoggerFactory.getLogger(WebmarkApp.class);

    public static void main(String[] args) {
        // ==================== 初始化 ====================
        String dataDir = System.getenv().getOrDefault("WEBMARK_DATA_DIR", "./data");
        boolean prettyPrint = !"false".equals(System.getenv().getOrDefault("WEBMARK_JSON_PRETTY", "true"));
        int port = Integer.parseInt(System.getenv().getOrDefault("WEBMARK_PORT", "8888"));

        var dataStore = new DataStore(dataDir, prettyPrint);
        dataStore.init();

        var bookmarkService = new BookmarkService(dataStore);
        var folderService = new FolderService(dataStore);
        var userService = new UserService(dataStore);
        var faviconService = new FaviconService(dataDir);
        var cssEditorService = new CssEditorService();
        var importExportService = new ImportExportService(bookmarkService, folderService, faviconService);
        var jwtUtil = new JwtUtil(
                System.getenv().getOrDefault("WEBMARK_JWT_SECRET", "webmark-jwt-secret-key-2024-min-32bytes!!"),
                1800000, 604800000
        );

        // ==================== Javalin ====================
        Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.directory = "/static";
                staticFiles.hostedPath = "/";
            });
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));

            // ===== Favicon 文件服务（外部目录） =====
            config.routes.get("/favicons/{name}", ctx -> {
                String name = ctx.pathParam("name");
                if (name == null || name.contains("..") || name.contains("/") || name.contains("\\")) {
                    ctx.status(404);
                    return;
                }
                java.nio.file.Path file = java.nio.file.Paths.get(dataDir, "favicons", name);
                if (java.nio.file.Files.exists(file)) {
                    ctx.contentType(guessMime(name));
                    ctx.result(java.nio.file.Files.readAllBytes(file));
                } else {
                    ctx.status(404);
                }
            });

            // ===== 页面路由 =====
            config.routes.get("/", ctx -> ctx.redirect("/index.html"));
            config.routes.get("/index", ctx -> ctx.redirect("/index.html"));
            config.routes.get("/login", ctx -> ctx.redirect("/login.html"));
            config.routes.get("/register", ctx -> ctx.redirect("/register.html"));
            config.routes.get("/addbookmark", ctx -> ctx.redirect("/addbookmark.html"));
            config.routes.get("/lookAround", ctx -> ctx.redirect("/index.html"));
            config.routes.get("/search", ctx -> ctx.redirect("/search.html"));

            // ===== JWT 中间件（保护需要登录的 API） =====
            config.routes.beforeMatched(ctx -> {
                if (ctx.path().startsWith("/api/admin/") || ctx.path().startsWith("/admin/api/")
                        || ctx.path().startsWith("/admin/tool/")
                        || ctx.path().startsWith("/api/folders") && !"GET".equals(ctx.method())
                        || ctx.path().equals("/api/home-bookmarks")) {
                    JwtAuthMiddleware.handle(ctx, jwtUtil, userService);
                }
            });

            // ===== 登出 =====
            config.routes.post("/logout", ctx -> {
                ctx.redirect("/index.html");
            });

            // ===== 注册 =====
            config.routes.post("/register", ctx -> {
                String userName = ctx.formParam("userName");
                String password = ctx.formParam("password");
                String email = ctx.formParam("email");
                if (userName == null || password == null) {
                    ctx.redirect("/register.html?error=missing");
                    return;
                }
                if (userService.findByUserName(userName) != null) {
                    ctx.redirect("/register.html?error=exists");
                    return;
                }
                if (email != null && !email.isEmpty() && userService.findByEmail(email) != null) {
                    ctx.redirect("/register.html?error=email_exists");
                    return;
                }
                User u = new User();
                u.setUserName(userName);
                u.setPassword(PasswordUtil.encode(password));
                u.setEmail(email != null ? email : "");
                u.setRole("ROLE_USER");
                userService.save(u);
                folderService.createDefaultFolders(u.getId());
                ctx.redirect("/login.html?registered=true");
            });

            // ===== 认证 API =====
            config.routes.post("/api/auth/login", ctx -> {
                var body = ctx.bodyAsClass(Map.class);
                String username = (String) body.get("username");
                String password = (String) body.get("password");
                User user = userService.findByUserName(username);
                if (user == null || !PasswordUtil.matches(password, user.getPassword())) {
                    ctx.status(401).json(Map.of("success", false, "message", "用户名或密码错误"));
                    return;
                }
                ctx.json(Map.of(
                        "success", true,
                        "token", jwtUtil.generateAccessToken(user.getUserName(), user.getRole()),
                        "refreshToken", jwtUtil.generateRefreshToken(user.getUserName(), user.getRole()),
                        "username", user.getUserName(),
                        "role", user.getRole()
                ));
            });

            config.routes.post("/api/auth/refresh", ctx -> {
                var body = ctx.bodyAsClass(Map.class);
                String refreshToken = (String) body.get("refreshToken");
                if (refreshToken == null || !"refresh".equals(jwtUtil.getTokenType(refreshToken))) {
                    ctx.status(401).json(Map.of("success", false, "message", "无效的 refresh token"));
                    return;
                }
                String username = jwtUtil.getUsername(refreshToken);
                User user = userService.findByUserName(username);
                if (user == null) {
                    ctx.status(401).json(Map.of("success", false, "message", "用户不存在"));
                    return;
                }
                ctx.json(Map.of(
                        "success", true,
                        "token", jwtUtil.generateAccessToken(user.getUserName(), user.getRole()),
                        "refreshToken", jwtUtil.generateRefreshToken(user.getUserName(), user.getRole())
                ));
            });

            config.routes.get("/api/auth/check", ctx -> {
                String auth = ctx.header("Authorization");
                ctx.json(Map.of("authenticated", auth != null && auth.startsWith("Bearer ")));
            });

            // ===== 书签浏览 API =====
            config.routes.get("/api/bookmarks", ctx -> {
                String fidStr = ctx.queryParam("folderId");
                Long folderId = fidStr != null && !fidStr.isEmpty() ? Long.parseLong(fidStr) : null;
                List<Bookmark> bookmarks;
                if (folderId != null) {
                    bookmarks = bookmarkService.listPublicByFolderIds(List.of(folderId));
                } else {
                    bookmarks = bookmarkService.listPublicByFolderIds(null);
                }
                ctx.json(Map.of("bookmarks", bookmarks, "total", bookmarks.size()));
            });

            config.routes.get("/api/collects", ctx -> {
                ctx.redirect("/api/bookmarks?" + ctx.queryString());
            });

            config.routes.get("/api/search", ctx -> {
                String key = ctx.queryParam("key");
                if (key == null) key = "";
                List<Bookmark> results = bookmarkService.searchPublic(key);
                ctx.json(Map.of("results", results, "total", results.size()));
            });

            // ===== 首页书签（用户私有） =====
            config.routes.get("/api/home-bookmarks", ctx -> {
                User user = ctx.attribute("user");
                if (user != null) {
                    List<Folder> tree = folderService.listTreeByUserId(user.getId());
                    for (Folder f : tree) {
                        if ("首页".equals(f.getName())) {
                            List<Long> ids = folderService.getDescendantIds(f.getId());
                            List<Bookmark> bookmarks = bookmarkService.listPublicByFolderIds(ids);
                            ctx.json(bookmarks);
                            return;
                        }
                    }
                }
                ctx.json(Collections.emptyList());
            });

            // ===== 文件夹 API =====
            config.routes.get("/api/folders", ctx -> {
                String username = ctx.attribute("username");
                if (username == null) {
                    // 手动检查 Authorization header（addbookmark.html 等页面不经过 JWT 中间件）
                    String authHeader = ctx.header("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        if (jwtUtil.validateToken(token) && "access".equals(jwtUtil.getTokenType(token))) {
                            username = jwtUtil.getUsername(token);
                        }
                    }
                }
                if (username != null) {
                    User user = userService.findByUserName(username);
                    ctx.json(folderService.listTreeByUserId(user.getId()));
                } else {
                    ctx.json(folderService.listPublicTree());
                }
            });

            config.routes.post("/api/folders", ctx -> {
                Folder folder = ctx.bodyAsClass(Folder.class);
                User user = ctx.attribute("user");
                if (user != null) folder.setUserId(user.getId());
                folderService.save(folder);
                ctx.json(folder);
            });

            config.routes.put("/api/folders/{id}", ctx -> {
                long id = ctx.pathParamAsClass("id", Long.class).get();
                Folder folder = ctx.bodyAsClass(Folder.class);
                Folder existing = folderService.getById(id);
                if (existing == null) { ctx.status(404); return; }
                folder.setId(id);
                if (folder.getUserId() == null) folder.setUserId(existing.getUserId());
                if (folder.getDeleted() == null) folder.setDeleted(existing.getDeleted());
                folder.setCreateTime(existing.getCreateTime());
                folderService.updateById(folder);
                ctx.json(folder);
            });

            config.routes.delete("/api/folders/{id}", ctx -> {
                long id = ctx.pathParamAsClass("id", Long.class).get();
                boolean force = Boolean.parseBoolean(ctx.queryParam("force"));
                Folder existing = folderService.getById(id);
                if (existing == null) { ctx.status(404).json(Map.of("success", false, "message", "文件夹不存在")); return; }

                List<Long> allIds = folderService.getDescendantIds(id);
                List<Long> childFolderIds = allIds.size() > 1 ? allIds.subList(1, allIds.size()) : new ArrayList<>();
                long bmCount = bookmarkService.listByFolderIds(allIds).size();
                Map<String, Object> result = new LinkedHashMap<>();

                if (!force) {
                    boolean hasChildren = !childFolderIds.isEmpty() || bmCount > 0;
                    result.put("success", !hasChildren);
                    result.put("hasChildren", hasChildren);
                    result.put("childFolderCount", childFolderIds.size());
                    result.put("bookmarkCount", (int) bmCount);
                    if (hasChildren) result.put("message", "该文件夹下有 " + childFolderIds.size() + " 个子目录和 " + bmCount + " 个书签，确认全部删除？");
                    ctx.json(result);
                    return;
                }

                if (bmCount > 0) bookmarkService.removeByFolderIds(allIds);
                if (!childFolderIds.isEmpty()) folderService.removeCascade(id);
                else folderService.removeById(id);
                result.put("success", true);
                result.put("deletedFolders", allIds.size());
                result.put("deletedBookmarks", (int) bmCount);
                ctx.json(result);
            });

            // ===== 书签管理 API =====
            config.routes.get("/api/admin/bookmarks", ctx -> {
                int page = parseInt(ctx.queryParam("page"), 1);
                int size = parseInt(ctx.queryParam("size"), 10);
                String fidStr = ctx.queryParam("folderId");
                Long folderId = fidStr != null && !fidStr.isEmpty() ? Long.parseLong(fidStr) : null;
                String keyword = ctx.queryParam("keyword");
                String ptStr = ctx.queryParam("publicType");
                Integer publicType = ptStr != null && !ptStr.isEmpty() ? Integer.parseInt(ptStr) : null;
                PageResult<Bookmark> result = bookmarkService.adminPageByFolder(page, size, keyword, folderId, publicType, folderService);
                ctx.json(Map.of("records", result.getRecords(), "total", result.getTotal(), "pages", result.getPages(), "current", result.getCurrent()));
            });

            config.routes.post("/api/admin/bookmarks", ctx -> {
                Bookmark bookmark = ctx.bodyAsClass(Bookmark.class);
                User user = ctx.attribute("user");
                if (user != null) bookmark.setUserId(user.getId());
                if (bookmark.getPublicType() == null) bookmark.setPublicType(1);
                if (bookmark.getReviewStatus() == null) bookmark.setReviewStatus(1);
                if (bookmark.getCreateTime() == null) bookmark.setCreateTime(LocalDateTime.now());
                bookmark.setUpdateTime(LocalDateTime.now());
                bookmarkService.save(bookmark);
                final Long savedId = bookmark.getId();
                final String savedUrl = bookmark.getUrl();
                new Thread(() -> {
                    try {
                        String logoUrl = faviconService.fetchAndSave(savedUrl);
                        if (logoUrl != null) {
                            Bookmark b = bookmarkService.getById(savedId);
                            if (b != null && b.getLogoUrl() == null) {
                                b.setLogoUrl(logoUrl);
                                bookmarkService.updateById(b);
                            }
                        }
                    } catch (Exception ignored) {}
                }).start();
                ctx.json(Map.of("success", true, "id", bookmark.getId()));
            });

            config.routes.put("/api/admin/bookmarks/{id}", ctx -> {
                long id = ctx.pathParamAsClass("id", Long.class).get();
                Bookmark bookmark = ctx.bodyAsClass(Bookmark.class);
                Bookmark existing = bookmarkService.getById(id);
                if (existing == null) { ctx.status(404).json(Map.of("success", false, "message", "书签不存在")); return; }
                User user = ctx.attribute("user");
                if (user != null && !user.getId().equals(existing.getUserId())) {
                    ctx.status(403).json(Map.of("success", false, "message", "无权操作该书签")); return;
                }
                // 合并：只更新传入的非空字段，保留原有数据
                if (bookmark.getTitle() != null) existing.setTitle(bookmark.getTitle());
                if (bookmark.getUrl() != null) existing.setUrl(bookmark.getUrl());
                if (bookmark.getDescription() != null) existing.setDescription(bookmark.getDescription());
                if (bookmark.getFolderId() != null) existing.setFolderId(bookmark.getFolderId());
                if (bookmark.getLogoUrl() != null) existing.setLogoUrl(bookmark.getLogoUrl());
                if (bookmark.getTags() != null) existing.setTags(bookmark.getTags());
                if (bookmark.getPublicType() != null) existing.setPublicType(bookmark.getPublicType());
                if (bookmark.getSortOrder() != null) existing.setSortOrder(bookmark.getSortOrder());
                existing.setUpdateTime(LocalDateTime.now());
                bookmarkService.updateById(existing);
                ctx.json(Map.of("success", true));
            });

            config.routes.delete("/api/admin/bookmarks/{id}", ctx -> {
                long id = ctx.pathParamAsClass("id", Long.class).get();
                Bookmark existing = bookmarkService.getById(id);
                if (existing == null) { ctx.status(404).json(Map.of("success", false, "message", "书签不存在")); return; }
                User user = ctx.attribute("user");
                if (user != null && !user.getId().equals(existing.getUserId())) {
                    ctx.status(403).json(Map.of("success", false, "message", "无权操作该书签")); return;
                }
                bookmarkService.removeById(id);
                ctx.json(Map.of("success", true));
            });

            config.routes.put("/api/admin/sort/bookmarks", ctx -> {
                List<Map<String, Object>> items = ctx.bodyAsClass(List.class);
                User user = ctx.attribute("user");
                for (Map<String, Object> item : items) {
                    Number id = (Number) item.get("id");
                    Number sortOrder = (Number) item.get("sortOrder");
                    Bookmark b = bookmarkService.getById(id.longValue());
                    if (b != null && user != null) {
                        if (b.getUserId() == null || user.getId().equals(b.getUserId())) {
                            b.setSortOrder(sortOrder.intValue());
                            bookmarkService.updateById(b);
                        }
                    }
                }
                ctx.json(Map.of("success", true));
            });

            config.routes.put("/api/admin/sort/folders", ctx -> {
                List<Map<String, Object>> items = ctx.bodyAsClass(List.class);
                User user = ctx.attribute("user");
                for (Map<String, Object> item : items) {
                    Number id = (Number) item.get("id");
                    Number sortOrder = (Number) item.get("sortOrder");
                    Folder f = folderService.getById(id.longValue());
                    if (f != null && user != null) {
                        // 允许排序：文件夹无归属（公共）或归属当前用户
                        if (f.getUserId() == null || user.getId().equals(f.getUserId())) {
                            f.setSortOrder(sortOrder.intValue());
                            folderService.updateById(f);
                        }
                    }
                }
                ctx.json(Map.of("success", true));
            });

            // ===== CSS 保存 =====
            config.routes.post("/api/admin/save-css", ctx -> {
                try {
                    Map<String, Object> body = ctx.bodyAsClass(Map.class);
                    @SuppressWarnings("unchecked")
                    Map<String, Map<String, String>> changes = (Map<String, Map<String, String>>) body.get("changes");
                    cssEditorService.saveCssChanges(changes);
                    ctx.json(Map.of("success", true));
                } catch (IllegalArgumentException e) {
                    ctx.status(400).json(Map.of("message", e.getMessage()));
                } catch (Exception e) {
                    log.error("保存 CSS 变更失败: {}", e.getMessage(), e);
                    ctx.status(500).json(Map.of("message", "保存失败: " + e.getMessage()));
                }
            });

            // ===== 导入导出 =====
            config.routes.post("/admin/tool/import", ctx -> {
                User user = ctx.attribute("user");
                if (user == null) { ctx.json(Map.of("success", false, "message", "未登录")); return; }
                UploadedFile file = ctx.uploadedFile("htmlFile");
                if (file == null) { ctx.json(Map.of("success", false, "message", "请选择文件")); return; }
                String structure = ctx.formParam("structure");
                String type = ctx.formParam("type");
                try {
                    Map<String, Object> result = importExportService.importBookmarks(
                            file.content(), structure, type, user);
                    ctx.json(result);
                } catch (Exception e) {
                    log.debug("书签导入失败", e);
                    ctx.json(Map.of("success", false, "message", "导入失败：" + e.getMessage()));
                }
            });

            config.routes.get("/admin/tool/export/download", ctx -> {
                User user = ctx.attribute("user");
                if (user == null) { ctx.status(401).json(Map.of("success", false, "message", "未登录")); return; }
                String scope = ctx.queryParam("scope") != null ? ctx.queryParam("scope") : "all";
                String format = ctx.queryParam("format") != null ? ctx.queryParam("format") : "html";
                String folderIds = ctx.queryParam("folderIds");
                ExportResult exportResult = importExportService.prepareExport(scope, format, folderIds, user);
                byte[] bytes = exportResult.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ctx.contentType(exportResult.getContentType());
                ctx.header("Content-Disposition", "attachment; filename=\"" + java.net.URLEncoder.encode(exportResult.getFilename(), java.nio.charset.StandardCharsets.UTF_8) + "\"");
                ctx.result(bytes);
            });

            // ===== 用户管理 API =====
            config.routes.get("/admin/api/user/list", ctx -> {
                int pageNum = parseInt(ctx.queryParam("pageNum"), 1);
                int pageSize = parseInt(ctx.queryParam("pageSize"), 10);
                String keyword = ctx.queryParam("keyword");
                try {
                    PageResult<User> page = userService.pageList(pageNum, pageSize, keyword);
                    List<Map<String, Object>> list = page.getRecords().stream().map(u -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", u.getId()); m.put("userName", u.getUserName());
                        m.put("email", u.getEmail()); m.put("role", u.getRole());
                        m.put("introduction", u.getIntroduction());
                        m.put("createTime", u.getCreateTime() != null ? u.getCreateTime().toString().substring(0, 19) : "");
                        return m;
                    }).collect(Collectors.toList());
                    ctx.json(Map.of("success", true, "data", Map.of("list", list, "total", page.getTotal())));
                } catch (Exception e) {
                    ctx.json(Map.of("success", false, "message", e.getMessage()));
                }
            });

            config.routes.get("/admin/api/user/detail", ctx -> {
                long id = ctx.queryParamAsClass("id", Long.class).get();
                User user = userService.getById(id);
                if (user == null) { ctx.json(Map.of("success", false, "message", "用户不存在")); return; }
                Map<String, Object> data = new HashMap<>();
                data.put("id", user.getId()); data.put("userName", user.getUserName());
                data.put("email", user.getEmail()); data.put("role", user.getRole());
                data.put("introduction", user.getIntroduction());
                ctx.json(Map.of("success", true, "data", data));
            });

            config.routes.post("/admin/api/user/save", ctx -> {
                Map<String, String> body = ctx.bodyAsClass(Map.class);
                try {
                    String idStr = body.get("id");
                    String userName = body.getOrDefault("userName", "").trim();
                    String password = body.getOrDefault("password", "").trim();
                    String email = body.getOrDefault("email", "").trim();
                    String role = body.getOrDefault("role", "ROLE_USER");
                    String introduction = body.getOrDefault("introduction", "");

                    if (userName.isEmpty()) {
                        ctx.json(Map.of("success", false, "message", "用户名不能为空")); return;
                    }

                    if (idStr == null || idStr.isEmpty()) {
                        if (password.isEmpty()) { ctx.json(Map.of("success", false, "message", "密码不能为空")); return; }
                        if (userService.findByUserName(userName) != null) { ctx.json(Map.of("success", false, "message", "用户名已存在")); return; }
                        User u = new User();
                        u.setUserName(userName); u.setPassword(PasswordUtil.encode(password));
                        u.setEmail(email); u.setRole(role); u.setIntroduction(introduction);
                        userService.save(u);
                        folderService.createDefaultFolders(u.getId());
                    } else {
                        User u = userService.getById(Long.valueOf(idStr));
                        if (u == null) { ctx.json(Map.of("success", false, "message", "用户不存在")); return; }
                        if (!u.getUserName().equals(userName) && userService.findByUserName(userName) != null) {
                            ctx.json(Map.of("success", false, "message", "用户名已存在")); return;
                        }
                        u.setUserName(userName);
                        if (!password.isEmpty()) u.setPassword(PasswordUtil.encode(password));
                        u.setEmail(email); u.setRole(role); u.setIntroduction(introduction);
                        u.setUpdateTime(LocalDateTime.now());
                        userService.updateById(u);
                    }
                    ctx.json(Map.of("success", true, "message", "保存成功"));
                } catch (Exception e) {
                    ctx.json(Map.of("success", false, "message", e.getMessage()));
                }
            });

            config.routes.post("/admin/api/user/delete", ctx -> {
                long id = ctx.queryParamAsClass("id", Long.class).get();
                User current = ctx.attribute("user");
                if (current != null && current.getId().equals(id)) {
                    ctx.json(Map.of("success", false, "message", "不能删除当前登录用户")); return;
                }
                boolean ok = userService.removeById(id);
                ctx.json(Map.of("success", ok, "message", ok ? "删除成功" : "删除失败"));
            });

        }).start(port);

        log.info("Webmark 启动成功，端口: {}, 数据目录: {}", port, dataDir);
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return defaultValue; }
    }

    private static String guessMime(String filename) {
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".ico")) return "image/x-icon";
        if (filename.endsWith(".svg")) return "image/svg+xml";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        if (filename.endsWith(".gif")) return "image/gif";
        if (filename.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}
