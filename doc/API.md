# Webmark REST API 文档

## 通用说明

- **Base URL**: `http://localhost:8888`
- **认证方式**: 公共 API 无需认证；管理 API 需要 JWT Token
- **JWT Header**: `Authorization: Bearer <token>`
- **Content-Type**: `application/json`

---

## 一、认证 API

### 1.1 注册

```
POST /register
```

**请求体** (form-urlencoded):
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userName | String | 是 | 用户名（唯一） |
| email | String | 是 | 邮箱（唯一） |
| password | String | 是 | 密码（BCrypt 加密存储） |

**响应**: 重定向到登录页（带注册成功提示）

---

### 1.2 JWT 登录

```
POST /api/auth/login
```

**请求体** (JSON):
```json
{
    "userName": "admin",
    "password": "admin123"
}
```

**成功响应** (200):
```json
{
    "code": 200,
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userName": "admin",
    "role": "ROLE_ADMIN"
}
```

**失败响应** (401):
```json
{
    "code": 401,
    "message": "用户名或密码错误"
}
```

---

## 二、公开 API（无需认证）

### 2.1 查询书签列表

```
GET /api/bookmarks?folderId={folderId}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| folderId | Long | 否 | 文件夹 ID，不传返回全部公开书签 |

**响应**:
```json
[
    {
        "id": 1,
        "title": "Dribbble",
        "url": "https://dribbble.com",
        "description": "设计灵感社区",
        "logoUrl": null,
        "tags": "设计,灵感",
        "folderId": 5,
        "publicType": 1,
        "sortOrder": 0,
        "createTime": "2025-01-01 12:00:00"
    }
]
```

---

### 2.2 查询文件夹树

```
GET /api/folders
```

**响应**:
```json
[
    {
        "id": 1,
        "name": "设计",
        "parentId": null,
        "sortOrder": 1,
        "children": [
            {
                "id": 5,
                "name": "灵感采集",
                "parentId": 1,
                "children": []
            }
        ]
    }
]
```

---

### 2.3 搜索书签

```
GET /api/search?key={keyword}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| key | String | 是 | 搜索关键词（匹配标题、描述、标签） |

**响应**: 同书签列表格式

---

## 三、管理 API（需要 JWT 认证）

> 所有管理 API 需要在 Header 中携带 `Authorization: Bearer <token>`

### 3.1 分页查询书签

```
GET /api/admin/bookmarks?page=1&size=20&keyword=&folderId=&publicType=
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | int | 否 | 1 | 页码 |
| size | int | 否 | 20 | 每页条数（支持 10/20/50/100） |
| keyword | String | 否 | — | 搜索关键词 |
| folderId | Long | 否 | — | 文件夹 ID（含子文件夹书签） |
| publicType | Integer | 否 | — | 0=私密, 1=公开 |

**响应**:
```json
{
    "records": [ ... ],
    "total": 70,
    "current": 1,
    "size": 20,
    "pages": 4
}
```

---

### 3.2 创建书签

```
POST /api/admin/bookmarks
```

**请求体**:
```json
{
    "title": "新书签",
    "url": "https://example.com",
    "description": "描述",
    "tags": "标签1,标签2",
    "folderId": 5,
    "publicType": 1
}
```

**成功响应** (200):
```json
{
    "code": 200,
    "message": "创建成功",
    "data": { "id": 71, ... }
}
```

---

### 3.3 更新书签

```
PUT /api/admin/bookmarks/{id}
```

**请求体**: 同创建书签

**响应**:
```json
{
    "code": 200,
    "message": "更新成功"
}
```

---

### 3.4 删除书签

```
DELETE /api/admin/bookmarks/{id}
```

**响应**:
```json
{
    "code": 200,
    "message": "删除成功"
}
```

---

### 3.5 创建文件夹

```
POST /api/folders
```

**请求体**:
```json
{
    "name": "新文件夹",
    "parentId": 1,
    "sortOrder": 99,
    "icon": "bi-folder"
}
```

**响应**:
```json
{
    "code": 200,
    "message": "创建成功",
    "data": { "id": 50, "name": "新文件夹", "parentId": 1 }
}
```

---

### 3.6 更新文件夹

```
PUT /api/folders/{id}
```

**请求体**:
```json
{
    "id": 50,
    "name": "改名后的文件夹",
    "parentId": null,
    "sortOrder": 10
}
```

---

### 3.7 删除文件夹（支持级联）

```
DELETE /api/folders/{id}?force={true|false}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| force | boolean | 否 | 是否强制级联删除（默认 false） |

**行为**:
- `force=false` 且文件夹为空 → 直接删除
- `force=false` 且文件夹含子内容 → 返回错误，提示需要确认
- `force=true` → 级联删除所有子孙文件夹和关联书签

**成功响应**:
```json
{
    "code": 200,
    "message": "删除成功"
}
```

**需要确认响应**:
```json
{
    "code": 409,
    "message": "文件夹下有 5 个子文件夹和 12 个书签，确认删除？"
}
```

---

### 3.8 批量排序

**书签排序**:
```
PUT /api/admin/sort/bookmarks
```

**请求体**:
```json
[
    { "id": 1, "sortOrder": 0 },
    { "id": 3, "sortOrder": 1 },
    { "id": 2, "sortOrder": 2 }
]
```

**文件夹排序**:
```
PUT /api/admin/sort/folders
```

请求体格式同上。

---

### 3.9 导入书签

```
POST /admin/tool/import
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | MultipartFile | 是 | HTML 书签文件 |
| mode | String | 是 | `tree` 树形 / `flat` 平铺 |
| isPrivate | boolean | 否 | 是否导入为私密书签 |

**响应**: 重定向到管理页面并显示导入统计

---

### 3.10 导出书签

```
GET /admin/tool/export/download?format={html|json|csv}&ids={id1,id2,...}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| format | String | 是 | 导出格式：html/json/csv |
| ids | String | 否 | 文件夹 ID 列表（逗号分隔），不传则导出全部 |

**响应**: 文件下载

---

## 四、错误码

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 401 | 未认证 / Token 无效 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 冲突（如删除非空文件夹需要确认） |
| 500 | 服务器内部错误 |

---

## 五、实体字段参考

### Bookmark

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| userId | Long | 所属用户 ID |
| folderId | Long | 所属文件夹 ID |
| favoritesId | Long | 收藏夹 ID（导入时使用） |
| title | String | 标题 |
| url | String | 链接地址 |
| description | String | 描述 |
| logoUrl | String | 图标 URL |
| tags | String | 标签（逗号分隔） |
| sortOrder | Integer | 排序序号 |
| publicType | Integer | 0=私密, 1=公开 |
| reviewStatus | Integer | 审核状态 |
| createTime | LocalDateTime | 创建时间 |
| updateTime | LocalDateTime | 更新时间 |
| deleted | Integer | 0=正常, 1=已删除 |

### Folder

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| userId | Long | 所属用户 |
| parentId | Long | 父文件夹 ID |
| name | String | 名称 |
| nameEn | String | 英文名称 |
| icon | String | 图标类名 |
| sortOrder | Integer | 排序 |
| children | List\<Folder\> | 子文件夹（非持久化） |
