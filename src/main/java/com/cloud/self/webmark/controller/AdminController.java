package com.cloud.self.webmark.controller;

import com.cloud.self.webmark.entity.User;
import com.cloud.self.webmark.service.FolderService;
import com.cloud.self.webmark.service.ImportExportService;
import com.cloud.self.webmark.service.ImportExportService.ExportResult;
import com.cloud.self.webmark.service.UserService;
import com.cloud.self.webmark.store.PageResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final FolderService folderService;
    private final UserService userService;
    private final ImportExportService importExportService;
    private final PasswordEncoder passwordEncoder;

    // ========== 页面路由统一重定向到 /index ==========

    @GetMapping({"", "/index", "/bookmark/edit/**", "/folder/list",
            "/user/list", "/favorites/list", "/urlLibrary/list", "/config",
            "/tool/collect", "/tool/import", "/tool/export", "/icon/list"})
    public String redirectToIndex() {
        return "redirect:/index";
    }

    // ========== 导入导出 ==========

    @PostMapping("/tool/import")
    @ResponseBody
    public Map<String, Object> importBookmarks(
            @RequestParam("htmlFile") MultipartFile htmlFile,
            @RequestParam(required = false) String structure,
            @RequestParam(required = false) String type,
            @AuthenticationPrincipal UserDetails userDetails) {
        Map<String, Object> result = new HashMap<>();
        String username = userDetails != null ? userDetails.getUsername() :
                SecurityContextHolder.getContext().getAuthentication() != null ?
                        SecurityContextHolder.getContext().getAuthentication().getName() : null;
        if (username == null) {
            result.put("success", false);
            result.put("message", "未登录或会话已过期");
            return result;
        }
        User user = userService.findByUserName(username);
        try {
            Map<String, Object> importResult = importExportService.importBookmarks(
                    new MultipartFileAdapter(htmlFile), structure, type, user);
            result.putAll(importResult);
        } catch (Exception e) {
            log.debug("书签导入失败", e);
            result.put("success", false);
            result.put("message", "导入失败：" + e.getMessage());
        }
        return result;
    }

    @GetMapping("/tool/export/download")
    public ResponseEntity<byte[]> exportDownload(
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(defaultValue = "html") String format,
            @RequestParam(required = false) String folderIds,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {
        User user = userService.findByUserName(userDetails.getUsername());
        ExportResult exportResult = importExportService.prepareExport(scope, format, folderIds, user);

        byte[] bytes = exportResult.getContent().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + URLEncoder.encode(exportResult.getFilename(), StandardCharsets.UTF_8) + "\"")
                .contentType(MediaType.parseMediaType(exportResult.getContentType())).body(bytes);
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
            }).collect(Collectors.toList());
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
                folderService.createDefaultFolders(u.getId());
            } else {
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

    private record MultipartFileAdapter(MultipartFile delegate) implements ImportExportService.MultipartFile {
        @Override
        public String getOriginalFilename() { return delegate.getOriginalFilename(); }
        @Override
        public java.io.InputStream getInputStream() throws Exception { return delegate.getInputStream(); }
        @Override
        public boolean isEmpty() { return delegate.isEmpty(); }
        @Override
        public long getSize() { return delegate.getSize(); }
    }
}
