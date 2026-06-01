package com.cloud.self.webmark.controller;

import com.cloud.self.webmark.entity.Bookmark;
import com.cloud.self.webmark.entity.Folder;
import com.cloud.self.webmark.entity.User;
import com.cloud.self.webmark.service.BookmarkService;
import com.cloud.self.webmark.service.FolderService;
import com.cloud.self.webmark.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class IndexController {

    private final BookmarkService bookmarkService;
    private final FolderService folderService;
    private final UserService userService;

    @GetMapping({"/", "/index"})
    public String index(@RequestParam(required = false) Long folderId,
                        @AuthenticationPrincipal UserDetails userDetails,
                        Model model) {
        User user = null;
        List<Folder> folderTree;

        if (userDetails != null) {
            user = userService.findByUserName(userDetails.getUsername());
            folderTree = user != null ? folderService.listTreeByUserId(user.getId()) : folderService.listPublicTree();
            model.addAttribute("user", user);
            model.addAttribute("currentUser", user);
        } else {
            folderTree = folderService.listPublicTree();
        }
        model.addAttribute("folderTree", folderTree);

        // 默认选中第一个顶级文件夹
        if (folderId == null && !folderTree.isEmpty()) {
            folderId = folderTree.get(0).getId();
        }
        model.addAttribute("folderId", folderId);

        // 构建书签分组
        Map<Long, Map<String, List<Bookmark>>> allGrouped = new LinkedHashMap<>();
        for (Folder top : folderTree) {
            List<Long> folderIds = folderService.getDescendantIds(top.getId());
            List<Bookmark> bookmarks = bookmarkService.listPublicByFolderIds(folderIds);
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

        return "index";
    }

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
    public String search(@RequestParam(required = false) String key,
                         @AuthenticationPrincipal UserDetails userDetails,
                         Model model) {
        if (key != null && !key.isEmpty()) {
            model.addAttribute("results", bookmarkService.searchPublic(key));
        }
        model.addAttribute("key", key);
        model.addAttribute("folderTree",
                userDetails != null ? folderService.listTreeByUserId(
                        userService.findByUserName(userDetails.getUsername()).getId())
                        : folderService.listPublicTree());
        return "search";
    }

    @GetMapping("/login")
    public String login() { return "login"; }

    @GetMapping("/register")
    public String register() { return "register"; }

    @GetMapping("/lookAround")
    public String lookAround() { return "redirect:/index"; }
}
