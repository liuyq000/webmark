# Webmark — 本地书签导航站

一个基于 **Spring Boot 3.5 + Thymeleaf + Bootstrap 5** 的轻量级书签管理与资源发现平台。**零数据库依赖**，采用 JSON 文件存储，开箱即用。

## 特性

| 特性 | 说明 |
|------|------|
| 🚀 **零数据库** | JSON 文件存储，无需安装 MySQL/Redis，解压即用 |
| 🔐 **双认证** | 同时支持表单登录（页面）和 JWT Token（API） |
| 📂 **文件夹管理** | 树形文件夹结构，支持三级嵌套、拖拽排序 |
| 🔖 **书签 CRUD** | 增删改查、搜索过滤、分页、公开/私密切换 |
| 📥 **导入导出** | 支持 HTML（Netscape 格式）、JSON、CSV 三种格式 |
| 🔗 **Bookmarklet** | 浏览器书签小工具，一键收藏网页 |
| 🎨 **响应式设计** | Bootstrap 5 + 自定义 CSS，适配桌面和移动端 |
| 🌱 **种子数据** | 首次启动自动加载 70 条预设书签 + 49 个分类文件夹 |
| 🖼 **图标管理** | 内置 Bootstrap Icons 浏览和复制 |

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.5.14 |
| 模板引擎 | Thymeleaf | — |
| 安全 | Spring Security + JWT | jjwt 0.12.5 |
| 前端 | Bootstrap 5 (WebJars) | 5.3.3 |
| 图标 | Bootstrap Icons (WebJars) | 1.11.3 |
| HTML 解析 | Jsoup | 1.18.1 |
| JSON | Jackson + jsr310 | — |
| 构建 | Maven | — |

## 快速开始

### 前置要求

- JDK 17+
- Maven 3.6+

### 启动

```bash
# 克隆项目
git clone <your-repo-url>
cd webmark

# 启动（开发环境，端口 8888）
mvn spring-boot:run

# 或指定生产环境（端口 8889）
mvn spring-boot:run -Dspring-boot.run.profiles=pro
```

### 访问

| 地址 | 说明 |
|------|------|
| `http://localhost:8888` | 首页（浏览模式） |
| `http://localhost:8888/admin/bookmark/list` | 管理后台（需登录） |

### 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | ROLE_ADMIN |

> 首次启动时，系统会自动加载种子数据（70 条书签 + 49 个文件夹）。数据存储在 `./data/` 目录下的 JSON 文件中。

## 项目结构

```
webmark/
├── data/                           # 运行时数据（gitignore）
│   ├── bookmarks.json
│   ├── folders.json
│   └── users.json
├── src/main/java/com/cloud/self/webmark/
│   ├── WebmarkApplication.java     # 启动类
│   ├── config/                     # 配置（DataStore + SecurityConfig）
│   ├── controller/                 # 控制器（前台/后台/API）
│   ├── entity/                     # 实体（Bookmark/Folder/User/...）
│   ├── security/                   # 安全（JWT + Filter + UserDetails）
│   ├── service/                    # 业务服务
│   ├── store/                      # JSON 文件仓库 + 分页
│   └── utils/                      # HTML 书签解析
├── src/main/resources/
│   ├── application.yaml            # 主配置
│   ├── application-dev.yaml        # 开发环境
│   ├── application-pro.yaml        # 生产环境
│   ├── seed/                       # 种子数据
│   ├── static/                     # CSS/JS/Images
│   └── templates/                  # Thymeleaf 模板
└── pom.xml
```

## 文档

| 文档 | 说明 |
|------|------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | 架构设计与核心原理 |
| [API.md](API.md) | REST API 接口文档 |
| [DEVELOPMENT.md](DEVELOPMENT.md) | 开发指南 |

## License

MIT
