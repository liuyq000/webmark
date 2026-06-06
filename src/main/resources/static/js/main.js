
// ========== JWT 自动注入 + 刷新 ==========
(function() {
    var originalFetch = window.fetch;
    var refreshPromise = null;
    window.fetch = function(url, options) {
        options = options || {};
        options.headers = options.headers || {};
        var token = localStorage.getItem('token');
        if (token && typeof url === 'string' && url.startsWith('/api/') && !url.includes('/api/auth/')) {
            options.headers['Authorization'] = 'Bearer ' + token;
        }
        return originalFetch(url, options).then(function(response) {
            if (response.status === 401 && typeof url === 'string' && url.startsWith('/api/') && !url.includes('/api/auth/')) {
                if (!refreshPromise) {
                    var rt = localStorage.getItem('refreshToken');
                    if (!rt) { window.location.href = '/login'; return response; }
                    refreshPromise = originalFetch('/api/auth/refresh', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ refreshToken: rt })
                    }).then(function(r) { return r.json(); }).then(function(d) {
                        if (d.success) {
                            localStorage.setItem('token', d.token);
                            localStorage.setItem('refreshToken', d.refreshToken);
                            refreshPromise = null;
                            return true;
                        }
                        localStorage.removeItem('token');
                        localStorage.removeItem('refreshToken');
                        window.location.href = '/login';
                        return false;
                    }).catch(function() {
                        localStorage.removeItem('token');
                        localStorage.removeItem('refreshToken');
                        window.location.href = '/login';
                        return false;
                    });
                }
                return refreshPromise.then(function(ok) {
                    if (ok) {
                        options.headers['Authorization'] = 'Bearer ' + localStorage.getItem('token');
                        return originalFetch(url, options);
                    }
                    return response;
                });
            }
            return response;
        });
    };
})();

// ========== 全局工具函数（供 index.html 内联 JS 调用） ==========
function escapeHtml(str) {
    var div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// ========== 首页数据加载（Javalin 模式） ==========
async function loadSidebarAndShortcuts() {
    try {
        var folders = null;
        var token = localStorage.getItem('token');
        var headers = {};
        if (token) headers['Authorization'] = 'Bearer ' + token;

        // 加载文件夹树
        var folderResp = await fetch('/api/folders', { headers: headers });
        if (folderResp.ok) {
            folderTreeData = await folderResp.json();
            renderSidebarFolders(folderTreeData);
        }

        // 加载首页书签快捷链接（登录后显示用户书签，未登录显示默认链接）
        var links = document.getElementById('homeShortcutLinks');
        if (links) {
            if (token) {
                try {
                    var homeResp = await fetch('/api/home-bookmarks', { headers: headers });
                    if (homeResp.ok) {
                        var bookmarks = await homeResp.json();
                        if (bookmarks.length > 0) {
                            links.innerHTML = '';
                            bookmarks.forEach(function(bm) {
                                var a = document.createElement('a');
                                a.href = bm.url; a.target = '_blank'; a.rel = 'noopener';
                                var iconHtml = bm.logoUrl ? '<img src="' + bm.logoUrl + '" style="width:16px;height:16px;border-radius:2px;vertical-align:middle" onerror="this.style.display=\'none\'">' : '<i class="bi bi-link-45deg"></i>';
                                a.innerHTML = iconHtml + ' <span>' + escapeHtml(bm.title || bm.url) + '</span>';
                                links.appendChild(a);
                            });
                        }
                    }
                } catch(e) { /* ignore */ }
            }
            // 未登录或无首页书签时显示默认链接
            if (!links.hasChildNodes()) {
                links.innerHTML =
                    '<a href="https://github.com" target="_blank"><i class="bi bi-github"></i> GitHub</a>' +
                    '<a href="https://translate.google.com" target="_blank"><i class="bi bi-translate"></i> 翻译</a>' +
                    '<a href="https://mail.qq.com" target="_blank"><i class="bi bi-envelope"></i> 邮箱</a>' +
                    '<a href="https://www.jd.com" target="_blank"><i class="bi bi-cart"></i> 京东</a>' +
                    '<a href="https://www.taobao.com" target="_blank"><i class="bi bi-bag"></i> 淘宝</a>' +
                    '<a href="https://www.bilibili.com" target="_blank"><i class="bi bi-play-btn"></i> B站</a>' +
                    '<a href="https://www.zhihu.com" target="_blank"><i class="bi bi-chat-dots"></i> 知乎</a>';
            }
        }
    } catch (e) {
        console.warn('加载数据失败', e);
    }
}

function renderSidebarFolders(folders, parentEl) {
    var list = parentEl || document.getElementById('sidebarFolderList');
    if (!list) return;
    if (!parentEl) list.innerHTML = '';
    folders.forEach(function(f) {
        var div = document.createElement('div');
        div.className = parentEl ? 'sidebar-sub-folder-item' : 'sidebar-folder-item';
        div.dataset.folderId = f.id;
        div.onmouseenter = function() { showFolderFloat(this); };
        var icon = f.icon || 'bi-folder-fill';
        var hasChildren = f.children && f.children.length > 0;
        var arrowHtml = hasChildren ? '<i class="bi bi-chevron-right sub-folder-arrow"></i>' : '';
        div.innerHTML = arrowHtml + '<i class="bi ' + escapeHtml(icon) + '"></i><span class="sidebar-folder-name">' + escapeHtml(f.name) + '</span>';
        list.appendChild(div);
        if (hasChildren) {
            renderSidebarFolders(f.children, list);
        }
    });
}

function toggleUserDropdown(e) {
    if (e) e.stopPropagation();
    var token = localStorage.getItem('token');
    if (!token) { window.location.href = '/login.html'; return; }
    var existing = document.getElementById('_userMenu');
    if (existing) { existing.remove(); return; }
    var menu = document.createElement('div');
    menu.id = '_userMenu';
    menu.style.position = 'fixed';
    menu.style.top = '54px'; menu.style.right = '12px';
    menu.style.minWidth = '180px';
    menu.style.background = 'var(--card-bg)';
    menu.style.border = '1px solid var(--border-color)';
    menu.style.borderRadius = '12px';
    menu.style.padding = '6px';
    menu.style.boxShadow = '0 8px 28px rgba(0,0,0,0.14)';
    menu.style.zIndex = '9999';
    var isDark = document.documentElement.getAttribute('data-theme') === 'dark';
    var themeIcon = isDark ? 'bi-sun-fill' : 'bi-moon-stars-fill';
    var themeText = isDark ? '亮色主题' : '暗色主题';
    menu.innerHTML =
        '<a href="javascript:;" onclick="closeUserMenu();showBmMgmt()" class="um-item"><i class="bi bi-bookmarks"></i>书签管理</a>' +
        '<a href="javascript:;" onclick="closeUserMenu();showUserMgmt()" class="um-item"><i class="bi bi-person-gear"></i>用户管理</a>' +
        '<a href="javascript:;" onclick="closeUserMenu();showCollectTool()" class="um-item"><i class="bi bi-globe"></i>网页收集工具</a>' +
        '<a href="javascript:;" onclick="closeUserMenu();toggleTheme()" class="um-item"><i class="bi ' + themeIcon + '"></i><span>' + themeText + '</span></a>' +
        '<a href="javascript:;" onclick="closeUserMenu();logoutUser()" class="um-item"><i class="bi bi-box-arrow-right"></i>退出登录</a>';
    document.body.appendChild(menu);
    setTimeout(function() { document.addEventListener('click', closeUserMenu, { once: true }); }, 10);
}
function closeUserMenu() {
    var el = document.getElementById('_userMenu');
    if (el) el.remove();
}
function logoutUser() {
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('username');
    window.location.href = '/index.html';
}

// ========== 管理弹窗（书签管理/用户管理/收集工具） ==========
function closeMgmtModal() {
    var el = document.getElementById('_mgmtModal');
    if (el) el.remove();
}

function showBmMgmt() {
    var token = localStorage.getItem('token');
    if (!token) { window.location.href = '/login.html'; return; }
    var existing = document.getElementById('_mgmtModal');
    if (existing) { existing.remove(); return; }
    var modal = document.createElement('div');
    modal.id = '_mgmtModal';
    modal.style.cssText = 'display:block;position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.5);z-index:1050;';
    var content = document.createElement('div');
    content.className = 'mgmt-modal-content';
    content.innerHTML =
        '<div class="bm-modal-header"><h5><i class="bi bi-bookmarks me-2"></i>书签管理</h5>' +
        '<button type="button" class="bm-modal-close" onclick="closeMgmtModal()"><i class="bi bi-x-lg"></i></button></div>' +
        '<div class="mgmt-toolbar">' +
        '<input class="mgmt-search-input" id="_mgmtSearch" placeholder="搜索标题/URL..." oninput="mgmtBmSearch(this.value)">' +
        '<select id="_mgmtPublicFilter" onchange="mgmtBmLoad(1)"><option value="">全部类型</option><option value="1">公开</option><option value="0">私密</option></select>' +
        '<button class="bm-btn bm-btn-primary" onclick="openBookmarkModal()" style="margin-left:auto;padding:6px 14px;font-size:13px"><i class="bi bi-plus"></i> 新建</button></div>' +
        '<div class="mgmt-modal-body" id="_mgmtBody"><div class="mgmt-empty"><i class="bi bi-hourglass-split"></i><p>加载中...</p></div></div>' +
        '<div class="mgmt-pagination" id="_mgmtPagination"></div>';
    modal.appendChild(content);
    document.body.appendChild(modal);
    mgmtBmLoad(1);
}

var mgmtBmSearchTimer = null;
function mgmtBmSearch(val) {
    clearTimeout(mgmtBmSearchTimer);
    mgmtBmSearchTimer = setTimeout(function() { mgmtBmLoad(1); }, 300);
}

async function mgmtBmLoad(page) {
    var body = document.getElementById('_mgmtBody');
    var pagination = document.getElementById('_mgmtPagination');
    if (!body) return;
    var token = localStorage.getItem('token');
    if (!token) { body.innerHTML = '<div class="mgmt-empty"><i class="bi bi-exclamation-circle"></i><p>请先登录</p></div>'; return; }
    var keyword = document.getElementById('_mgmtSearch') ? document.getElementById('_mgmtSearch').value.trim() : '';
    var publicType = document.getElementById('_mgmtPublicFilter') ? document.getElementById('_mgmtPublicFilter').value : '';
    var size = 15;
    var url = '/api/admin/bookmarks?page=' + page + '&size=' + size;
    if (keyword) url += '&keyword=' + encodeURIComponent(keyword);
    if (publicType) url += '&publicType=' + publicType;
    try {
        var resp = await fetch(url, { headers: { 'Authorization': 'Bearer ' + token } });
        var data = await resp.json();
        var records = data.records || [];
        if (records.length === 0) {
            body.innerHTML = '<div class="mgmt-empty"><i class="bi bi-inbox"></i><p>暂无书签</p></div>';
            pagination.innerHTML = '';
            return;
        }
        var html = '<table class="mgmt-table"><thead><tr><th>标题</th><th>URL</th><th>类型</th><th>操作</th></tr></thead><tbody>';
        records.forEach(function(b) {
            var typeHtml = b.publicType === 1 ? '<span class="mgmt-badge mgmt-badge-public">公开</span>' : '<span class="mgmt-badge mgmt-badge-private">私密</span>';
            html += '<tr><td><strong>' + escapeHtml(b.title || '未命名') + '</strong></td>' +
                '<td class="mgmt-url-cell"><a href="' + escapeHtml(b.url || '') + '" target="_blank">' + escapeHtml((b.url || '').substring(0, 50)) + '</a></td>' +
                '<td>' + typeHtml + '</td>' +
                '<td><button class="mgmt-action-btn edit-btn" onclick="bmMgmtEdit(' + b.id + ')"><i class="bi bi-pencil"></i> 编辑</button>' +
                '<button class="mgmt-action-btn del-btn" onclick="bmMgmtDelete(' + b.id + ')"><i class="bi bi-trash"></i> 删除</button></td></tr>';
        });
        html += '</tbody></table>';
        body.innerHTML = html;
        // 分页
        var totalPages = data.pages || 1;
        var current = data.current || page;
        var phtml = '<button onclick="mgmtBmLoad(1)"' + (current <= 1 ? ' disabled style="opacity:0.4"' : '') + '>首页</button>' +
            '<button onclick="mgmtBmLoad(' + (current - 1) + ')"' + (current <= 1 ? ' disabled style="opacity:0.4"' : '') + '><i class="bi bi-chevron-left"></i></button>' +
            '<span class="page-info">' + current + ' / ' + totalPages + '</span>' +
            '<button onclick="mgmtBmLoad(' + (current + 1) + ')"' + (current >= totalPages ? ' disabled style="opacity:0.4"' : '') + '><i class="bi bi-chevron-right"></i></button>' +
            '<button onclick="mgmtBmLoad(' + totalPages + ')"' + (current >= totalPages ? ' disabled style="opacity:0.4"' : '') + '>末页</button>';
        pagination.innerHTML = phtml;
    } catch(e) {
        body.innerHTML = '<div class="mgmt-empty"><i class="bi bi-exclamation-circle"></i><p>加载失败</p></div>';
    }
}

function bmMgmtEdit(id) {
    var token = localStorage.getItem('token');
    fetch('/api/admin/bookmarks?page=1&size=200', {
        headers: { 'Authorization': 'Bearer ' + token }
    }).then(function(r) { return r.json(); }).then(function(data) {
        var records = data.records || [];
        var found = records.find(function(b) { return Number(b.id) === Number(id); });
        if (found) openBookmarkModal(found);
        else alert('未找到该书签数据');
    }).catch(function() { alert('加载书签数据失败'); });
}

async function bmMgmtDelete(id) {
    if (!confirm('确定删除此书签吗?')) return;
    var token = localStorage.getItem('token');
    try {
        var resp = await fetch('/api/admin/bookmarks/' + id, {
            method: 'DELETE',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        var data = await resp.json();
        if (data.success) { mgmtBmLoad(1); }
        else { alert('删除失败: ' + (data.message || '')); }
    } catch(e) { alert('删除失败'); }
}

// ========== 用户管理弹窗 ==========
function showUserMgmt() {
    var token = localStorage.getItem('token');
    if (!token) { window.location.href = '/login.html'; return; }
    var existing = document.getElementById('_mgmtModal');
    if (existing) { existing.remove(); return; }
    var modal = document.createElement('div');
    modal.id = '_mgmtModal';
    modal.style.cssText = 'display:block;position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.5);z-index:1050;';
    var content = document.createElement('div');
    content.className = 'mgmt-modal-content';
    content.innerHTML =
        '<div class="bm-modal-header"><h5><i class="bi bi-people me-2"></i>用户管理</h5>' +
        '<button type="button" class="bm-modal-close" onclick="closeMgmtModal()"><i class="bi bi-x-lg"></i></button></div>' +
        '<div class="mgmt-toolbar">' +
        '<input class="mgmt-search-input" id="_userSearch" placeholder="搜索用户名..." oninput="mgmtUserSearch(this.value)">' +
        '</div>' +
        '<div class="mgmt-modal-body" id="_mgmtUserBody"><div class="mgmt-empty"><i class="bi bi-hourglass-split"></i><p>加载中...</p></div></div>' +
        '<div class="mgmt-pagination" id="_mgmtUserPagination"></div>';
    modal.appendChild(content);
    document.body.appendChild(modal);
    mgmtUserLoad(1);
}

var mgmtUserSearchTimer = null;
function mgmtUserSearch(val) {
    clearTimeout(mgmtUserSearchTimer);
    mgmtUserSearchTimer = setTimeout(function() { mgmtUserLoad(1); }, 300);
}

async function mgmtUserLoad(page) {
    var body = document.getElementById('_mgmtUserBody');
    var pagination = document.getElementById('_mgmtUserPagination');
    if (!body) return;
    var token = localStorage.getItem('token');
    if (!token) { body.innerHTML = '<div class="mgmt-empty"><i class="bi bi-exclamation-circle"></i><p>请先登录</p></div>'; return; }
    var keyword = document.getElementById('_userSearch') ? document.getElementById('_userSearch').value.trim() : '';
    var url = '/admin/api/user/list?pageNum=' + page + '&pageSize=10';
    if (keyword) url += '&keyword=' + encodeURIComponent(keyword);
    try {
        var resp = await fetch(url, { headers: { 'Authorization': 'Bearer ' + token } });
        var data = await resp.json();
        if (!data.success) { body.innerHTML = '<div class="mgmt-empty"><i class="bi bi-exclamation-circle"></i><p>' + (data.message || '加载失败') + '</p></div>'; return; }
        var list = data.data.list || [];
        var total = data.data.total || 0;
        if (list.length === 0) {
            body.innerHTML = '<div class="mgmt-empty"><i class="bi bi-people"></i><p>暂无用户</p></div>';
            pagination.innerHTML = '';
            return;
        }
        var currentUser = localStorage.getItem('username') || '';
        var html = '<table class="mgmt-table"><thead><tr><th>ID</th><th>用户名</th><th>邮箱</th><th>角色</th><th>操作</th></tr></thead><tbody>';
        list.forEach(function(u) {
            var isAdmin = u.role === 'ROLE_ADMIN';
            var isSelf = u.userName === currentUser;
            var roleHtml = isAdmin ? '<span class="mgmt-badge mgmt-badge-admin">管理员</span>' : '<span class="mgmt-badge mgmt-badge-user">用户</span>';
            html += '<tr><td>' + u.id + '</td><td><strong>' + escapeHtml(u.userName || '') + '</strong>' + (isSelf ? ' <span style="font-size:11px;color:#999">(当前)</span>' : '') + '</td>' +
                '<td style="color:var(--text-secondary)">' + escapeHtml(u.email || '-') + '</td>' +
                '<td>' + roleHtml + '</td>' +
                '<td>' +
                '<button class="mgmt-action-btn edit-btn" onclick="mgmtUserEdit(' + u.id + ',\'' + encodeURIComponent(JSON.stringify(u)) + '\')"><i class="bi bi-pencil"></i></button>' +
                (isAdmin && list.length <= 1 ? '' : '<button class="mgmt-action-btn del-btn" onclick="mgmtUserDel(' + u.id + ',\'' + escapeHtml(u.userName || '') + '\')"><i class="bi bi-trash"></i></button>') +
                '</td></tr>';
        });
        html += '</tbody></table>';
        body.innerHTML = html;
        var totalPages = Math.ceil(total / 10) || 1;
        var phtml = '<button onclick="mgmtUserLoad(1)"' + (page <= 1 ? ' disabled style="opacity:0.4"' : '') + '>首页</button>' +
            '<button onclick="mgmtUserLoad(' + (page - 1) + ')"' + (page <= 1 ? ' disabled style="opacity:0.4"' : '') + '><i class="bi bi-chevron-left"></i></button>' +
            '<span class="page-info">' + page + ' / ' + totalPages + '</span>' +
            '<button onclick="mgmtUserLoad(' + (page + 1) + ')"' + (page >= totalPages ? ' disabled style="opacity:0.4"' : '') + '><i class="bi bi-chevron-right"></i></button>' +
            '<button onclick="mgmtUserLoad(' + totalPages + ')"' + (page >= totalPages ? ' disabled style="opacity:0.4"' : '') + '>末页</button>';
        pagination.innerHTML = phtml;
    } catch(e) {
        body.innerHTML = '<div class="mgmt-empty"><i class="bi bi-exclamation-circle"></i><p>加载失败</p></div>';
    }
}

function mgmtUserEdit(id, encodedJson) {
    try {
        var u = JSON.parse(decodeURIComponent(encodedJson));
        var newRole = u.role === 'ROLE_ADMIN' ? 'ROLE_USER' : 'ROLE_ADMIN';
        if (!confirm('确认将用户 "' + u.userName + '" 的角色切换为 ' + (newRole === 'ROLE_ADMIN' ? '管理员' : '普通用户') + ' 吗？')) return;
        var token = localStorage.getItem('token');
        fetch('/admin/api/user/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
            body: JSON.stringify({ id: u.id, userName: u.userName, email: u.email, role: newRole })
        }).then(function(r) { return r.json(); }).then(function(d) {
            if (d.success) { alert('更新成功'); mgmtUserLoad(1); }
            else { alert('更新失败: ' + (d.message || '')); }
        }).catch(function(e) { alert('更新失败'); });
    } catch(e) { alert('数据解析失败'); }
}

function mgmtUserDel(id, userName) {
    if (!confirm('确定删除用户 "' + userName + '" 吗？')) return;
    var token = localStorage.getItem('token');
    fetch('/admin/api/user/delete?id=' + id, {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + token }
    }).then(function(r) { return r.json(); }).then(function(d) {
        if (d.success) { alert('删除成功'); mgmtUserLoad(1); }
        else { alert('删除失败: ' + (d.message || '')); }
    }).catch(function(e) { alert('删除失败'); });
}

// ========== 网页收集工具弹窗 ==========
function showCollectTool() {
    var existing = document.getElementById('_mgmtModal');
    if (existing) { existing.remove(); return; }
    var modal = document.createElement('div');
    modal.id = '_mgmtModal';
    modal.style.cssText = 'display:block;position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.5);z-index:1050;';
    var content = document.createElement('div');
    content.className = 'mgmt-modal-content';
    content.style.maxWidth = '600px';
    content.innerHTML =
        '<div class="bm-modal-header"><h5><i class="bi bi-globe me-2"></i>网页收集工具</h5>' +
        '<button type="button" class="bm-modal-close" onclick="closeMgmtModal()"><i class="bi bi-x-lg"></i></button></div>' +
        '<div class="bm-modal-body">' +
        '<div style="border:2px dashed var(--border-color);border-radius:12px;padding:32px;text-align:center">' +
        '<i class="bi bi-bookmark-plus" style="font-size:40px;color:var(--primary);display:block;margin-bottom:12px"></i>' +
        '<h6 style="margin-bottom:16px">一键收藏到本地书签</h6>' +
        '<p style="font-size:13px;color:var(--text-secondary);margin-bottom:20px">将下方按钮拖到浏览器书签栏，浏览任意网页时点击即可快速收藏</p>' +
        '<a class="bm-btn bm-btn-primary" style="padding:12px 28px;font-size:16px" href="javascript:(function(){var d;var ds=\'\';var m=document.getElementsByTagName(\'meta\');for(var x=0,y=m.length;x<y;x++){if(m[x].name.toLowerCase()==\'description\'){d=m[x];}}if(d){ds=\'&description=\'+encodeURIComponent(d.content);}window.open(\'/addbookmark.html?url=\'+encodeURIComponent(document.URL)+ds+\'&title=\'+encodeURIComponent(document.title),\'_blank\');})();">' +
        '<i class="bi bi-bookmark-plus me-2"></i>收藏到本地书签</a>' +
        '<p class="mt-3" style="font-size:12px;color:#999">拖到浏览器书签栏使用，支持自动获取页面标题和描述</p></div>' +
        '<div class="mt-3" style="background:var(--main-bg);border-radius:10px;padding:16px">' +
        '<h6 style="font-size:14px;margin-bottom:8px"><i class="bi bi-info-circle me-1"></i> 使用说明</h6>' +
        '<ol style="font-size:13px;color:var(--text-secondary);padding-left:20px;line-height:1.8">' +
        '<li>将上方按钮 <strong>拖动</strong> 到浏览器的书签栏</li>' +
        '<li>在任意网页浏览时，点击该书签</li>' +
        '<li>会自动弹出添加页面，URL 和标题已自动填写</li>' +
        '<li>补充信息后保存即可</li></ol></div>' +
        '</div>';
    modal.appendChild(content);
    document.body.appendChild(modal);
}

// ========== 主题切换 ==========
function toggleTheme() {
    var html = document.documentElement;
    var isDark = html.getAttribute('data-theme') === 'dark';
    html.setAttribute('data-theme', isDark ? '' : 'dark');
    localStorage.setItem('theme', isDark ? 'light' : 'dark');
}

// ========== 初始化主题 ==========
(function() {
    var saved = localStorage.getItem('theme');
    if (saved === 'dark') {
        document.documentElement.setAttribute('data-theme', 'dark');
    }
})();

// ========== 新建书签模态框 ==========
function openBookmarkModal(editData) {
    var token = localStorage.getItem('token');
    if (!token) { window.location.href = '/login.html'; return; }
    var existing = document.getElementById('_bookmarkModal');
    if (existing) { existing.remove(); return; }
    var isEdit = !!editData;
    var modal = document.createElement('div');
    modal.id = '_bookmarkModal';
    modal.style.cssText = 'display:block;position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.5);z-index:1050;';
    var content = document.createElement('div');
    content.className = 'bm-modal-content';
    content.innerHTML =
        '<div class="bm-modal-header">' +
        '<h5><i class="bi ' + (isEdit ? 'bi-pencil-square' : 'bi-plus-circle') + ' me-2"></i>' + (isEdit ? '编辑书签' : '新建书签') + '</h5>' +
        '<button type="button" class="bm-modal-close" onclick="closeBookmarkModal()"><i class="bi bi-x-lg"></i></button></div>' +
        '<div class="bm-modal-body">' +
        '<div class="bm-field"><label>URL <span class="bm-required">*</span></label><input id="_bmUrl" class="bm-input" value="' + escapeHtml(editData ? (editData.url || '') : '') + '" placeholder="https://example.com"></div>' +
        '<div class="bm-field"><label>标题</label><input id="_bmTitle" class="bm-input" value="' + escapeHtml(editData ? (editData.title || '') : '') + '" placeholder="书签名称"></div>' +
        '<div class="bm-field"><label>描述</label><textarea id="_bmDesc" class="bm-input bm-textarea" rows="2" placeholder="简短描述">' + escapeHtml(editData ? (editData.description || '') : '') + '</textarea></div>' +
        '<div class="bm-field"><label>Logo URL</label><input id="_bmLogo" class="bm-input" value="' + escapeHtml(editData ? (editData.logoUrl || '') : '') + '" placeholder="可选，图标地址"></div>' +
        '<div class="bm-field"><label>所属文件夹</label><select id="_bmFolder" class="bm-input"><option value="">无</option></select></div>' +
        '<div class="bm-field"><label>标签</label><input id="_bmTags" class="bm-input" value="' + escapeHtml(editData ? (editData.tags || '') : '') + '" placeholder="多个标签用逗号分隔"></div>' +
        '<div class="bm-field"><label>可见性</label>' +
        '<div class="bm-toggle-group"><label class="bm-toggle-label"><input type="radio" name="bmPublic" value="1"' + ((!editData || editData.publicType === 1) ? ' checked' : '') + '><span>公开</span></label>' +
        '<label class="bm-toggle-label"><input type="radio" name="bmPublic" value="0"' + (editData && editData.publicType === 0 ? ' checked' : '') + '><span>私密</span></label></div></div>' +
        '</div>' +
        '<div class="bm-modal-footer"><button class="bm-btn bm-btn-secondary" onclick="closeBookmarkModal()">取消</button>' +
        '<button class="bm-btn bm-btn-primary" onclick="submitBookmarkModal(' + (isEdit ? editData.id : 'null') + ')"><i class="bi bi-check"></i> ' + (isEdit ? '更新' : '保存') + '</button></div>';
    modal.appendChild(content);
    document.body.appendChild(modal);
    // 加载文件夹列表
    loadBookmarkModalFolders(editData ? editData.folderId : null);
}
function loadBookmarkModalFolders(selectedId) {
    fetch('/api/folders').then(function(r) { return r.json(); }).then(function(folders) {
        var sel = document.getElementById('_bmFolder');
        if (!sel) return;
        sel.innerHTML = '<option value="">无</option>';
        function addOptions(list, prefix) {
            list.forEach(function(f) {
                var opt = document.createElement('option');
                opt.value = f.id;
                opt.textContent = (prefix || '') + f.name;
                if (selectedId && Number(selectedId) === Number(f.id)) opt.selected = true;
                sel.appendChild(opt);
                if (f.children && f.children.length > 0) {
                    addOptions(f.children, (prefix || '') + '── ');
                }
            });
        }
        addOptions(folders, '');
    }).catch(function() {});
}
function closeBookmarkModal() {
    var el = document.getElementById('_bookmarkModal');
    if (el) el.remove();
}
async function submitBookmarkModal(editId) {
    var token = localStorage.getItem('token');
    if (!token) { alert('请先登录'); return; }
    var url = document.getElementById('_bmUrl').value.trim();
    if (!url) { alert('请输入URL'); return; }
    var title = document.getElementById('_bmTitle').value.trim();
    var desc = document.getElementById('_bmDesc').value.trim();
    var logoUrl = document.getElementById('_bmLogo').value.trim();
    var folderId = document.getElementById('_bmFolder').value;
    var tags = document.getElementById('_bmTags').value.trim();
    var publicRadio = document.querySelector('input[name="bmPublic"]:checked');
    var publicType = publicRadio ? parseInt(publicRadio.value) : 1;
    var body = { url: url, title: title, description: desc, logoUrl: logoUrl, tags: tags, publicType: publicType };
    if (folderId) body.folderId = parseInt(folderId);
    try {
        var urlPath = '/api/admin/bookmarks';
        var method = 'POST';
        if (editId) { urlPath += '/' + editId; method = 'PUT'; }
        var resp = await fetch(urlPath, {
            method: method,
            headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
            body: JSON.stringify(body)
        });
        var data = await resp.json();
        if (data.success) { closeBookmarkModal(); alert(editId ? '更新成功' : '添加成功'); }
        else { alert('操作失败: ' + (data.message || '未知错误')); }
    } catch(e) { alert('操作失败: ' + e.message); }
}

// ========== 关闭模态框（点击背景） ==========
document.addEventListener('click', function(e) {
    var bm = document.getElementById('_bookmarkModal');
    if (bm && e.target === bm) closeBookmarkModal();
    var mm = document.getElementById('_mgmtModal');
    if (mm && e.target === mm) closeMgmtModal();
});

function highlightText(text, keyword) {
    if (!keyword || !text) return escapeHtml(text || '');
    var escaped = escapeHtml(text);
    var escapedKw = keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    var regex = new RegExp('(' + escapedKw + ')', 'gi');
    return escaped.replace(regex, '<mark class="search-highlight">$1</mark>');
}

// ========== 文件夹浮层面板 ==========
var floatTimer = null;
var floatFolderId = null;
async function showFolderFloat(el) {
    clearTimeout(floatTimer);
    var panel = document.getElementById('folderFloatPanel');
    var fid = el.dataset.folderId;
    if (!fid) { panel.innerHTML = '<div class="float-empty">暂无书签</div>'; panel.classList.add('show'); return; }
    // 仅当切换文件夹时才重新加载
    if (floatFolderId !== fid) {
        floatFolderId = fid;
        panel.innerHTML = '<div class="float-empty" style="padding:12px"><i class="bi bi-hourglass-split"></i> 加载中...</div>';
        panel.classList.add('show');
        try {
            var resp = await fetch('/api/bookmarks?folderId=' + fid);
            var data = await resp.json();
            var bms = data.bookmarks || [];
            if (bms.length === 0) {
                panel.innerHTML = '<div class="float-empty">暂无书签</div>';
            } else {
                var html = '<div class="float-links">';
                bms.forEach(function(b) {
                    var icon = b.logoUrl ? '<img src="' + b.logoUrl + '" class="bm-link-icon" onerror="this.style.display=\'none\'">' : '<span class="bm-link-fallback">' + (b.title ? b.title.charAt(0) : 'W') + '</span>';
                    html += '<a href="' + b.url + '" target="_blank" rel="noopener" class="bm-link">' + icon + '<span class="bm-link-title">' + escapeHtml(b.title || b.url) + '</span></a>';
                });
                html += '</div>';
                panel.innerHTML = html;
            }
        } catch(e) {
            panel.innerHTML = '<div class="float-empty">加载失败</div>';
        }
    }
    panel.classList.add('show');
    document.querySelectorAll('.sidebar-folder-item').forEach(function(item) { item.style.background = ''; });
    el.style.background = 'rgba(0,0,0,0.06)';
}
function hideFolderFloat() {
    floatTimer = setTimeout(function() {
        var panel = document.getElementById('folderFloatPanel');
        panel.classList.remove('show');
        document.querySelectorAll('.sidebar-folder-item').forEach(function(item) { item.style.background = ''; });
    }, 200);
}
function cancelHideFolderFloat() {
    clearTimeout(floatTimer);
}

// ========== 首页搜索引擎搜索 ==========
function doHomeSearch() {
    var engine = document.getElementById('searchEngineSelect').value;
    var keyword = document.getElementById('homeSearchInput').value.trim();
    if (!keyword) return;
    var urls = {
        baidu: 'https://www.baidu.com/s?wd=' + encodeURIComponent(keyword),
        quark: 'https://quark.sm.cn/s?q=' + encodeURIComponent(keyword),
        google: 'https://www.google.com/search?q=' + encodeURIComponent(keyword)
    };
    window.open(urls[engine] || urls.baidu, '_blank');
}

// ========== 自定义搜索引擎下拉框 ==========
function toggleEngineMenu() {
    var menu = document.getElementById('engineMenu');
    var icon = document.querySelector('#searchEngineDropdown .bi-chevron-down');
    var isOpen = menu.classList.toggle('open');
    if (icon) icon.classList.toggle('open', isOpen);
}
function selectEngine(el) {
    var value = el.getAttribute('data-value');
    var label = el.textContent;
    // 更新隐藏 select
    document.getElementById('searchEngineSelect').value = value;
    // 更新按钮显示的文本
    document.querySelector('#searchEngineDropdown .dropdown-label').textContent = label;
    // 更新激活状态
    document.querySelectorAll('#engineMenu li').forEach(function(li) { li.classList.remove('active'); });
    el.classList.add('active');
    // 关闭菜单
    document.getElementById('engineMenu').classList.remove('open');
    var icon = document.querySelector('#searchEngineDropdown .bi-chevron-down');
    if (icon) icon.classList.remove('open');
}
// 点击其他地方关闭下拉
document.addEventListener('click', function(e) {
    var dd = document.getElementById('searchEngineDropdown');
    if (dd && !dd.contains(e.target)) {
        document.getElementById('engineMenu').classList.remove('open');
        var icon = document.querySelector('#searchEngineDropdown .bi-chevron-down');
        if (icon) icon.classList.remove('open');
    }
});

document.addEventListener('DOMContentLoaded', function () {
    // ===== Javalin 模式：初始化数据 =====
    var folderTreeData = [];
    (function initApp() {
        var token = localStorage.getItem('token');
        var username = localStorage.getItem('username');
        // 渲染登录状态
        var actions = document.getElementById('headerActions');
        if (actions) {
            if (token && username) {
                actions.innerHTML = '<a class="header-btn" href="javascript:;" onclick="openBookmarkModal()"><i class="bi bi-plus-circle"></i> 新建书签</a>' +
                    '<div id="userDropdown" class="header-user-dropdown">' +
                    '<button type="button" class="user-btn" onclick="toggleUserDropdown(event)">' +
                    '<i class="bi bi-person-circle"></i><span class="user-btn-name">' + escapeHtml(username) + '</span><i class="bi bi-caret-down-fill"></i>' +
                    '</button></div>';
            } else {
                actions.innerHTML = '<a class="header-btn" href="/login.html">登录</a><a class="header-btn" href="/register.html">注册</a>';
            }
        }
        // 加载左侧文件夹树 + 快捷链接
        loadSidebarAndShortcuts();
    })();

    // 移动端侧边栏
    const sidebar = document.getElementById('sidebar');
    const sidebarToggle = document.getElementById('sidebarToggle');
    const sidebarOverlay = document.getElementById('sidebarOverlay');

    if (sidebarToggle) {
        sidebarToggle.addEventListener('click', function () {
            sidebar.classList.toggle('open');
            sidebarOverlay.classList.toggle('active');
            document.body.style.overflow = sidebar.classList.contains('open') ? 'hidden' : '';
        });
    }

    if (sidebarOverlay) {
        sidebarOverlay.addEventListener('click', function () {
            sidebar.classList.remove('open');
            sidebarOverlay.classList.remove('active');
            document.body.style.overflow = '';
        });
    }

    // 侧边栏迷你模式切换（桌面端 - Majestic风格）
    var sidebarToggleDesktop = document.getElementById('sidebarToggleDesktop');
    var mainContent = document.querySelector('.main-content');
    var SIDEBAR_MINI_KEY = 'sidebar_mini';

    var topHeader = document.querySelector('.top-header');

    function toggleSidebarMini() {
        var isMini = sidebar.classList.toggle('mini');
        if (mainContent) {
            mainContent.classList.toggle('sidebar-mini', isMini);
        }
        if (sidebarToggleDesktop) {
            sidebarToggleDesktop.querySelector('i').style.transform = isMini ? 'rotate(180deg)' : '';
        }
        // 管理中面板打开时同步调整位置
        var mgmtPanel = document.getElementById('bmMgmtPanel');
        if (mgmtPanel && mgmtPanel.style.display === 'block') {
            mgmtPanel.style.left = (isMini ? 60 : 220) + 'px';
        }
        var collectPanel = document.getElementById('collectToolPanel');
        if (collectPanel && collectPanel.style.display === 'block') {
            collectPanel.style.left = (isMini ? 60 : 220) + 'px';
        }
        try { localStorage.setItem(SIDEBAR_MINI_KEY, isMini ? '1' : ''); } catch (e) {}
    }

    // 恢复侧边栏状态
    try {
        if (localStorage.getItem(SIDEBAR_MINI_KEY) === '1' && window.innerWidth > 768) {
            sidebar.classList.add('mini');
            if (mainContent) mainContent.classList.add('sidebar-mini');
            if (sidebarToggleDesktop) sidebarToggleDesktop.querySelector('i').style.transform = 'rotate(180deg)';
        }
    } catch (e) {}

    if (sidebarToggleDesktop) {
        sidebarToggleDesktop.addEventListener('click', toggleSidebarMini);
    }

    // 迷你模式悬浮子列表 - 自动生成 flyout (附加到body避免被overflow裁切)
    var flyoutContainer = null;

    function buildFlyouts() {
        // 创建全局 flyout 容器
        if (!flyoutContainer) {
            flyoutContainer = document.createElement('div');
            flyoutContainer.className = 'mini-flyout-container';
            flyoutContainer.style.cssText = 'position:fixed;top:0;left:0;width:0;height:0;z-index:950;pointer-events:none;';
            document.body.appendChild(flyoutContainer);
        }
        document.querySelectorAll('.sidebar-channel').forEach(function (ch) {
            if (ch.dataset.flyoutBuilt) return;
            ch.dataset.flyoutBuilt = '1';

            var title = ch.querySelector('.channel-title span.channel-text');
            var list = ch.querySelector('.channel-list');
            if (!title || !list) return;

            var flyout = document.createElement('div');
            flyout.className = 'mini-flyout';
            flyout.dataset.channel = title.textContent;

            var flyoutTitle = document.createElement('div');
            flyoutTitle.className = 'flyout-title';
            flyoutTitle.textContent = title.textContent;
            flyout.appendChild(flyoutTitle);

            var flyoutList = document.createElement('div');
            flyoutList.className = 'flyout-list';
            list.querySelectorAll('li a').forEach(function (a) {
                var clonedA = a.cloneNode(true);
                // flyout 中的二级导航点击也滚动到对应区域
                clonedA.addEventListener('click', function(e) {
                    e.preventDefault();
                    var folderId = a.getAttribute('data-folder-id');
                    var sectionName = a.getAttribute('data-section-name');
                    switchFolderPanel(folderId);
                    var panel = document.querySelector('.folder-panel[data-folder-id="' + folderId + '"]');
                    if (panel && sectionName) {
                        var targetSection = panel.querySelector('.category-section[data-section-name="' + sectionName + '"]');
                        if (targetSection) {
                            var offset = 80;
                            var top = targetSection.getBoundingClientRect().top + window.pageYOffset - offset;
                            window.scrollTo({ top: top, behavior: 'smooth' });
                        }
                    }
                    // 高亮
                    document.querySelectorAll('.channel-list a').forEach(function(x) { x.classList.remove('active'); });
                    document.querySelectorAll('.flyout-list a').forEach(function(x) { x.classList.remove('active'); });
                    a.classList.add('active');
                    clonedA.classList.add('active');
                });
                flyoutList.appendChild(clonedA);
            });
            flyout.appendChild(flyoutList);

            flyoutContainer.appendChild(flyout);
        });
    }
    buildFlyouts();

    // 迷你模式 hover 显示 flyout
    var currentFlyout = null;
    document.querySelectorAll('.sidebar-channel').forEach(function (ch) {
        ch.addEventListener('mouseenter', function () {
            if (!sidebar.classList.contains('mini')) return;
            var name = ch.querySelector('.channel-title span.channel-text');
            if (!name) return;
            var flyout = flyoutContainer ? flyoutContainer.querySelector('.mini-flyout[data-channel="' + name.textContent + '"]') : null;
            if (!flyout) return;

            var rect = ch.getBoundingClientRect();
            flyout.style.display = 'block';
            flyout.style.position = 'fixed';
            flyout.style.left = rect.right + 'px';
            flyout.style.top = rect.top + 'px';
            flyout.style.pointerEvents = 'auto';
            flyout.style.animation = 'flyoutFadeIn 0.15s ease';
            currentFlyout = flyout;
        });
        ch.addEventListener('mouseleave', function () {
            if (currentFlyout) {
                currentFlyout.style.display = 'none';
                currentFlyout = null;
            }
        });
    });

    // 侧边栏频道折叠/展开 - 持久化状态
    var STORAGE_KEY = 'sidebar_channel_state';

    function getChannelStates() {
        try {
            var saved = localStorage.getItem(STORAGE_KEY);
            return saved ? JSON.parse(saved) : {};
        } catch (e) {
            return {};
        }
    }

    function saveChannelStates() {
        var states = {};
        document.querySelectorAll('.sidebar-channel').forEach(function (ch) {
            var title = ch.querySelector('.channel-title span');
            if (title) {
                states[title.textContent] = ch.classList.contains('open');
            }
        });
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(states));
        } catch (e) { /* ignore */ }
    }

    // 恢复折叠状态
    var savedStates = getChannelStates();
    var hasSavedState = Object.keys(savedStates).length > 0;
    document.querySelectorAll('.sidebar-channel').forEach(function (ch) {
        var title = ch.querySelector('.channel-title span');
        if (title) {
            if (hasSavedState) {
                if (savedStates[title.textContent] === false) {
                    ch.classList.remove('open');
                } else {
                    ch.classList.add('open');
                }
            }
        }
    });

    // 点击切换折叠 — 由 index.html 的事件委托统一处理（确保 innerHTML 替换后事件不丢失）
    // 此处不再重复绑定，避免与委托 handler 冲突

    // 二级导航点击：滚动到对应区域
    document.querySelectorAll('.sub-nav-link').forEach(function (link) {
        link.addEventListener('click', function (e) {
            e.preventDefault();
            var folderId = this.getAttribute('data-folder-id');
            var sectionName = this.getAttribute('data-section-name');

            // 先确保对应面板可见
            switchFolderPanel(folderId);

            // 滚动到对应子分类区域
            var panel = document.querySelector('.folder-panel[data-folder-id="' + folderId + '"]');
            if (panel && sectionName) {
                var targetSection = panel.querySelector('.category-section[data-section-name="' + sectionName + '"]');
                if (targetSection) {
                    var offset = 80;
                    var top = targetSection.getBoundingClientRect().top + window.pageYOffset - offset;
                    window.scrollTo({ top: top, behavior: 'smooth' });
                }
            }

            // 高亮当前二级导航
            document.querySelectorAll('.channel-list a').forEach(function (a) { a.classList.remove('active'); });
            document.querySelectorAll('.flyout-list a').forEach(function (a) { a.classList.remove('active'); });
            this.classList.add('active');

            // 移动端自动关闭侧边栏
            if (window.innerWidth <= 768) {
                sidebar.classList.remove('open');
                sidebarOverlay.classList.remove('active');
                document.body.style.overflow = '';
            }
        });
    });

    // 切换文件夹面板
    function switchFolderPanel(folderId) {
        // 如果管理中面板显示，先隐藏
        if (folderId != null) {
            var bmBrowsePanel = document.getElementById('bmBrowsePanel');
            var bmMgmtPanel = document.getElementById('bmMgmtPanel');
            var userMgmtPanel = document.getElementById('userMgmtPanel');
            var collectToolPanel = document.getElementById('collectToolPanel');
            if (bmBrowsePanel) bmBrowsePanel.style.display = '';
            if (bmMgmtPanel) bmMgmtPanel.style.display = 'none';
            if (userMgmtPanel) userMgmtPanel.style.display = 'none';
            if (collectToolPanel) collectToolPanel.style.display = 'none';
        }

        // 切换面板显示
        document.querySelectorAll('.folder-panel').forEach(function (panel) {
            panel.style.display = panel.getAttribute('data-folder-id') === folderId ? '' : 'none';
        });

        // 切换侧边栏一级导航高亮
        document.querySelectorAll('.sidebar-channel').forEach(function (ch) {
            if (ch.getAttribute('data-folder-id') === folderId) {
                ch.classList.add('active');
            } else {
                ch.classList.remove('active');
            }
        });

        // 清除二级导航高亮
        document.querySelectorAll('.channel-list a').forEach(function (a) { a.classList.remove('active'); });
        document.querySelectorAll('.flyout-list a').forEach(function (a) { a.classList.remove('active'); });

        // 滚动到顶部
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    // 图片懒加载
    if ('IntersectionObserver' in window) {
        var imgObserver = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) {
                    var img = entry.target;
                    if (img.dataset.src) {
                        img.src = img.dataset.src;
                        img.removeAttribute('data-src');
                    }
                    imgObserver.unobserve(img);
                }
            });
        });
        document.querySelectorAll('img[data-src]').forEach(function (img) { imgObserver.observe(img); });
    }

    // =================== 搜索浮层 ===================
    var searchToggle = document.getElementById('searchToggle');
    var headerSearchInline = document.getElementById('headerSearchInline');
    var searchOverlay = document.getElementById('searchOverlay');
    var searchOverlayInput = document.getElementById('searchOverlayInput');
    var searchOverlayClose = document.getElementById('searchOverlayClose');
    var searchOverlayBody = document.getElementById('searchOverlayBody');
    var searchTimer = null;

    function openSearch() {
        if (!searchOverlay) return;
        searchOverlay.classList.add('active');
        document.body.style.overflow = 'hidden';
        setTimeout(function () { searchOverlayInput.focus(); }, 100);
    }

    function closeSearch() {
        if (!searchOverlay) return;
        searchOverlay.classList.remove('active');
        document.body.style.overflow = '';
        searchOverlayInput.value = '';
        searchOverlayBody.innerHTML = '<div class="search-overlay-empty"><i class="bi bi-search"></i><p>输入关键词开始搜索</p></div>';
    }

    if (searchToggle) {
        searchToggle.addEventListener('click', function (e) {
            e.preventDefault();
            openSearch();
        });
    }

    if (headerSearchInline) {
        headerSearchInline.addEventListener('click', function (e) {
            e.preventDefault();
            openSearch();
        });
    }

    if (searchOverlayClose) {
        searchOverlayClose.addEventListener('click', closeSearch);
    }

    // 点击遮罩关闭
    if (searchOverlay) {
        searchOverlay.querySelector('.search-overlay-mask').addEventListener('click', closeSearch);
    }

    // ESC 关闭
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && searchOverlay && searchOverlay.classList.contains('active')) {
            closeSearch();
        }
    });

    // 搜索输入防抖
    if (searchOverlayInput) {
        searchOverlayInput.addEventListener('input', function () {
            var key = this.value.trim();
            clearTimeout(searchTimer);
            if (!key) {
                searchOverlayBody.innerHTML = '<div class="search-overlay-empty"><i class="bi bi-search"></i><p>输入关键词开始搜索</p></div>';
                return;
            }
            searchOverlayBody.innerHTML = '<div class="search-overlay-empty"><i class="bi bi-hourglass-split"></i><p>搜索中...</p></div>';
            searchTimer = setTimeout(function () {
                fetch('/api/search?key=' + encodeURIComponent(key))
                    .then(function (res) { return res.json(); })
                    .then(function (data) {
                        var results = data.results || [];
                        if (results.length === 0) {
                            searchOverlayBody.innerHTML = '<div class="search-no-result"><i class="bi bi-search"></i><p>未找到匹配的资源</p></div>';
                            return;
                        }
                        var html = '<div class="search-result-count">共找到 <strong>' + results.length + '</strong> 个结果</div>';
                        html += '<div class="search-overlay-results">';
                        results.forEach(function (item) {
                            var initial = item.title ? item.title.charAt(0) : 'W';
                            var logoHtml = '';
                            if (item.logoUrl) {
                                logoHtml = '<img src="' + item.logoUrl + '" alt="" onerror="this.style.display=\'none\';this.nextElementSibling.style.display=\'flex\';">' +
                                    '<div class="sri-fallback" style="display:none">' + initial + '</div>';
                            } else {
                                logoHtml = '<div class="sri-fallback">' + initial + '</div>';
                            }
                            html += '<a class="search-result-item" href="' + item.url + '" target="_blank" rel="noopener">' +
                                '<div class="sri-icon">' + logoHtml + '</div>' +
                                '<div class="sri-info"><div class="sri-name">' + highlightText(item.title, key) + '</div>' +
                                '<div class="sri-desc">' + highlightText(item.description || '', key) + '</div></div></a>';
                        });
                        html += '</div>';
                        searchOverlayBody.innerHTML = html;
                    })
                    .catch(function () {
                        searchOverlayBody.innerHTML = '<div class="search-no-result"><i class="bi bi-exclamation-circle"></i><p>搜索出错，请重试</p></div>';
                    });
            }, 300);
        });
    }

    // ========== 前台卡片拖动排序 ==========
    var frontendDragged = null;

    document.querySelectorAll('.resource-card').forEach(function(card) {
        card.setAttribute('draggable', 'true');
        card.addEventListener('dragstart', function(e) {
            frontendDragged = this;
            e.dataTransfer.effectAllowed = 'move';
            this.classList.add('opacity-50');
        });
        card.addEventListener('dragend', function(e) {
            this.classList.remove('opacity-50');
            frontendDragged = null;
        });
    });

    document.querySelectorAll('.resource-grid').forEach(function(grid) {
        grid.addEventListener('dragover', function(e) {
            var card = e.target.closest('.resource-card');
            if (!card || !frontendDragged || card === frontendDragged || !grid.contains(card)) return;
            e.preventDefault();
            var cards = [...grid.querySelectorAll('.resource-card')];
            var fromIdx = cards.indexOf(frontendDragged);
            var toIdx = cards.indexOf(card);
            if (fromIdx < 0 || toIdx < 0) return;
            if (fromIdx < toIdx) card.after(frontendDragged);
            else card.before(frontendDragged);
        });
        grid.addEventListener('drop', function(e) {
            e.preventDefault();
            var cards = [...grid.querySelectorAll('.resource-card')];
            var updates = [];
            cards.forEach(function(c, i) {
                var id = c.getAttribute('data-bookmark-id');
                if (id) updates.push({ id: Number(id), sortOrder: i + 1 });
            });
            if (updates.length > 0) {
                fetch('/api/admin/sort/bookmarks', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(updates)
                }).catch(function() {});
            }
        });
    });

    // 暴露到全局供 index.html 中的事件委托使用
    window.switchFolderPanel = switchFolderPanel;
});
