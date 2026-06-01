# Webmark 架构设计

## 一、整体架构

```
┌──────────────────────────────────────────────────────┐
│                  Thymeleaf Templates                  │
│  (index/login/register/search/home + admin/**)       │
└────────────┬──────────────────────────────┬──────────┘
             │ 服务端渲染                     │ AJAX/API 请求
     ┌───────▼────────┐              ┌───────▼────────┐
     │ IndexController │              │  ApiController │
     │  AuthController │              │ AdminController│
     └───────┬─────────┘              └───────┬────────┘
             │                                │
    ┌────────▼────────────────────────────────▼────────┐
    │                  Service Layer                     │
    │  BookmarkService / FolderService / UserService    │
    │  FavoritesService / UrlLibraryService             │
    └────────────────────┬─────────────────────────────┘
                         │
    ┌────────────────────▼─────────────────────────────┐
    │               DataStore (配置层)                   │
    │  管理 6 个 JsonFileRepository<T>                   │
    └────────────────────┬─────────────────────────────┘
                         │
    ┌────────────────────▼─────────────────────────────┐
    │         JsonFileRepository<T> (存储层)             │
    │  CRUD / 分页 / 排序 / 批量操作 / 逻辑删除          │
    │  CopyOnWriteArrayList + AtomicLong + flush()      │
    └────────────────────┬─────────────────────────────┘
                         │
    ┌────────────────────▼─────────────────────────────┐
    │            JSON 文件 (./data/)                     │
    │  users.json / bookmarks.json / folders.json       │
    │  favorites.json / url_library.json / config.json  │
    └──────────────────────────────────────────────────┘
```

### 安全架构

```
Request
  ┌── Authorization: Bearer <token>? ──▶ JwtAuthFilter (OncePerRequestFilter)
  │                                         │
  │                                    ┌────▼────┐
  │                                    │ 解析 JWT  │
  │                                    │ 设置认证  │
  │                                    └────┬────┘
  │                                         │
  └── Form POST /doLogin? ──────────▶ UsernamePasswordAuthenticationFilter
                                            │
                                     ┌──────▼──────┐
                                     │ SecurityConfig│
                                     │ URL 权限配置  │
                                     └──────┬──────┘
                                            │
                                     ┌──────▼──────────────┐
                                     │ CustomUserDetailsService│
                                     │ 按用户名/邮箱加载用户    │
                                     └─────────────────────┘
```

## 二、核心设计决策

### 2.1 零数据库 —— 为什么用 JSON 文件？

| 考量 | JSON 文件方案 | 传统数据库方案 |
|------|-------------|--------------|
| 部署复杂度 | 零依赖，复制即运行 | 需要安装数据库、创建库表 |
| 数据量 | 个人书签站，千级别 | 万级以上也能支撑 |
| 备份迁移 | 复制 JSON 文件即可 | 需要 dump/restore |
| 并发 | 单用户/少用户场景足够 | 多用户必需 |
| 内存占用 | 极低 | JPA 连接池等额外开销 |

**适用场景**：个人/小团队使用，数据量在 10000 条以内。

### 2.2 逻辑删除

所有实体（Bookmark、Folder、User）都包含 `deleted` 字段：

- 删除操作将 `deleted` 设为 `1`（逻辑删除），而非物理删除
- 所有查询方法默认过滤 `deleted=1` 的数据
- `JsonFileRepository` 通过反射统一处理 `deleted` 字段
- 提供 `physicalDeleteById()` 用于真正的物理删除

### 2.3 双认证机制

| 认证方式 | 适用场景 | 实现 |
|---------|---------|------|
| 表单登录 | 浏览器页面访问 | Spring Security Form Login |
| JWT Token | API 调用、Bookmarklet | `JwtAuthFilter` + `Authorization: Bearer` |

两者通过 `SecurityFilterChain` 合并到一个过滤器链中：
- JWT 过滤器先执行，从 Header 提取 Token
- 若无 Token，交给后续的表单认证处理
- CSRF 保护对 `/api/**` 豁免

### 2.4 种子数据初始化

`DataStore.init()` 在应用启动时执行：

```
启动 → 检查 data/users.json 是否为空
  ├── 为空 → 从 seed/ 目录加载种子数据 → 加密密码 → 写入 data/
  └── 不为空 → 跳过，直接使用已有数据
```

种子数据包含：
- 1 个管理员账号（admin/admin123）
- 49 个文件夹（4 个顶级分类 + 45 个子分类）
- 70 条预设书签（设计/前端/产品/运营资源）

## 三、核心组件详解

### 3.1 JsonFileRepository<T> —— 通用 JSON 仓库

这是替代传统 ORM 的核心基础设施，提供：

```
能力矩阵：
┌────────────┬──────────────────────────────────┐
│ 线程安全    │ CopyOnWriteArrayList + synchronized │
│ ID 生成    │ AtomicLong 自增                   │
│ 持久化      │ 写操作自动 flush() 到磁盘          │
│ 反射操作    │ 通过反射读写 id/deleted/createTime  │
│ 通用类型    │ 泛型 + Jackson 序列化              │
│ 逻辑删除    │ 反射设置 deleted 字段              │
└────────────┴──────────────────────────────────┘
```

关键方法：

| 方法 | 说明 |
|------|------|
| `save(T)` | 保存实体，自动生成 ID、设置时间戳 |
| `find(Predicate)` | Lambda 条件查询（通过 CopyOnWriteArrayList 遍历） |
| `page(pageNum, pageSize, predicate)` | 条件分页 |
| `update(T)` | 按 ID 更新（反射读取 ID） |
| `deleteById(Long)` | 逻辑删除（设置 deleted=1） |
| `updateAll(predicate, updater)` | 批量条件更新 |

### 3.2 DataStore —— 数据存储管理器

管理 6 个 `JsonFileRepository` 实例，每个对应一个 JSON 文件：

| Repository | 文件 | 用途 |
|-----------|------|------|
| `userRepo` | `users.json` | 用户数据 |
| `bookmarkRepo` | `bookmarks.json` | 书签数据 |
| `folderRepo` | `folders.json` | 文件夹树 |
| `favoritesRepo` | `favorites.json` | 收藏夹 |
| `urlLibraryRepo` | `url_library.json` | URL 库（去重收集） |
| `configRepo` | `config.json` | 系统配置 |

### 3.3 文件夹树形结构

文件夹实体支持自引用（`parentId` → `id`），在查询时动态构建树：

```
Folder {
    id, parentId, name, sortOrder, ...
    children: List<Folder>    // 非持久化字段，查询时注入
}
```

构建流程：
1. 从 JSON 读取所有平铺的 Folder 记录
2. 过滤 `parentId` 为 `null` 的作为根节点
3. 对每个节点递归查找子节点
4. 按 `sortOrder` 排序

### 3.4 导入导出

**导入**：解析浏览器导出的 HTML 书签文件（Netscape Bookmark Format）

```
HTML 文件 → Jsoup 解析 → HtmlUtil.parseTree() / parseFlat()
  ├── 树形模式：递归解析 <DL><DT><H3>...</H3><A>
  │   ├── H3 标签 → 创建文件夹
  │   └── A 标签  → 创建书签
  └── 平铺模式：提取所有 http/https 链接
```

**导出**：支持三种格式

| 格式 | 特点 |
|------|------|
| HTML | 浏览器可直接导入的 Netscape 格式 |
| JSON | 完整数据结构，含文件夹层级 |
| CSV | 纯表格数据（URL, 标题, 描述） |

## 四、页面路由设计

### 4.1 前台页面（无需登录）

| 路径 | 模板 | 说明 |
|------|------|------|
| `/` | `index.html` | 首页，浏览模式 + 管理模式双模式 |
| `/login` | `login.html` | 登录页 |
| `/register` | `register.html` | 注册页 |
| `/search` | `search.html` | 搜索结果页 |

### 4.2 管理后台（需登录）

| 路径 | 模板 | 说明 |
|------|------|------|
| `/admin/bookmark/list` | `admin/bookmark/list.html` | 书签管理（左树右表） |
| `/admin/icon/list` | `admin/icon/list.html` | 图标浏览管理 |
| `/admin/tool/collect` | `admin/tool/collect.html` | Bookmarklet 工具 |
| `/admin/tool/import` | `admin/tool/import.html` | 导入书签 |
| `/admin/tool/export` | `admin/tool/export.html` | 导出书签 |
| `/admin/user/list` | `admin/user/list.html` | 用户管理 |
| `/admin/config` | `admin/config.html` | 系统配置 |

## 五、数据流示例

### 创建书签流程

```
用户点击"新建书签"
  → 模态框填写 title/url/folderId
  → POST /api/admin/bookmarks (AJAX, JWT 认证)
  → ApiController.createBookmark()
  → BookmarkService → Bookmark.setUserId/deleted/sortOrder
  → JsonFileRepository.save(bookmark)
    → 生成 ID → 设置时间 → 加入列表 → flush()
  → 返回 JSON 响应
  → 前端刷新书签列表
```

### 级联删除文件夹流程

```
用户点击删除文件夹
  → 前端先检查子内容数量
  → 空文件夹：直接 POST /api/folders/{id}/delete
  → 含子内容：弹出二次确认 → force=true
  → FolderService.removeCascade(folderId)
    → getDescendantIds(folderId) 获取所有子孙文件夹 ID
    → BookmarkService.removeByFolderIds() 逻辑删除书签
    → 逐个逻辑删除文件夹
  → 前台刷新
```

## 六、配置说明

### 开发环境（dev）

```yaml
server.port: 8888
spring.thymeleaf.cache: false      # 关闭缓存，修改模板即时生效
logging.level.com.cloud.self.webmark: debug
webmark.data.dir: ./data           # JSON 数据目录
```

### 生产环境（pro）

```yaml
server.port: 8889
spring.thymeleaf.cache: true       # 开启缓存，提升性能
logging.level.com.cloud.self.webmark: info
webmark.data.dir: ./data
```

## 七、扩展方向

| 方向 | 优先级 | 说明 |
|------|--------|------|
| viewCount/collectCount | 高 | 书签浏览量和收藏量统计 |
| 拖拽排序持久化 | 高 | 首页书签卡片拖拽排序 |
| 全文搜索优化 | 中 | 引入倒排索引提升搜索性能 |
| 多用户协作 | 中 | 支持分享文件夹给其他用户 |
| 数据备份恢复 | 低 | 一键备份/恢复 JSON 数据 |
| Docker 部署 | 低 | 容器化部署方案 |
