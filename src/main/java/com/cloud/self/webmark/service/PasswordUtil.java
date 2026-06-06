package com.cloud.self.webmark.service;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * 密码工具类，代替 Spring Security 的 PasswordEncoder。
 */
public class PasswordUtil {

    public static String encode(String rawPassword) {
        return BCrypt.withDefaults().hashToString(10, rawPassword.toCharArray());
    }

    public static boolean matches(String rawPassword, String encodedPassword) {
        return BCrypt.verifyer().verify(rawPassword.toCharArray(), encodedPassword).verified;
    }
}
