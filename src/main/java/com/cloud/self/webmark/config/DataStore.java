package com.cloud.self.webmark.config;

import com.cloud.self.webmark.entity.Bookmark;
import com.cloud.self.webmark.entity.Config;
import com.cloud.self.webmark.entity.Folder;
import com.cloud.self.webmark.entity.User;
import com.cloud.self.webmark.service.PasswordUtil;
import com.cloud.self.webmark.store.JsonFileRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据存储管理器，管理所有 JSON 文件仓库并初始化种子数据。
 */
public class DataStore {

    private static final Logger log = LoggerFactory.getLogger(DataStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final String dataDir;
    private final boolean jsonPrettyPrint;

    private JsonFileRepository<User> userRepository;
    private JsonFileRepository<Bookmark> bookmarkRepository;
    private JsonFileRepository<Folder> folderRepository;
    private JsonFileRepository<Config> configRepository;

    public DataStore(String dataDir, boolean jsonPrettyPrint) {
        this.dataDir = dataDir;
        this.jsonPrettyPrint = jsonPrettyPrint;
    }

    public void init() {
        File dir = new File(dataDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        userRepository = new JsonFileRepository<>(new File(dir, "users.json"), User.class, jsonPrettyPrint);
        bookmarkRepository = new JsonFileRepository<>(new File(dir, "bookmarks.json"), Bookmark.class, jsonPrettyPrint);
        folderRepository = new JsonFileRepository<>(new File(dir, "folders.json"), Folder.class, jsonPrettyPrint);
        configRepository = new JsonFileRepository<>(new File(dir, "config.json"), Config.class, jsonPrettyPrint);

        if (userRepository.count() == 0) {
            log.info("首次运行，初始化种子数据...");
            initSeedData();
            log.info("种子数据初始化完成");
        }

        log.info("数据存储初始化完成，数据目录: {}", dir.getAbsolutePath());
    }

    private void initSeedData() {
        try {
            List<User> users = loadSeedData("seed/users.json", new TypeReference<List<User>>() {});
            for (User user : users) {
                user.setPassword(PasswordUtil.encode(user.getPassword()));
                user.setCreateTime(LocalDateTime.now());
                user.setUpdateTime(LocalDateTime.now());
                userRepository.save(user);
            }

            List<Folder> folders = loadSeedData("seed/folders.json", new TypeReference<List<Folder>>() {});
            for (Folder folder : folders) {
                folder.setCreateTime(LocalDateTime.now());
                folder.setUpdateTime(LocalDateTime.now());
                folderRepository.save(folder);
            }

            List<Bookmark> bookmarks = loadSeedData("seed/bookmarks.json", new TypeReference<List<Bookmark>>() {});
            for (Bookmark bookmark : bookmarks) {
                bookmark.setCreateTime(LocalDateTime.now());
                bookmark.setUpdateTime(LocalDateTime.now());
                bookmarkRepository.save(bookmark);
            }

            log.info("种子数据加载完成: {} 用户, {} 文件夹, {} 书签",
                    users.size(), folders.size(), bookmarks.size());
        } catch (Exception e) {
            log.error("加载种子数据失败", e);
            throw new RuntimeException("种子数据加载失败", e);
        }
    }

    private <T> List<T> loadSeedData(String path, TypeReference<List<T>> typeRef) throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream is = cl.getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("种子文件未找到: " + path);
            return MAPPER.readValue(is, typeRef);
        }
    }

    public JsonFileRepository<User> getUserRepository() { return userRepository; }
    public JsonFileRepository<Bookmark> getBookmarkRepository() { return bookmarkRepository; }
    public JsonFileRepository<Folder> getFolderRepository() { return folderRepository; }
    public JsonFileRepository<Config> getConfigRepository() { return configRepository; }
}
