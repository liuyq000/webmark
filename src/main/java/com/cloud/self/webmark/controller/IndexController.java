package com.cloud.self.webmark.controller;

import com.cloud.self.webmark.entity.Bookmark;
import com.cloud.self.webmark.entity.Favorites;
import com.cloud.self.webmark.entity.Folder;
import com.cloud.self.webmark.entity.User;
import com.cloud.self.webmark.service.BookmarkService;
import com.cloud.self.webmark.service.FavoritesService;
import com.cloud.self.webmark.service.FolderService;
import com.cloud.self.webmark.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class IndexController {

    private final BookmarkService bookmarkService;
    private final FolderService folderService;
    private final FavoritesService favoritesService;
    private final UserService userService;

    @GetMapping({"/", "/index"})
    public String index(@RequestParam(required = false) Long folderId,
                        @AuthenticationPrincipal UserDetails userDetails,
                        Model model) {
        // 侧边栏文件夹树：登录用户看公共+自己的，匿名用户只看公共
        List<Folder> folderTree;
        if (userDetails != null) {
            User user = userService.findByUserName(userDetails.getUsername());
            folderTree = user != null ? folderService.listTreeByUserId(user.getId()) : folderService.listPublicTree();
        } else {
            folderTree = folderService.listPublicTree();
        }
        model.addAttribute("folderTree", folderTree);

        // 如果没有指定 folderId，默认选中第一个顶级文件夹
        if (folderId == null && !folderTree.isEmpty()) {
            folderId = folderTree.get(0).getId();
        }
        model.addAttribute("folderId", folderId);

        // 构建所有顶级文件夹的书签分组数据（用于前端切换，不跳页面）
        Map<Long, Map<String, List<Bookmark>>> allGrouped = new LinkedHashMap<>();
        for (Folder top : folderTree) {
            List<Long> folderIds = folderService.getDescendantIds(top.getId());
            List<Bookmark> bookmarks = bookmarkService.listPublicByFolderIds(folderIds);
            Map<String, List<Bookmark>> grouped = new LinkedHashMap<>();
            // 按子文件夹分组
            if (top.getChildren() != null) {
                for (Folder child : top.getChildren()) {
                    collectGroupedBookmarks(child, bookmarks, grouped);
                }
            }
            // 直接归属顶级文件夹的书签
            List<Bookmark> topBookmarks = bookmarks.stream()
                    .filter(b -> top.getId().equals(b.getFolderId()))
                    .collect(Collectors.toList());
            if (!topBookmarks.isEmpty()) {
                grouped.put(top.getName(), topBookmarks);
            }
            if (!grouped.isEmpty()) {
                allGrouped.put(top.getId(), grouped);
            }
        }
        model.addAttribute("allGrouped", allGrouped);

        // 当前文件夹信息（用于高亮）
        if (folderId != null) {
            Folder current = folderService.getById(folderId);
            model.addAttribute("currentFolder", current);
        }

        return "index";
    }

    /** 递归收集子文件夹下的书签分组 */
    private void collectGroupedBookmarks(Folder folder, List<Bookmark> allBookmarks, Map<String, List<Bookmark>> grouped) {
        List<Bookmark> folderBookmarks = allBookmarks.stream()
                .filter(b -> folder.getId().equals(b.getFolderId()))
                .collect(Collectors.toList());
        if (!folderBookmarks.isEmpty()) {
            grouped.put(folder.getName(), folderBookmarks);
        }
        if (folder.getChildren() != null) {
            for (Folder child : folder.getChildren()) {
                collectGroupedBookmarks(child, allBookmarks, grouped);
            }
        }
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String key, Model model) {
        if (key != null && !key.isEmpty()) {
            List<Bookmark> results = bookmarkService.searchPublic(key);
            model.addAttribute("results", results);
        }
        model.addAttribute("key", key);
        // 侧边栏
        model.addAttribute("folderTree", folderService.listPublicTree());
        return "search";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/lookAround")
    public String lookAround() {
        return "redirect:/index";
    }

    @GetMapping("/home")
    public String home(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        User user = userService.findByUserName(userDetails.getUsername());
        if (user == null) {
            return "redirect:/login";
        }

        // 用户文件夹树
        List<Folder> folderTree = folderService.listTreeByUserId(user.getId());
        model.addAttribute("folderTree", folderTree);

        // 默认选中第一个顶级文件夹
        Long folderId = folderTree.isEmpty() ? null : folderTree.get(0).getId();
        model.addAttribute("folderId", folderId);

        // 构建所有顶级文件夹的书签分组数据
        Map<Long, Map<String, List<Bookmark>>> allGrouped = new LinkedHashMap<>();
        for (Folder top : folderTree) {
            List<Long> folderIds = folderService.getDescendantIds(top.getId());
            List<Bookmark> bookmarks = bookmarkService.listByFolderIds(folderIds);
            Map<String, List<Bookmark>> grouped = new LinkedHashMap<>();
            if (top.getChildren() != null) {
                for (Folder child : top.getChildren()) {
                    collectGroupedBookmarks(child, bookmarks, grouped);
                }
            }
            List<Bookmark> topBookmarks = bookmarks.stream()
                    .filter(b -> top.getId().equals(b.getFolderId()))
                    .collect(Collectors.toList());
            if (!topBookmarks.isEmpty()) {
                grouped.put(top.getName(), topBookmarks);
            }
            if (!grouped.isEmpty()) {
                allGrouped.put(top.getId(), grouped);
            }
        }
        model.addAttribute("allGrouped", allGrouped);

        // 用户收藏夹列表
        List<Favorites> favoritesList = favoritesService.list().stream()
                .filter(f -> user.getId().equals(f.getUserId()))
                .sorted(Comparator.comparing(Favorites::getCreateTime).reversed())
                .collect(Collectors.toList());
        model.addAttribute("user", user);
        model.addAttribute("favoritesList", favoritesList);

        return "home";
    }
}
