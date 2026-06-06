package com.cloud.self.webmark.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class CssEditorService {

    private static final Logger log = LoggerFactory.getLogger(CssEditorService.class);

    public void saveCssChanges(Map<String, Map<String, String>> changes) throws IOException {
        Path srcCss = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "static", "css", "style.css");
        Path targetCss = Paths.get(System.getProperty("user.dir"), "target", "classes", "static", "css", "style.css");

        if (!Files.exists(srcCss)) {
            throw new IllegalArgumentException("style.css 文件不存在");
        }

        String content = Files.readString(srcCss);
        StringBuilder newContent = new StringBuilder(content);

        for (Map.Entry<String, Map<String, String>> entry : changes.entrySet()) {
            String selector = entry.getKey();
            Map<String, String> props = entry.getValue();

            int blockStart = findCssBlockStart(newContent.toString(), selector);
            if (blockStart >= 0) {
                int blockEnd = findCssBlockEnd(newContent.toString(), blockStart);
                String originalBlock = newContent.substring(blockStart, blockEnd);
                String updatedBlock = updateCssBlock(originalBlock, props);
                String before = newContent.substring(0, blockStart);
                String after = newContent.substring(blockEnd);
                newContent = new StringBuilder(before + updatedBlock + after);
            } else {
                StringBuilder newBlock = new StringBuilder();
                newBlock.append("\n").append(selector).append(" {\n");
                for (Map.Entry<String, String> prop : props.entrySet()) {
                    newBlock.append("    ").append(prop.getKey()).append(": ").append(prop.getValue()).append(";\n");
                }
                newBlock.append("}\n");
                newContent.append(newBlock);
            }
        }

        Files.writeString(srcCss, newContent.toString());
        Files.createDirectories(targetCss.getParent());
        Files.writeString(targetCss, newContent.toString());

        log.info("已保存 {} 个选择器的 CSS 变更", changes.size());
    }

    private int findCssBlockStart(String content, String selector) {
        int idx = content.indexOf(selector + " {");
        if (idx >= 0) return idx;
        return -1;
    }

    private int findCssBlockEnd(String content, int start) {
        int depth = 0;
        boolean started = false;
        for (int i = start; i < content.length(); i++) {
            if (content.charAt(i) == '{') { started = true; depth++; }
            if (content.charAt(i) == '}') { depth--; if (depth <= 0 && started) return i + 1; }
        }
        return content.length();
    }

    private String updateCssBlock(String block, Map<String, String> props) {
        String result = block;
        for (Map.Entry<String, String> prop : props.entrySet()) {
            String propName = prop.getKey();
            String propValue = prop.getValue();
            int propIdx = result.indexOf(propName + ":");
            if (propIdx >= 0) {
                int valStart = result.indexOf(":", propIdx) + 1;
                int valEnd = findCssValueEnd(result, valStart);
                String oldDecl = result.substring(propIdx, valEnd);
                String newDecl = propName + ": " + propValue;
                result = result.replace(oldDecl, newDecl);
            } else {
                int insertPos = result.lastIndexOf('}');
                result = result.substring(0, insertPos) + "    " + propName + ": " + propValue + ";\n" + result.substring(insertPos);
            }
        }
        return result;
    }

    private int findCssValueEnd(String block, int start) {
        int i = start;
        while (i < block.length()) {
            char c = block.charAt(i);
            if (c == ';') return i + 1;
            i++;
        }
        return block.length();
    }
}
