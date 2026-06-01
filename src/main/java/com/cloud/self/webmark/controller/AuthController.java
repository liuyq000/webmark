package com.cloud.self.webmark.controller;

import com.cloud.self.webmark.entity.User;
import com.cloud.self.webmark.service.FolderService;
import com.cloud.self.webmark.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final FolderService folderService;

    @PostMapping("/register")
    public String register(@ModelAttribute User user) {
        if (userService.findByUserName(user.getUserName()) != null) {
            return "redirect:/register?error=exists";
        }
        if (userService.findByEmail(user.getEmail()) != null) {
            return "redirect:/register?error=email_exists";
        }
        userService.register(user);
        // 为新用户创建默认文件夹
        folderService.createDefaultFolders(user.getId());
        return "redirect:/login?registered=true";
    }
}
