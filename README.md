# Webmark — 本地书签导航站

一个轻量级的私人书签管理与导航系统，基于 **Spring Boot 3 + Thymeleaf** 构建，**零外部数据库依赖**，数据全部存储于 JSON 文件。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端框架 | Spring Boot 3.5 + Spring Security |
| 模板引擎 | Thymeleaf (服务器端渲染) |
| 数据存储 | JSON 文件（自研泛型仓库 `JsonFileRepository`） |
| 前端 | Bootstrap 5.3 + Bootstrap Icons + 原生 JavaScript |
| 认证 | Session（表单登录）+ JWT（API Bearer Token）双模式 |
| 书签解析 | Jsoup |
| 构建 | Maven + Java 17 |

## 架构特点

### 零数据库依赖

项目使用自研的 `JsonFileRepository<E>` 泛型仓库替代传统数据库。每个实体对应一个 JSON 文件（`data/users.json`、`data/bookmarks.json`、`data/folders.json`），通过反射实现通用 CRUD、分页、排序、逻辑删除。适合个人或小团队轻量部署，数据文件可直接备份或版本控制。

### 认证双通道

- 页面访问：Session + Cookie 表单登录
- API 访问：JWT（Access Token 30 分钟 + Refresh Token 7 天）
- `JwtAuthFilter` 在 UsernamePasswordAuthenticationFilter 之前执行，有 token 走 token，没有则走 session

## 核心实体

- **User** — 用户（支持多用户，管理员/普通用户角色）
- **Folder** — 文件夹（无限级树形结构，支持公共文件夹 `userId=null`）
- **Bookmark** — 书签（标题、URL、描述、logo、标签、公开/私密、审核状态）
- **Config** — 用户配置

## 功能模块

### 书签浏览与导航
- 左侧侧边栏显示文件夹树结构
- 鼠标悬浮侧边栏项弹出浮层，展示该分类下的书签列表
- 书签显示网站 Logo、标题，点击新窗口打开

### 书签管理
- 完整 CRUD：新建、编辑、删除书签
- 拖拽排序（表格行拖拽 + 文件夹树拖拽）
- 按文件夹、关键词、公开/私密筛选
- 支持分页

### 文件夹管理
- 无限级树形结构
- 完整 CRUD 操作
- 级联删除确认（检查子文件夹和书签数量）
- 拖拽排序

### 搜索
- 实时搜索弹窗（输入即搜，300ms 防抖）
- 搜索范围：标题、描述、标签
- 搜索结果高亮关键词

### 导入 / 导出
- **导入**：支持浏览器导出的 Netscape HTML 书签文件格式（平铺导入或保留目录结构）
- **导出**：支持 HTML（Netscape 格式）、JSON、CSV 三种格式
- 导入后后台批量抓取 favicon

### Favicon 自动抓取
- 后台线程异步下载网站 favicon
- 缓存到 `data/favicons/` 目录复用
- 策略：先尝试 `domain/favicon.ico`，失败则用 Jsoup 解析页面 `<link rel="icon">`

### 网页收集工具 (Bookmarklet)
- 拖拽到浏览器书签栏的小工具
- 点击一键将当前网页收藏到 Webmark（自动提取标题和描述）

### 暗色主题
- 基于 CSS 变量 + `data-theme` 属性切换
- 跟随系统偏好或手动切换，配置持久化到 `localStorage`

### CSS 可视化编辑器
- 运行时解析并修改 `style.css`
- 支持增删改 CSS 选择器及其属性

### Bootstrap Icons 图标浏览器
- 内置弹窗浏览 2100+ Bootstrap Icons
- 支持分类筛选、关键词搜索、点击复制

## 数据存储结构

```
data/
├── users.json          # 用户数据
├── bookmarks.json      # 书签数据
├── folders.json        # 文件夹数据
├── config.json         # 用户配置
└── favicons/           # favicon 缓存目录
```

## 快速启动

### 前置条件
- JDK 17+
- Maven 3.6+

### 运行

```bash
# 克隆项目
git clone <repo-url>
cd webmark

# 编译运行
mvn spring-boot:run

# 打包后运行
mvn package -DskipTests
java -jar target/webmark-0.0.1-SNAPSHOT.jar
```

### 访问

- 首页：`http://localhost:8080/`
- 默认用户：admin / admin123（以实际种子数据为准）

## 配置说明

主配置文件 `src/main/resources/application.yaml`，开发环境配置 `application-dev.yaml`，生产环境配置 `application-pro.yaml`。

| 配置项 | 说明 | 默认值 |
|---|---|---|
| `webmark.data.dir` | 数据文件目录 | `./data` |
| `webmark.jwt.secret` | JWT 签名密钥 | `webmark-jwt-secret-key-2024-min-32bytes!!` |
| `webmark.jwt.access-expiration-ms` | Access Token 有效期 | 1800000 (30 分钟) |
| `webmark.jwt.refresh-expiration-ms` | Refresh Token 有效期 | 604800000 (7 天) |

## 项目结构

```
src/main/java/com/cloud/self/webmark/
├── WebmarkApplication.java          # 启动类
├── config/
│   ├── DataStore.java               # 数据存储管理器（初始化种子数据）
│   └── SecurityConfig.java          # Spring Security 配置
├── controller/
│   ├── IndexController.java         # 页面路由（首页、搜索、登录、注册）
│   ├── AuthController.java          # 注册处理
│   ├── ApiController.java           # REST API（书签、文件夹、认证、CSS）
│   └── AdminController.java         # 管理功能（导入导出、用户管理）
├── entity/
│   ├── Bookmark.java                # 书签实体
│   ├── Folder.java                  # 文件夹实体
│   ├── User.java                    # 用户实体
│   └── Config.java                  # 配置实体
├── security/
│   ├── CustomUserDetailsService.java # Spring Security 用户加载
│   ├── JwtUtil.java                 # JWT 生成与校验
│   └── JwtAuthFilter.java           # JWT 认证过滤器
├── service/
│   ├── BookmarkService.java         # 书签业务逻辑
│   ├── FolderService.java           # 文件夹业务逻辑
│   ├── UserService.java             # 用户业务逻辑
│   └── FaviconService.java          # Favicon 抓取服务
├── store/
│   ├── JsonFileRepository.java      # JSON 文件泛型仓库
│   └── PageResult.java              # 分页结果封装
└── utils/
    └── HtmlUtil.java                # HTML 书签文件解析工具
```

```
src/main/resources/
├── application.yaml                 # 主配置
├── application-dev.yaml             # 开发环境配置
├── application-pro.yaml             # 生产环境配置
├── static/
│   ├── css/
│   │   ├── style.css                # 主样式
│   │   └── drag-editor.css          # 拖拽编辑器样式
│   ├── js/
│   │   ├── main.js                  # 主 JS（JWT 注入、搜索、浮层、主题）
│   │   └── drag-editor.js           # 拖拽编辑器 JS
│   └── images/                      # 静态图片
├── templates/
│   ├── index.html                   # 主页模板
│   ├── login.html                   # 登录页
│   ├── register.html                # 注册页
│   ├── addbookmark.html             # 书签添加页
│   ├── search.html                  # 搜索页
│   └── fragments/                   # Thymeleaf 片段
│       ├── browse-panel.html        # 浏览面板
│       ├── mgmt-panel.html          # 管理面板
│       ├── user-panel.html          # 用户管理面板
│       ├── bookmark-modals.html     # 书签弹窗
│       ├── folder-modals.html       # 文件夹弹窗
│       ├── tool-modals.html         # 工具弹窗（导入导出）
│       ├── user-modals.html         # 用户弹窗
│       └── collect-tool.html        # 网页收集工具
└── seed/                            # 种子数据
    ├── users.json
    ├── folders.json
    └── bookmarks.json
```
