package com.cloud.self.webmark.controller;

import com.cloud.self.webmark.entity.Bookmark;
import com.cloud.self.webmark.entity.Folder;
import com.cloud.self.webmark.entity.User;
import com.cloud.self.webmark.service.BookmarkService;
import com.cloud.self.webmark.service.FolderService;
import com.cloud.self.webmark.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
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
                        HttpServletRequest request,
                        Model model) {
        User user = null;
        List<Folder> folderTree;
        List<Bookmark> homeBookmarks = new ArrayList<>();

        if (userDetails != null) {
            user = userService.findByUserName(userDetails.getUsername());
            folderTree = user != null ? folderService.listTreeByUserId(user.getId()) : folderService.listPublicTree();
            model.addAttribute("user", user);

            // 登录后：从左侧栏移除"首页"文件夹，将其书签作为快捷链接
            Folder homeFolder = null;
            for (int i = folderTree.size() - 1; i >= 0; i--) {
                if ("首页".equals(folderTree.get(i).getName())) {
                    homeFolder = folderTree.remove(i);
                    break;
                }
            }
            if (homeFolder != null) {
                List<Long> homeDescendantIds = folderService.getDescendantIds(homeFolder.getId());
                homeBookmarks = bookmarkService.listPublicByFolderIds(homeDescendantIds);
            }
        } else {
            folderTree = folderService.listPublicTree();
        }
        model.addAttribute("folderTree", folderTree);
        model.addAttribute("homeBookmarks", homeBookmarks);

        // 文件夹 ID → Folder 映射
        Map<Long, Folder> folderMap = folderTree.stream()
                .collect(Collectors.toMap(Folder::getId, f -> f, (a, b) -> a, LinkedHashMap::new));
        model.addAttribute("folderMap", folderMap);

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

        // 构建 bookmarklet 路径（用于网页收集小工具）
        // 使用当前请求的协议+域名+端口，确保 bookmarklet 打开的地址与当前 session 的 cookie 域一致
        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort()) + "/";
        String path="javascript:(function()%7Bvar%20description;var%20desString=%22%22;var%20metas=document.getElementsByTagName('meta');for(var%20x=0,y=metas.length;x%3Cy;x++)%7Bif(metas%5Bx%5D.name.toLowerCase()==%22description%22)%7Bdescription=metas%5Bx%5D;%7D%7Dif(description)%7BdesString=%22&amp;description=%22+encodeURIComponent(description.content);%7Dvar%20win=window.open(%22"
                + baseUrl
                +"addbookmark?from=webtool&url=%22+encodeURIComponent(document.URL)+desString+%22&title=%22+encodeURIComponent(document.title)+%22&charset=%22+document.charset,'_blank');win.focus();%7D)();";
        model.addAttribute("bookmarkletPath",path);

        return "index";
    }

    @GetMapping("/addbookmark")
    public String addBookmark(@RequestParam(required = false) String url,
                              @RequestParam(required = false) String title,
                              @RequestParam(required = false) String description,
                              @RequestParam(required = false) String charset,
                              @AuthenticationPrincipal UserDetails userDetails,
                              Model model) {
        if (userDetails != null) {
            User user = userService.findByUserName(userDetails.getUsername());
            model.addAttribute("user", user);
            List<Folder> folderTree = folderService.listTreeByUserId(user.getId());
            model.addAttribute("folderTree", folderTree);
        }
        model.addAttribute("bmUrl", url != null ? url : "");
        model.addAttribute("bmTitle", title != null ? title : "");
        model.addAttribute("bmDescription", description != null ? description : "");
        model.addAttribute("bmCharset", charset != null ? charset : "");
        return "addbookmark";
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
        User loginUser = userDetails != null ? userService.findByUserName(userDetails.getUsername()) : null;
        model.addAttribute("folderTree",
                loginUser != null ? folderService.listTreeByUserId(loginUser.getId())
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
