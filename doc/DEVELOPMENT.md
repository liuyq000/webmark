# Webmark 开发指南

## 一、环境准备

| 工具 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 17+ | 推荐 17 |
| Maven | 3.6+ | 构建工具 |
| IDE | IntelliJ IDEA | 推荐 |
| Git | 2.x | 版本管理 |

## 二、项目启动

### 2.1 克隆与编译

```bash
git clone <repo-url>
cd webmark
mvn clean compile
```

### 2.2 启动方式

```bash
# 方式一：Maven 命令
mvn spring-boot:run

# 方式二：IDE 中运行 WebmarkApplication.main()
```

默认激活 `dev` profile，端口 **8888**。

### 2.3 首次启动

首次启动时，系统检测 `data/users.json` 是否为空：
- **为空**：自动从 `src/main/resources/seed/` 加载种子数据
- **不为空**：使用已有数据

如需**重置数据**，删除 `data/` 目录后重启即可。

## 三、项目热开发

### 3.1 模板热更新

`dev` 环境下 `spring.thymeleaf.cache=false`，修改 Thymeleaf 模板后刷新浏览器即可生效，无需重启。

### 3.2 Java 代码热更新

推荐使用 IntelliJ IDEA 的 `Update classes and resources`（Ctrl+F10），或搭配 Spring DevTools：

```xml
<!-- pom.xml 中添加 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

### 3.3 调试技巧

**查看 JSON 数据**：直接打开 `data/` 目录下的 JSON 文件查看当前数据状态。

**清除数据重置**：
```bash
rm -rf data/
# 重启后自动从 seed/ 加载
```

**临时测试端点**：可在 Controller 中添加 `@GetMapping("/debug/xxx")` 临时调试端点，验证后删除。

## 四、添加新功能

### 4.1 添加新实体

1. 在 `entity/` 下创建实体类（含 `id` 和 `deleted` 字段）
2. 在 `DataStore.java` 中创建对应的 `JsonFileRepository<T>` 实例
3. 如有种子数据，在 `seed/` 下添加 JSON 文件

```java
// 实体类示例
@Data
public class NewEntity {
    private Long id;
    private String name;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

### 4.2 添加新 API

1. 创建 `XxxService.java`（业务逻辑）
2. 在 `ApiController.java` 或新建 Controller 中添加端点
3. 如果用 JWT 认证，前端需携带 `Authorization: Bearer <token>`

```java
// 服务层
@Service
public class NewService {
    private final JsonFileRepository<NewEntity> repo;
    // ...
}

// 控制器
@PostMapping("/api/new-resource")
public ResponseEntity<?> create(@RequestBody NewEntity entity) {
    // ...
}
```

### 4.3 添加新页面

1. 在 `templates/` 下创建 Thymeleaf 模板
2. 在 Controller 中添加页面路由
3. 纯静态资源放 `static/` 目录

```html
<!-- src/main/resources/templates/new-page.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>新页面</title>
    <link rel="stylesheet" th:href="@{/webjars/bootstrap/css/bootstrap.min.css}">
</head>
<body>
    <div class="container">
        <h1>新页面</h1>
    </div>
</body>
</html>
```

```java
@GetMapping("/new-page")
public String newPage() {
    return "new-page";
}
```

### 4.4 添加前端交互

项目的前端 JavaScript 分为两种组织形式：

1. **全局脚本**：`static/js/main.js`（前台）、`static/js/admin.js`（后台）
2. **内嵌脚本**：复杂页面（如 `index.html` 的管理模式、`bookmark/list.html`）的 JS 直接内嵌在模板中

推荐新页面的 JS 逻辑内嵌在模板中，避免全局脚本膨胀。

## 五、JsonFileRepository 使用指南

这是项目的核心存储层，无需 SQL，全部通过 Lambda 表达式操作。

### 5.1 基本 CRUD

```java
// 保存（自动生成 ID、时间戳）
Bookmark bookmark = new Bookmark();
bookmark.setTitle("新书签");
bookmarkRepo.save(bookmark);

// 查询单个
Optional<Bookmark> opt = bookmarkRepo.findById(1L);

// 条件查询
List<Bookmark> publicList = bookmarkRepo.find(b -> b.getPublicType() == 1);

// 更新
Bookmark toUpdate = bookmarkRepo.findById(1L).get();
toUpdate.setTitle("新标题");
bookmarkRepo.update(toUpdate);

// 逻辑删除
bookmarkRepo.deleteById(1L);

// 物理删除
bookmarkRepo.physicalDeleteById(1L);
```

### 5.2 分页查询

```java
// 基本分页
PageResult<Bookmark> page = bookmarkRepo.page(1, 20, b -> b.getPublicType() == 1);

// 排序分页
PageResult<Bookmark> sortedPage = bookmarkRepo.pageOrderBy(
    1, 20, null,
    Comparator.comparing(Bookmark::getSortOrder).thenComparing(Bookmark::getCreateTime)
);

// 分页结果转换
PageResult<Map<String, Object>> dto = page.convert(b -> {
    Map<String, Object> map = new HashMap<>();
    map.put("id", b.getId());
    map.put("title", b.getTitle());
    return map;
});
```

### 5.3 批量操作

```java
// 批量保存
bookmarkRepo.saveAll(bookmarkList);

// 批量条件更新
bookmarkRepo.updateAll(
    b -> b.getFolderId() == 5,
    b -> b.setPublicType(0)
);
```

## 六、文件夹树操作

### 6.1 构建文件夹树

```java
// 获取用户的文件夹树（含公共文件夹）
List<Folder> tree = folderService.listTreeByUserId(userId);

// 获取纯公共文件夹树
List<Folder> publicTree = folderService.listPublicTree();

// 获取指定文件夹的子树
List<Folder> subtree = folderService.listTree(parentId);
```

### 6.2 递归获取子文件夹

```java
// 获取某个文件夹的所有后代（用于级联操作）
List<Long> descendantIds = folderService.getDescendantIds(folderId);

// 结果：[10, 11, 12]  // folderId=10 及其所有子文件夹
```

## 七、安全相关

### 7.1 获取当前登录用户

```java
// 在 Controller 方法中
@GetMapping("/profile")
public String profile(@AuthenticationPrincipal UserDetails userDetails) {
    String username = userDetails.getUsername();
    // ...
}
```

### 7.2 Thymeleaf 模板中的安全标签

```html
<!-- 仅登录用户可见 -->
<div sec:authorize="isAuthenticated()">
    欢迎，<span sec:authentication="name">用户名</span>
</div>

<!-- 仅管理员可见 -->
<div sec:authorize="hasRole('ADMIN')">
    管理后台入口
</div>

<!-- 未登录用户可见 -->
<div sec:authorize="!isAuthenticated()">
    <a href="/login">登录</a>
</div>
```

### 7.3 JWT Token 使用

前端请求管理 API 时：

```javascript
// 登录后存储 token
const token = loginResponse.token;

// 后续请求携带 token
fetch('/api/admin/bookmarks?page=1', {
    headers: {
        'Authorization': `Bearer ${token}`
    }
}).then(res => res.json());
```

## 八、静态资源说明

### 8.1 WebJars

Bootstrap 和 Bootstrap Icons 通过 WebJars 内嵌，无需 CDN：

```html
<!-- CSS -->
<link rel="stylesheet" th:href="@{/webjars/bootstrap/css/bootstrap.min.css}">

<!-- JS -->
<script th:src="@{/webjars/bootstrap/js/bootstrap.bundle.min.js}"></script>

<!-- 图标 -->
<link rel="stylesheet" th:href="@{/webjars/bootstrap-icons/font/bootstrap-icons.css}">
```

### 8.2 图标分类

`admin/icon/list.html` 页面内置图标分类数据，支持按类别筛选 Bootstrap Icons：

- 箭头类 (Arrows)
- 文件类 (Files)
- 表单类 (Forms)
- 媒体类 (Media)
- 社交类 (Social)
- 等等...

## 九、常见问题

### Q: 修改了种子数据，重启后没有生效？

A: 种子数据只在 `data/users.json` 为空时加载。需要先删除 `data/` 目录再重启。

### Q: 如何备份数据？

A: 直接复制 `data/` 目录即可。`data/` 已被 `.gitignore` 排除，不会被提交到 Git。

### Q: 导入书签时中文乱码？

A: 确保导入的 HTML 文件编码为 UTF-8。浏览器导出的书签通常是 UTF-8。

### Q: JWT Token 过期了怎么办？

A: 默认有效期 24 小时。重新调用 `/api/auth/login` 获取新 Token。

### Q: 如何添加新的前端图标？

A: 项目使用 Bootstrap Icons，访问 https://icons.getbootstrap.com 搜索图标名，在模板中使用 `<i class="bi bi-icon-name"></i>` 即可。

## 十、代码规范

### 10.1 实体字段规范

所有持久化实体必须包含以下字段：

```java
private Long id;
private LocalDateTime createTime;
private LocalDateTime updateTime;
private Integer deleted;  // 0=正常, 1=已删除
```

### 10.2 命名约定

| 类型 | 约定 | 示例 |
|------|------|------|
| 包名 | 小写 | `com.cloud.self.webmark` |
| 类名 | PascalCase | `BookmarkService` |
| 方法名 | camelCase | `findByUserId` |
| 常量 | UPPER_SNAKE | `MAX_PAGE_SIZE` |
| 模板文件 | kebab-case | `bookmark-list.html` |
| CSS 类 | BEM 风格 | `.folder-tree__item--active` |
