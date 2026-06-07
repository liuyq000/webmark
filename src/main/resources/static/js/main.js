
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
                            localStorage.setItem('role', d.role);
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

// ========== 通用 HTML 片段加载函数 ==========
var modalHtmlCache = {};
function loadPartial(url, callback) {
    function render(html) {
        if (callback) callback(html);
    }
    if (modalHtmlCache[url]) {
        render(modalHtmlCache[url]);
        return;
    }
    fetch(url).then(function(r) { return r.text(); }).then(function(html) {
        modalHtmlCache[url] = html;
        render(html);
    }).catch(function() { console.error('加载失败: ' + url); });
}

// ========== 首页数据加载（Javalin 模式） ==========
async function loadSidebarAndShortcuts() {
    try {
        var folders = null;
        var token = localStorage.getItem('token');
        var headers = {};
        if (token) headers['Authorization'] = 'Bearer ' + token;

        // 加载文件夹树
        var folderResp = await fetch('/api/folders?_=' + Date.now(), { headers: headers, cache: 'no-cache' });
        if (folderResp.ok) {
            folderTreeData = await folderResp.json();
            renderSidebarFolders(folderTreeData);
            // 查找首页文件夹 ID
            homeFolderId = (function findHomeFolderId(list) {
                for (var i = 0; i < list.length; i++) {
                    if (list[i].name === '首页') return list[i].id;
                    if (list[i].children) { var id = findHomeFolderId(list[i].children); if (id) return id; }
                }
                return null;
            })(folderTreeData);
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
                                a.setAttribute('draggable', 'true');
                                a.setAttribute('data-bm-id', bm.id);
                                a.setAttribute('data-folder-id', homeFolderId);
                                var iconHtml = bm.logoUrl ? '<img src="' + bm.logoUrl + '" style="width:16px;height:16px;border-radius:2px;vertical-align:middle" onerror="this.style.display=\'none\'">' : '<i class="bi bi-link-45deg"></i>';
                                a.innerHTML = iconHtml + ' <span>' + escapeHtml(bm.title || bm.url) + '</span>';
                                links.appendChild(a);
                            });
                        }
                    }
                } catch(e) { /* ignore */ }
            }
            // 首页文件夹为空时不显示默认链接
        }
    } catch (e) {
        console.warn('加载数据失败', e);
    }
}

function renderSidebarFolders(folders) {
    var list = document.getElementById('sidebarFolderList');
    if (!list) return;
    list.innerHTML = '';
    folders.forEach(function(f) {
        // 隐去"首页"文件夹，其书签展示在搜索框下的快捷链接中
        if (f.name === '首页') return;
        var div = document.createElement('div');
        div.className = 'sidebar-folder-item';
        div.dataset.folderId = f.id;
        div.onmouseenter = function() { showFolderFloat(this); };
        var icon = f.icon || 'bi-folder-fill';
        div.innerHTML = '<i class="bi ' + escapeHtml(icon) + '"></i><span class="sidebar-folder-name">' + escapeHtml(f.name) + '</span>';
        list.appendChild(div);
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
    var userRole = localStorage.getItem('role');
    menu.innerHTML =
        '<a href="javascript:;" onclick="closeUserMenu();showBmMgmt()" class="um-item"><i class="bi bi-bookmarks"></i>书签管理</a>' +
        (userRole === 'ROLE_ADMIN' ? '<a href="javascript:;" onclick="closeUserMenu();showUserMgmt()" class="um-item"><i class="bi bi-person-gear"></i>用户管理</a>' : '') +
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
    localStorage.removeItem('role');
    window.location.href = '/index.html';
}

// ========== 管理弹窗（书签管理/用户管理/收集工具） ==========
function closeMgmtModal() {
    var el = document.getElementById('_mgmtModal');
    if (el) el.remove();
}


// ========== 首页跨文件夹拖拽 ==========
var homeDragEl = null; // 正在拖拽的链接元素
var homeDragBmId = null; // 被拖拽书签 ID
var homeDragFolderId = null; // 来源文件夹 ID
var homeFolderId = null; // 首页文件夹 ID（从文件夹树中查找）

// 浮层面板链接和首页快捷链接的跨文件夹拖拽
document.addEventListener('dragstart', function(e) {
    var bmLink = e.target.closest('.bm-link[data-bm-id]') || e.target.closest('#homeShortcutLinks a[data-bm-id]');
    if (bmLink) {
        homeDragEl = bmLink;
        homeDragBmId = bmLink.getAttribute('data-bm-id');
        homeDragFolderId = bmLink.getAttribute('data-folder-id');
        e.dataTransfer.effectAllowed = 'move';
        e.dataTransfer.setData('text/plain', homeDragBmId);
        // 视觉反馈：被拖拽项缩小 + 阴影
        bmLink.style.transition = 'transform 0.15s, box-shadow 0.15s';
        bmLink.style.transform = 'scale(0.92)';
        bmLink.style.boxShadow = '0 4px 12px rgba(0,0,0,0.15)';
        // 标记拖拽状态，阻止面板关闭
        window._isDragging = true;
        clearTimeout(floatTimer);
        return;
    }
    // 管理弹窗内的拖拽
    var mm = document.getElementById('_mgmtModal');
    if (mm && mm.contains(e.target)) {
        var ti = e.target.closest('.mgmt-tree-panel .tree-item[data-folder-id]');
        if (ti && ti.getAttribute('data-folder-id') !== '') {
            mgrDraggedFolder = ti;
            e.dataTransfer.effectAllowed = 'move';
        }
    }
});
document.addEventListener('dragover', function(e) {
    // 管理弹窗内拖拽
    var mm = document.getElementById('_mgmtModal');
    if (mm && mm.contains(e.target)) {
        e.preventDefault();
        var tr = e.target.closest('tr[data-id]');
        if (tr && mgrDraggedRow && tr !== mgrDraggedRow) {
            var tbody = tr.parentNode;
            var rows = [...tbody.querySelectorAll('tr[data-id]')];
            var fromIdx = rows.indexOf(mgrDraggedRow);
            var toIdx = rows.indexOf(tr);
            if (fromIdx >= 0 && toIdx >= 0) {
                if (fromIdx < toIdx) tr.after(mgrDraggedRow); else tr.before(mgrDraggedRow);
            }
        }
        var ti = e.target.closest('.mgmt-tree-panel .tree-item[data-folder-id]');
        if (ti && mgrDraggedFolder && ti !== mgrDraggedFolder && mgrDraggedFolder.parentNode === ti.parentNode) {
            var parent = ti.parentNode;
            var items = [];
            for (var ci = 0; ci < parent.children.length; ci++) {
                var c = parent.children[ci];
                if (c.classList.contains('tree-item') && c.getAttribute('data-folder-id') && c.getAttribute('data-folder-id') !== '') {
                    items.push(c);
                }
            }
            var fromIdx = items.indexOf(mgrDraggedFolder);
            var toIdx = items.indexOf(ti);
            if (fromIdx >= 0 && toIdx >= 0) {
                if (fromIdx < toIdx) ti.after(mgrDraggedFolder); else ti.before(mgrDraggedFolder);
            }
        }
        return;
    }
    // 首页跨文件夹拖拽
    if (!homeDragEl) return;
    e.preventDefault();
    // 高亮当前 hover 的目标链接
    document.querySelectorAll('.bm-link.drag-over, #homeShortcutLinks a.drag-over').forEach(function(el) { el.classList.remove('drag-over'); });
    var targetLink = e.target.closest('.bm-link[data-bm-id]');
    if (targetLink) targetLink.classList.add('drag-over');
    // 目标：另一个浮层面板链接（同文件夹或不同文件夹）
    if (targetLink && targetLink.parentNode === homeDragEl.parentNode) {
        var children = Array.from(targetLink.parentNode.children);
        var from = children.indexOf(homeDragEl);
        var to = children.indexOf(targetLink);
        if (from >= 0 && to >= 0 && from !== to) {
            if (from < to) targetLink.after(homeDragEl); else targetLink.before(homeDragEl);
        }
    }
    // 目标：首页快捷链接区
    var homeLinks = document.getElementById('homeShortcutLinks');
    if (homeLinks && homeLinks.contains(e.target)) {
        homeLinks.style.background = 'rgba(74,144,217,0.06)';
        if (homeDragEl.parentNode !== homeLinks) {
            homeLinks.insertBefore(homeDragEl, e.target.closest('a') || null);
        }
    }
    // 目标：浮层面板（遍历所有可见面板，找到鼠标实际 hover 的面板）
    window._dragTargetFolderId = null;
    var targetFloatPanel = null;
    document.querySelectorAll('.folder-float-panel.show').forEach(function(p) {
        if (p.contains(e.target)) {
            targetFloatPanel = p;
            window._dragTargetFolderId = p.dataset.folderId || getFloatFolderId();
        }
    });
    if (targetFloatPanel) {
        targetFloatPanel.style.background = 'rgba(74,144,217,0.06)';
        var floatLinks = targetFloatPanel.querySelector('.float-links');
        if (floatLinks && homeDragEl.parentNode !== floatLinks) {
            floatLinks.appendChild(homeDragEl);
        }
    }
    // 清除非目标面板的高亮
    document.querySelectorAll('.folder-float-panel.show').forEach(function(p) {
        if (p !== targetFloatPanel) p.style.background = '';
    });
});
document.addEventListener('dragend', function() {
    // 管理弹窗文件夹拖拽
    if (mgrDraggedFolder) {
        mgrDraggedFolder.style.opacity = '';
        var parent = mgrDraggedFolder.parentNode;
        if (parent) {
            var ids = [];
            for (var ci = 0; ci < parent.children.length; ci++) {
                var child = parent.children[ci];
                var fid = child.getAttribute && child.getAttribute('data-folder-id');
                if (fid && fid !== '') ids.push(fid);
            }
            if (ids.length > 0) {
                var token = localStorage.getItem('token');
                var updates = ids.map(function(id, i) { return { id: Number(id), sortOrder: i + 1 }; });
                fetch('/api/admin/sort/folders', { method: 'PUT', headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token }, body: JSON.stringify(updates) })
                    .then(function(r) { return r.json(); })
                    .then(function(d) {
                        if (d.success) setTimeout(loadSidebarAndShortcuts, 2000);
                    })
                    .catch(function(e) { console.warn('排序失败', e); });
            }
        }
    }
    mgrDraggedFolder = null;
    mgrDraggedRow = null;
    // 清理拖拽高亮
    document.querySelectorAll('.bm-link.drag-over, #homeShortcutLinks a.drag-over').forEach(function(el) { el.classList.remove('drag-over'); });
    var hp = document.getElementById('homeShortcutLinks');
    if (hp) hp.style.background = '';
    document.querySelectorAll('.folder-float-panel').forEach(function(p) { p.style.background = ''; });
    // 首页跨文件夹拖拽
    if (homeDragEl) {
        homeDragEl.style.transform = '';
        homeDragEl.style.boxShadow = '';
        homeDragEl.style.transition = '';
        // 检查是否跨文件夹移动
        var newFolderId = null;
        var parentEl = homeDragEl.parentNode;
        if (parentEl && parentEl.id === 'homeShortcutLinks') {
            // 拖入首页快捷链接 → 移动到首页文件夹
            newFolderId = homeFolderId;
        } else if (parentEl && parentEl.closest) {
            var floatPanel = parentEl.closest('.folder-float-panel');
            if (floatPanel) {
                newFolderId = window._dragTargetFolderId || floatPanel.dataset.folderId || getFloatFolderId();
            }
        }
        // 保存排序 / 跨文件夹移动
        var ids = [];
        var folderIds = {};
        for (var ci = 0; ci < parentEl.children.length; ci++) {
            var child = parentEl.children[ci];
            if (child.getAttribute && child.getAttribute('data-bm-id')) {
                ids.push(child.getAttribute('data-bm-id'));
                folderIds[child.getAttribute('data-bm-id')] = child.getAttribute('data-folder-id');
            }
        }
        if (ids.length > 0) {
            var token = localStorage.getItem('token');
            var updates = ids.map(function(id, i) { return { id: Number(id), sortOrder: i + 1 }; });
            // 如果需要移动文件夹，先更新 folderId
            if (newFolderId && newFolderId !== homeDragFolderId) {
                fetch('/api/admin/bookmarks/' + homeDragBmId, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
                    body: JSON.stringify({ folderId: parseInt(newFolderId) })
                }).then(function() {
                    // 再保存顺序
                    fetch('/api/admin/sort/bookmarks', { method: 'PUT', headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token }, body: JSON.stringify(updates) })
                        .then(function() { setTimeout(loadSidebarAndShortcuts, 2000); })
                        .catch(function() {});
                }).catch(function() {});
            } else {
                fetch('/api/admin/sort/bookmarks', { method: 'PUT', headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token }, body: JSON.stringify(updates) })
                    .then(function() { setTimeout(loadSidebarAndShortcuts, 2000); })
                    .catch(function() {});
            }
        }
    }
    homeDragEl = null;
    homeDragBmId = null;
    homeDragFolderId = null;
    window._isDragging = false;
    window._dragTargetFolderId = null;
});


document.addEventListener('drop', function(e) {
    if (document.getElementById('_mgmtModal')) e.preventDefault();
});

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

// ========== 文件夹浮层面板（级联展开，类似浏览器书签栏） ==========
var floatTimer = null;
var floatPanels = []; // [{el, fid}] — panels[0] 为第一级（侧边栏悬停）

async function showFolderFloat(el) {
    clearTimeout(floatTimer);
    var fid = el.dataset.folderId;
    closeFloatPanelsFrom(1); // 关闭旧下级面板

    var panel = getFloatPanel(0);
    if (!fid) {
        panel.innerHTML = '<div class="float-empty">暂无书签</div>';
        panel.classList.add('show');
        positionFloatPanel(panel, el);
        return;
    }
    if (floatPanels[0] && floatPanels[0].fid === fid && panel.classList.contains('show')) return;

    floatPanels[0] = { el: panel, fid: fid };
    panel.dataset.folderId = fid;
    panel.innerHTML = '<div class="float-empty" style="padding:12px"><i class="bi bi-hourglass-split"></i> 加载中...</div>';
    panel.classList.add('show');
    positionFloatPanel(panel, el);
    await loadFloatPanel(panel, fid);

    document.querySelectorAll('.sidebar-folder-item').forEach(function(item) { item.style.background = ''; });
    el.style.background = 'rgba(0,0,0,0.06)';
}

function getFloatPanel(level) {
    var id = 'folderFloatPanel_' + level;
    var el = document.getElementById(id);
    if (el) return el;
    el = document.createElement('div');
    el.id = id;
    el.className = 'folder-float-panel';
    el.setAttribute('data-level', level);
    el.addEventListener('mouseenter', function() { clearTimeout(floatTimer); });
    el.addEventListener('mouseleave', function() { scheduleHideFloatPanels(level); });
    document.body.appendChild(el);
    return el;
}

function positionFloatPanel(panel, refEl) {
    if (refEl && refEl.getBoundingClientRect) {
        var rect = refEl.getBoundingClientRect();
        panel.style.left = (rect.right + 4) + 'px';
        panel.style.top = Math.max(2, rect.top - 5) + 'px';
    }
}

function positionChildPanel(childPanel, parentPanel, subEl) {
    var parentRect = parentPanel.getBoundingClientRect();
    childPanel.style.left = (parentRect.right + 4) + 'px';
    // 子面板顶部与触发它的子文件夹项对齐
    if (subEl) {
        var subRect = subEl.getBoundingClientRect();
        childPanel.style.top = subRect.top + 'px';
    } else {
        childPanel.style.top = parentRect.top + 'px';
    }
}

async function loadFloatPanel(panel, fid) {
    try {
        var folderResp = await fetch('/api/folders');
        var allFolders = await folderResp.json();
        var children = [];
        function findChildren(list, pid) {
            for (var i = 0; i < list.length; i++) {
                if (Number(list[i].id) === Number(pid)) {
                    children = list[i].children || [];
                    return true;
                }
                if (list[i].children && list[i].children.length > 0) {
                    if (findChildren(list[i].children, pid)) return true;
                }
            }
            return false;
        }
        findChildren(allFolders, fid);

        var resp = await fetch('/api/bookmarks?folderId=' + fid);
        var data = await resp.json();
        var bms = data.bookmarks || [];

        var html = '<div class="float-links">';
        var hasContent = false;

        // 子文件夹（点击可级联展开）
        if (children && children.length > 0) {
            children.forEach(function(child) {
                html += '<div class="float-sub-folder" data-folder-id="' + child.id + '">' +
                    '<i class="bi bi-folder"></i> ' + escapeHtml(child.name) + '</div>';
                hasContent = true;
            });
        }

        // 书签链接
        if (bms.length > 0) {
            hasContent = true;
            bms.forEach(function(b) {
                html += buildFloatLink(b);
            });
        }

        html += '</div>';
        panel.innerHTML = hasContent ? html : '<div class="float-empty">暂无书签</div>';
        // 为子文件夹绑定悬停展开事件
        attachFloatSubHover(panel);
    } catch (e) {
        panel.innerHTML = '<div class="float-empty">加载失败</div>';
    }
}

// 悬停子文件夹 → 在右侧级联展开子面板
var floatSubHoverTimer = null;

function attachFloatSubHover(panel) {
    panel.querySelectorAll('.float-sub-folder').forEach(function(el) {
        el.addEventListener('mouseenter', function() {
            clearTimeout(floatSubHoverTimer);
            var fid = this.getAttribute('data-folder-id');
            if (!fid) return;
            floatSubHoverTimer = setTimeout(function() {
                openChildFloatPanel(fid, panel, this);
            }.bind(this), 200);
        });
        el.addEventListener('mouseleave', function() {
            clearTimeout(floatSubHoverTimer);
        });
    });
}

function openChildFloatPanel(fid, parentPanel, subEl) {
    var parentLevel = parseInt(parentPanel.getAttribute('data-level') || '0');
    var childLevel = parentLevel + 1;

    closeFloatPanelsFrom(childLevel + 1);
    var childPanel = getFloatPanel(childLevel);

    if (floatPanels[childLevel] && floatPanels[childLevel].fid === fid && childPanel.classList.contains('show')) return;

    floatPanels[childLevel] = { el: childPanel, fid: fid };
    childPanel.dataset.folderId = fid;
    childPanel.innerHTML = '<div class="float-empty" style="padding:12px"><i class="bi bi-hourglass-split"></i> 加载中...</div>';
    childPanel.classList.add('show');
    positionChildPanel(childPanel, parentPanel, subEl);
    loadFloatPanel(childPanel, fid);
}

function closeFloatPanelsFrom(level) {
    for (var i = level; i < floatPanels.length; i++) {
        if (floatPanels[i] && floatPanels[i].el) {
            floatPanels[i].el.classList.remove('show');
        }
    }
    floatPanels.length = level;
}

function scheduleHideFloatPanels(fromLevel) {
    // 拖拽中不关闭面板，防止用户无法拖入面板
    if (window._isDragging) return;
    clearTimeout(floatTimer);
    floatTimer = setTimeout(function() {
        closeFloatPanelsFrom(fromLevel);
        if (fromLevel === 0) {
            document.querySelectorAll('.sidebar-folder-item').forEach(function(item) { item.style.background = ''; });
        }
    }, 200);
}

function hideFolderFloat() { scheduleHideFloatPanels(0); }
function cancelHideFolderFloat() { clearTimeout(floatTimer); }

// 获取当前最深层可见浮层的 folderId（用于拖拽排序）
function getFloatFolderId() {
    for (var i = floatPanels.length - 1; i >= 0; i--) {
        if (floatPanels[i] && floatPanels[i].el && floatPanels[i].el.classList.contains('show')) {
            return floatPanels[i].fid;
        }
    }
    return null;
}

// ========== 辅助：构建浮层书签链接 HTML ==========
function buildFloatLink(b) {
    var icon = b.logoUrl ? '<img src="' + b.logoUrl + '" class="bm-link-icon" onerror="this.style.display=\'none\'">' : '<span class="bm-link-fallback">' + (b.title ? b.title.charAt(0) : 'W') + '</span>';
    return '<a href="' + b.url + '" target="_blank" rel="noopener" class="bm-link" draggable="true" data-bm-id="' + b.id + '" data-folder-id="' + (b.folderId || '') + '">' + icon + '<span class="bm-link-title">' + escapeHtml(b.title || b.url) + '</span></a>';
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
