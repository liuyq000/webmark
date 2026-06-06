package com.cloud.self.webmark.controller;

import com.cloud.self.webmark.entity.Bookmark;
import com.cloud.self.webmark.entity.Folder;
import com.cloud.self.webmark.entity.User;
import com.cloud.self.webmark.security.JwtUtil;
import com.cloud.self.webmark.service.*;
import com.cloud.self.webmark.store.PageResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final BookmarkService bookmarkService;
    private final FolderService folderService;
    private final UserService userService;
    private final FaviconService faviconService;
    private final CssEditorService cssEditorService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    /** JWT 登录：返回 token */
    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> jwtLogin(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        Map<String, Object> result = new LinkedHashMap<>();

        User user = userService.findByUserName(username);
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            result.put("success", false);
            result.put("message", "用户名或密码错误");
            return ResponseEntity.status(401).body(result);
        }

        String token = jwtUtil.generateAccessToken(user.getUserName(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUserName(), user.getRole());
        result.put("success", true);
        result.put("token", token);
        result.put("refreshToken", refreshToken);
        result.put("username", user.getUserName());
        result.put("role", user.getRole());
        return ResponseEntity.ok(result);
    }

    /** 刷新 access token */
    @PostMapping("/auth/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || !"refresh".equals(jwtUtil.getTokenType(refreshToken))) {
            result.put("success", false);
            result.put("message", "无效的 refresh token");
            return ResponseEntity.status(401).body(result);
        }
        String username = jwtUtil.getUsername(refreshToken);
        User user = userService.findByUserName(username);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return ResponseEntity.status(401).body(result);
        }
        String newToken = jwtUtil.generateAccessToken(user.getUserName(), user.getRole());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getUserName(), user.getRole());
        result.put("success", true);
        result.put("token", newToken);
        result.put("refreshToken", newRefreshToken);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/bookmarks")
    public Map<String, Object> listBookmarks(@RequestParam(required = false) Long folderId) {
        List<Bookmark> bookmarks;
        if (folderId != null) {
            List<Long> folderIds = folderService.getDescendantIds(folderId);
            bookmarks = bookmarkService.listPublicByFolderIds(folderIds);
        } else {
            bookmarks = bookmarkService.listPublicByFolderIds(null);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bookmarks", bookmarks);
        result.put("total", bookmarks.size());
        return result;
    }

    @GetMapping("/folders")
    public List<Folder> listFolders(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            User user = userService.findByUserName(userDetails.getUsername());
            return folderService.listTreeByUserId(user.getId());
        }
        return folderService.listPublicTree();
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String key) {
        List<Bookmark> results = bookmarkService.searchPublic(key);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("results", results);
        result.put("total", results.size());
        return result;
    }

    // ==================== 书签管理 ====================

    @GetMapping("/admin/bookmarks")
    public Map<String, Object> adminBookmarks(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) Long folderId,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Integer publicType) {
        PageResult<Bookmark> result = bookmarkService.adminPageByFolder(page, size, keyword, folderId, publicType, folderService);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("pages", result.getPages());
        data.put("current", result.getCurrent());
        return data;
    }

    @PostMapping("/admin/bookmarks")
    public ResponseEntity<Map<String, Object>> createBookmark(@RequestBody Bookmark bookmark, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            User user = userService.findByUserName(userDetails.getUsername());
            bookmark.setUserId(user.getId());
        }
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("id", bookmark.getId());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/admin/bookmarks/{id}")
    public ResponseEntity<Map<String, Object>> updateBookmark(@PathVariable Long id, @RequestBody Bookmark bookmark,
                                                               @AuthenticationPrincipal UserDetails userDetails) {
        Bookmark existing = bookmarkService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "书签不存在"));
        }
        User currentUser = getCurrentUser(userDetails);
        if (currentUser != null && !currentUser.getId().equals(existing.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "无权操作该书签"));
        }
        bookmark.setId(id);
        bookmarkService.updateById(bookmark);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/admin/bookmarks/{id}")
    public ResponseEntity<Map<String, Object>> deleteBookmark(@PathVariable Long id,
                                                              @AuthenticationPrincipal UserDetails userDetails) {
        Bookmark existing = bookmarkService.getById(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "书签不存在"));
        }
        User currentUser = getCurrentUser(userDetails);
        if (currentUser != null && !currentUser.getId().equals(existing.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "无权操作该书签"));
        }
        bookmarkService.removeById(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ResponseEntity.ok(result);
    }

    // ==================== 文件夹 CRUD ====================

    @PostMapping("/folders")
    public ResponseEntity<Folder> createFolder(@RequestBody Folder folder, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            User user = userService.findByUserName(userDetails.getUsername());
            folder.setUserId(user.getId());
        }
        folderService.save(folder);
        return ResponseEntity.ok(folder);
    }

    @PutMapping("/folders/{id}")
    public ResponseEntity<Folder> updateFolder(@PathVariable Long id, @RequestBody Folder folder,
                                                @AuthenticationPrincipal UserDetails userDetails) {
        Folder existing = folderService.getById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        User currentUser = getCurrentUser(userDetails);
        if (currentUser != null && existing.getUserId() != null && !currentUser.getId().equals(existing.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        folder.setId(id);
        if (folder.getUserId() == null) folder.setUserId(existing.getUserId());
        if (folder.getDeleted() == null) folder.setDeleted(existing.getDeleted());
        folder.setCreateTime(existing.getCreateTime());
        folderService.updateById(folder);
        return ResponseEntity.ok(folder);
    }

    @DeleteMapping("/folders/{id}")
    public ResponseEntity<Map<String, Object>> deleteFolder(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean force,
            @AuthenticationPrincipal UserDetails userDetails) {
        Folder existing = folderService.getById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        User currentUser = getCurrentUser(userDetails);
        if (currentUser != null && existing.getUserId() != null && !currentUser.getId().equals(existing.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", "无权操作该文件夹"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        List<Long> descendantIds = folderService.getDescendantIds(id);
        List<Long> childFolderIds = descendantIds.subList(1, descendantIds.size());
        long bookmarkCount = bookmarkService.listByFolderIds(descendantIds).size();

        if (!force) {
            boolean hasChildren = childFolderIds.size() > 0 || bookmarkCount > 0;
            result.put("success", !hasChildren);
            result.put("hasChildren", hasChildren);
            result.put("childFolderCount", childFolderIds.size());
            result.put("bookmarkCount", (int) bookmarkCount);
            if (hasChildren) {
                result.put("message", "该文件夹下有 " + childFolderIds.size() + " 个子目录和 " + bookmarkCount + " 个书签，确认全部删除？");
            }
            return ResponseEntity.ok(result);
        }

        if (bookmarkCount > 0) {
            bookmarkService.removeByFolderIds(descendantIds);
        }
        if (!childFolderIds.isEmpty()) {
            folderService.removeCascade(id);
        } else {
            folderService.removeById(id);
        }

        result.put("success", true);
        result.put("deletedFolders", descendantIds.size());
        result.put("deletedBookmarks", (int) bookmarkCount);
        return ResponseEntity.ok(result);
    }

    // ==================== 批量排序 ====================

    @PutMapping("/admin/sort/bookmarks")
    public ResponseEntity<Map<String, Object>> sortBookmarks(@RequestBody List<Map<String, Object>> items,
                                                             @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        for (Map<String, Object> item : items) {
            Number id = (Number) item.get("id");
            Number sortOrder = (Number) item.get("sortOrder");
            Bookmark b = bookmarkService.getById(id.longValue());
            if (b != null && currentUser != null && currentUser.getId().equals(b.getUserId())) {
                b.setSortOrder(sortOrder.intValue());
                bookmarkService.updateById(b);
            }
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/admin/sort/folders")
    public ResponseEntity<Map<String, Object>> sortFolders(@RequestBody List<Map<String, Object>> items,
                                                           @AuthenticationPrincipal UserDetails userDetails) {
        User currentUser = getCurrentUser(userDetails);
        for (Map<String, Object> item : items) {
            Number id = (Number) item.get("id");
            Number sortOrder = (Number) item.get("sortOrder");
            Folder f = folderService.getById(id.longValue());
            if (f != null && currentUser != null && f.getUserId() != null && currentUser.getId().equals(f.getUserId())) {
                f.setSortOrder(sortOrder.intValue());
                folderService.updateById(f);
            }
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** 保存 CSS 变更 - 委托给 CssEditorService */
    @PostMapping("/admin/save-css")
    public ResponseEntity<?> saveCssChanges(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> changes = (Map<String, Map<String, String>>) body.get("changes");
            cssEditorService.saveCssChanges(changes);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("保存 CSS 变更失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "保存失败: " + e.getMessage()));
        }
    }

    // ==================== 工具方法 ====================

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails != null) {
            return userService.findByUserName(userDetails.getUsername());
        }
        return null;
    }
}
