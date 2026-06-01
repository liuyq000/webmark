package com.cloud.self.webmark.controller;

import com.cloud.self.webmark.entity.Bookmark;
import com.cloud.self.webmark.entity.Folder;
import com.cloud.self.webmark.entity.User;
import com.cloud.self.webmark.security.JwtUtil;
import com.cloud.self.webmark.service.BookmarkService;
import com.cloud.self.webmark.service.FolderService;
import com.cloud.self.webmark.service.UserService;
import com.cloud.self.webmark.store.PageResult;
import lombok.RequiredArgsConstructor;
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

    private final BookmarkService bookmarkService;
    private final FolderService folderService;
    private final UserService userService;
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

        String token = jwtUtil.generateToken(user.getUserName(), user.getRole());
        result.put("success", true);
        result.put("token", token);
        result.put("username", user.getUserName());
        result.put("role", user.getRole());
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("id", bookmark.getId());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/admin/bookmarks/{id}")
    public ResponseEntity<Map<String, Object>> updateBookmark(@PathVariable Long id, @RequestBody Bookmark bookmark) {
        bookmark.setId(id);
        bookmarkService.updateById(bookmark);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/admin/bookmarks/{id}")
    public ResponseEntity<Map<String, Object>> deleteBookmark(@PathVariable Long id) {
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
    public ResponseEntity<Folder> updateFolder(@PathVariable Long id, @RequestBody Folder folder) {
        folder.setId(id);
        folderService.updateById(folder);
        return ResponseEntity.ok(folder);
    }

    @DeleteMapping("/folders/{id}")
    public ResponseEntity<Map<String, Object>> deleteFolder(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean force) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 统计子内容
        List<Long> descendantIds = folderService.getDescendantIds(id);
        List<Long> childFolderIds = descendantIds.subList(1, descendantIds.size()); // 去掉自身
        long bookmarkCount = bookmarkService.listByFolderIds(descendantIds).size();

        // 非强制模式：仅检查，不删除
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

        // 强制模式：级联删除，先删书签，再删文件夹
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
    public ResponseEntity<Map<String, Object>> sortBookmarks(@RequestBody List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            Number id = (Number) item.get("id");
            Number sortOrder = (Number) item.get("sortOrder");
            Bookmark b = bookmarkService.getById(id.longValue());
            if (b != null) {
                b.setSortOrder(sortOrder.intValue());
                bookmarkService.updateById(b);
            }
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PutMapping("/admin/sort/folders")
    public ResponseEntity<Map<String, Object>> sortFolders(@RequestBody List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            Number id = (Number) item.get("id");
            Number sortOrder = (Number) item.get("sortOrder");
            Folder f = folderService.getById(id.longValue());
            if (f != null) {
                f.setSortOrder(sortOrder.intValue());
                folderService.updateById(f);
            }
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

}
