package com.cloud.self.webmark.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTML 书签文件解析工具
 * 支持浏览器导出的 Netscape Bookmark File Format
 */
public class HtmlUtil {

    /** 树形书签节点 */
    public static class BookmarkNode {
        public String name;
        public final Map<String, String> links = new LinkedHashMap<>(); // url → title
        public final List<BookmarkNode> children = new ArrayList<>();
    }

    /**
     * 平铺解析：提取所有 http 链接，不保留目录结构
     */
    public static Map<String, String> parseFlat(InputStream in) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        Document doc = Jsoup.parse(in, "UTF-8", "");
        Elements links = doc.select("a[href]");
        for (Element a : links) {
            String href = a.attr("href").trim();
            if (href.startsWith("http://") || href.startsWith("https://")) {
                String title = a.text().trim();
                if (title.isEmpty()) title = href;
                if (!result.containsKey(href)) {
                    result.put(href, title);
                }
            }
        }
        return result;
    }

    /**
     * 树形解析：完整保留目录层级关系
     * 返回根级别的书签节点列表
     */
    public static List<BookmarkNode> parseTree(InputStream in) throws Exception {
        Document doc = Jsoup.parse(in, "UTF-8", "");
        Elements rootDls = doc.select("dl");
        List<BookmarkNode> roots = new ArrayList<>();
        if (!rootDls.isEmpty()) {
            parseDl(rootDls.first(), roots);
        }
        return roots;
    }

    /** 递归解析一个 <dl> 下的所有 <dt> */
    private static void parseDl(Element dl, List<BookmarkNode> nodes) {
        List<Element> dtList = new ArrayList<>();
        for (Element child : dl.children()) {
            if ("dt".equals(child.tagName())) {
                dtList.add(child);
            }
        }
        for (Element dt : dtList) {
            Element h3 = dt.selectFirst("h3");
            if (h3 != null) {
                // 有 h3 → 这是一个文件夹节点
                BookmarkNode node = new BookmarkNode();
                node.name = h3.text().trim();
                // 文件夹内的 <a> 链接（直接子级，不含子文件夹内）
                Elements directLinks = dt.select("> dl > dt > a[href]");
                for (Element a : directLinks) {
                    String href = a.attr("href").trim();
                    if (href.startsWith("http://") || href.startsWith("https://")) {
                        String title = a.text().trim();
                        if (title.isEmpty()) title = href;
                        node.links.putIfAbsent(href, title);
                    }
                }
                // 递归子文件夹
                Element childDl = dt.selectFirst("> dl");
                if (childDl != null) {
                    parseDl(childDl, node.children);
                }
                if (!node.links.isEmpty() || !node.children.isEmpty()) {
                    nodes.add(node);
                }
            } else {
                // 无 h3 → 可能是直接放在根级的链接
                Elements links = dt.select("a[href]");
                // 忽略直接根级链接，一般不会出现
            }
        }
    }
}
