package com.cloud.self.webmark.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Webmark 配置加载器。
 * <p>
 * 优先级（低 → 高）：
 * <ol>
 *   <li>config-default.properties（默认值，可在此设置 webmark.env=dev/prod）</li>
 *   <li>config-{env}.properties（按环境覆盖）</li>
 *   <li>环境变量 WEBMARK_XXX（最高优先级）</li>
 * </ol>
 * </p>
 */
public class WebmarkConfig {

    private static final Logger log = LoggerFactory.getLogger(WebmarkConfig.class);

    private final Properties props = new Properties();

    public WebmarkConfig() {
        // 1. 加载默认配置
        loadProps("config-default.properties", false);

        // 2. 确定环境：环境变量 WEBMARK_ENV 优先，否则从 config-default.properties 读取 webmark.env
        String env = System.getenv("WEBMARK_ENV");
        if (env == null) {
            env = props.getProperty("webmark.env", "dev");
        }
        log.info("当前环境: {}", env);

        // 3. 加载环境配置文件
        loadProps("config-" + env + ".properties", true);

        // 4. 环境变量覆盖（最高优先级）
        overrideFromEnv();
    }

    /** 从 classpath 加载 properties 文件 */
    private void loadProps(String fileName, boolean optional) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (is != null) {
                Properties p = new Properties();
                p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                for (String key : p.stringPropertyNames()) {
                    String val = p.getProperty(key);
                    Object old = props.setProperty(key, val);
                    if (old != null && !old.equals(val)) {
                        log.debug("配置 [{}] 被 {} 覆盖: {} → {}", key, fileName, old, val);
                    }
                }
                log.info("已加载配置: {}", fileName);
            } else if (!optional) {
                log.warn("未找到配置文件: {}, 使用默认值", fileName);
            } else {
                log.info("未找到环境配置文件: {}（可选，跳过）", fileName);
            }
        } catch (Exception e) {
            log.warn("加载配置失败: {}", fileName, e);
        }
    }

    /** 环境变量覆盖（WEBMARK_xxx → webmark.xxx） */
    private void overrideFromEnv() {
        props.stringPropertyNames().forEach(key -> {
            String envKey = key.toUpperCase().replace('.', '_');
            String envVal = System.getenv(envKey);
            if (envVal != null) {
                Object old = props.setProperty(key, envVal);
                log.info("环境变量 [{}] 覆盖配置 [{}]: {} → {}", envKey, key, old, envVal);
            }
        });
    }

    // ==================== 配置项获取 ====================

    public int getPort() {
        return Integer.parseInt(props.getProperty("webmark.port", "8888"));
    }

    public String getDataDir() {
        return props.getProperty("webmark.dataDir", "./data");
    }

    public boolean isJsonPretty() {
        String val = props.getProperty("webmark.jsonPretty", "true");
        return !"false".equals(val);
    }

    public String getJwtSecret() {
        return props.getProperty("webmark.jwtSecret",
                "webmark-jwt-secret-key-2024-min-32bytes!!");
    }

    public long getJwtAccessExpiration() {
        return Long.parseLong(props.getProperty("webmark.jwtAccessExpiration", "1800000"));
    }

    public long getJwtRefreshExpiration() {
        return Long.parseLong(props.getProperty("webmark.jwtRefreshExpiration", "604800000"));
    }

    /** 获取当前环境名称（从配置文件或环境变量读取） */
    public String getEnv() {
        return props.getProperty("webmark.env", "dev");
    }

    /** 判断是否为生产环境 */
    public boolean isProd() {
        return "prod".equals(getEnv());
    }
}
