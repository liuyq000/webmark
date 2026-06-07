// ========== 书签管理主面板相关函数 ==========
var mgrCurrentFolderId = null;
var mgrCurrentPage = 1;
var mgrCurrentSize = 10;
var mgrCurrentKeyword = '';
var mgrFolderTreeData = null;
var mgrDraggedRow = null;
var mgrDraggedFolder = null;

function showBmMgmt() {
    var token = localStorage.getItem('token');
    if (!token) { window.location.href = '/login.html'; return; }
    var existing = document.getElementById('_mgmtModal');
    if (existing) { existing.remove(); return; }
    loadPartial('/partials/bmmgmt-panel.html', function(html) {
        var container = document.getElementById('toolModals');
        container.innerHTML = html;
        mgrLoadFolderTree();
    });
}

async function mgrLoadFolderTree() {
    try {
        var resp = await fetch('/api/folders?_=' + Date.now(), { cache: 'no-cache' });
        var folders = await resp.json();
        mgrFolderTreeData = folders;
        var container = document.getElementById('mgrTreeContainer');
        if (!container) return;
        container.innerHTML = '';
        // "全部书签"
        var allItem = document.createElement('div');
        allItem.className = 'tree-item active';
        allItem.setAttribute('data-folder-id', '');
        allItem.innerHTML = '<span style="display:inline-block;width:16px"></span><i class="bi bi-layers folder-icon"></i> 全部书签';
        allItem.onclick = function() { mgrSelectFolder(''); };
        container.appendChild(allItem);
        // 递归添加文件夹
        function addFolders(list, level, parentEl) {
            var target = parentEl || container;
            list.forEach(function(f) {
                var isProtected = f.name === '首页' || f.name === '未读列表';
                var hasChildren = f.children && f.children.length > 0;
                var div = document.createElement('div');
                div.className = 'tree-item' + (isProtected ? ' tree-item-protected' : '');
                div.setAttribute('data-folder-id', f.id);
                div.setAttribute('draggable', 'true');
                div.style.paddingLeft = (12 + level * 20) + 'px';
                var toggleHtml = hasChildren
                    ? '<span class="toggle-icon" onclick="event.stopPropagation();mgrToggleTree(this)"><i class="bi bi-chevron-right"></i></span>'
                    : '<span class="toggle-icon">&nbsp;</span>';
                div.innerHTML = toggleHtml + '<i class="bi bi-folder-fill text-warning folder-icon"></i> ' + escapeHtml(f.name);
                div.onclick = function() { mgrSelectFolder(f.id); };
                // 操作按钮（首页和未读列表不可修改）
                var actions = document.createElement('span');
                actions.className = 'tree-item-actions';
                if (isProtected) {
                    actions.innerHTML = '<span class="text-muted" style="font-size:11px">系统文件夹</span>';
                } else {
                    actions.innerHTML =
                        '<i class="bi bi-pencil" onclick="event.stopPropagation();mgrEditFolder(' + f.id + ')" title="编辑"></i>' +
                        '<i class="bi bi-plus-circle" onclick="event.stopPropagation();mgrAddSubFolder(' + f.id + ',\'' + escapeHtml(f.name) + '\')" title="新增子文件夹"></i>' +
                        '<i class="bi bi-trash" onclick="event.stopPropagation();mgrDelFolder(' + f.id + ')" title="删除"></i>';
                }
                div.appendChild(actions);
                target.appendChild(div);
                if (hasChildren) {
                    var cd = document.createElement('div');
                    cd.className = 'tree-children';
                    target.appendChild(cd);
                    addFolders(f.children, level + 1, cd);
                }
            });
        }
        addFolders(folders, 0);
        setTimeout(enableMgrDrag, 50);
        mgrLoadBookmarks();
    } catch(e) {}
}

function mgrSelectFolder(fid) {
    document.querySelectorAll('#mgrTreeContainer .tree-item').forEach(function(el) { el.classList.remove('active'); });
    var selector = fid ? '[data-folder-id="' + fid + '"]' : '[data-folder-id=""]';
    var target = document.querySelector('#mgrTreeContainer ' + selector);
    if (target) target.classList.add('active');
    mgrCurrentFolderId = fid || null;
    mgrCurrentPage = 1;
    mgrLoadBookmarks();
}

function mgrToggleTree(el) {
    var parent = el.closest('.tree-item');
    if (!parent) return;
    var cd = parent.nextElementSibling;
    if (!cd || !cd.classList.contains('tree-children')) return;
    cd.classList.toggle('tree-expanded');
    var icon = el.querySelector('i');
    if (icon) icon.className = cd.classList.contains('tree-expanded') ? 'bi bi-chevron-down' : 'bi bi-chevron-right';
}

async function mgrLoadBookmarks() {
    var body = document.getElementById('mgrTableBody');
    if (!body) return;
    var token = localStorage.getItem('token');
    if (!token) return;
    mgrCurrentKeyword = (document.getElementById('mgrKeyword') || {}).value || '';
    var pt = (document.getElementById('mgrPublicType') || {}).value || '';
    var url = '/api/admin/bookmarks?page=' + mgrCurrentPage + '&size=' + mgrCurrentSize;
    if (mgrCurrentFolderId) url += '&folderId=' + mgrCurrentFolderId;
    if (mgrCurrentKeyword) url += '&keyword=' + encodeURIComponent(mgrCurrentKeyword);
    if (pt) url += '&publicType=' + pt;
    try {
        var resp = await fetch(url, { headers: { 'Authorization': 'Bearer ' + token } });
        var data = await resp.json();
        var records = data.records || [];
        var countEl = document.getElementById('mgrCountLabel');
        if (countEl) countEl.textContent = '共 ' + (data.total || 0) + ' 条';
        if (records.length === 0) {
            body.innerHTML = '<div style="text-align:center;padding:60px;color:#999"><i class="bi bi-inbox" style="font-size:36px;display:block;margin-bottom:10px"></i>暂无书签</div>';
        } else {
            function fmtTime(t) {
                if (!t) return '';
                if (Array.isArray(t)) return t.slice(0,3).join('-') + ' ' + t.slice(3,6).map(function(v){return String(v).padStart(2,'0');}).join(':');
                return String(t).replace('T',' ').substring(0,16);
            }
            function escAttr(s) { return (s||'').replace(/"/g,'&quot;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
            var html = '<table class="mgmt-table"><thead><tr><th style="width:30px"></th><th style="width:50px">ID</th><th>标题</th><th>URL</th><th style="width:70px">类型</th><th style="width:130px">创建时间</th><th style="width:90px">操作</th></tr></thead><tbody>';
            records.forEach(function(b) {
                var logoHtml = b.logoUrl ? '<img src="' + escAttr(b.logoUrl) + '" class="bookmark-logo" onerror="this.style.display=\'none\'">' : '';
                html += '<tr draggable="true" data-id="' + b.id + '" ondragstart="mgrRowDragStart(event)" ondragend="mgrRowDragEnd(event)">' +
                    '<td><i class="bi bi-grip-vertical" style="cursor:grab;color:#ccc"></i></td>' +
                    '<td class="small text-muted">' + b.id + '</td>' +
                    '<td>' + logoHtml + '<strong>' + escapeHtml(b.title || '') + '</strong></td>' +
                    '<td class="bm-url-cell"><a href="' + escAttr(b.url||'') + '" target="_blank">' + escapeHtml((b.url||'').substring(0,40)) + '</a></td>' +
                    '<td>' + (b.publicType === 1 ? '<span class="badge-public">公开</span>' : '<span class="badge-private">私密</span>') + '</td>' +
                    '<td class="small text-muted">' + fmtTime(b.createTime) + '</td>' +
                    '<td><button class="page-link-btn" onclick="mgrEditBookmark(' + b.id + ')" title="编辑"><i class="bi bi-pencil"></i></button> ' +
                    '<button class="page-link-btn" onclick="mgrDeleteBookmark(' + b.id + ')" title="删除" style="color:#dc3545"><i class="bi bi-trash"></i></button></td></tr>';
            });
            html += '</tbody></table>';
            body.innerHTML = html;
        }
        // 分页
        var total = data.pages || 1;
        var cur = data.current || mgrCurrentPage;
        var ph = document.getElementById('mgrPaginationHtml');
        if (ph) {
            var phtml = '';
            phtml += '<button class="page-link-btn" onclick="mgrGoToPage(1)"' + (cur<=1?' disabled':'') + '>首页</button> ';
            phtml += '<button class="page-link-btn" onclick="mgrGoToPage(' + (cur-1) + ')"' + (cur<=1?' disabled':'') + '><i class="bi bi-chevron-left"></i></button> ';
            var start = Math.max(1, cur-2), end = Math.min(total, cur+2);
            if (start > 1) phtml += '<span class="page-info-text">...</span> ';
            for (var i = start; i <= end; i++) {
                phtml += '<button class="page-link-btn' + (i===cur?' active':'') + '" onclick="mgrGoToPage(' + i + ')">' + i + '</button> ';
            }
            if (end < total) phtml += '<span class="page-info-text">...</span> ';
            phtml += '<button class="page-link-btn" onclick="mgrGoToPage(' + (cur+1) + ')"' + (cur>=total?' disabled':'') + '><i class="bi bi-chevron-right"></i></button> ';
            phtml += '<button class="page-link-btn" onclick="mgrGoToPage(' + total + ')"' + (cur>=total?' disabled':'') + '>末页</button>';
            ph.innerHTML = phtml;
        }
    } catch(e) {
        if (body) body.innerHTML = '<div style="text-align:center;padding:40px;color:#999">加载失败</div>';
    }
}

function mgrGoToPage(p) { mgrCurrentPage = p; mgrLoadBookmarks(); }
function mgrChangePageSize(size) { mgrCurrentSize = Number(size); mgrCurrentPage = 1; mgrLoadBookmarks(); }

// ========== 拖拽排序 ==========
function mgrRowDragStart(e) {
    mgrDraggedRow = e.target.closest('tr');
    e.dataTransfer.effectAllowed = 'move';
    setTimeout(function() { if (mgrDraggedRow) mgrDraggedRow.style.opacity = '0.5'; }, 0);
}

function mgrRowDragEnd(e) {
    if (mgrDraggedRow) {
        mgrDraggedRow.style.opacity = '';
        var tbody = mgrDraggedRow.parentNode;
        if (tbody) {
            var ids = [];
            for (var ci = 0; ci < tbody.children.length; ci++) {
                var row = tbody.children[ci];
                if (row.dataset && row.dataset.id) ids.push(row.dataset.id);
            }
            if (ids.length > 0) {
                var token = localStorage.getItem('token');
                var updates = ids.map(function(id, i) { return { id: Number(id), sortOrder: i + 1 }; });
                fetch('/api/admin/sort/bookmarks', {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
                    body: JSON.stringify(updates)
                }).then(function(r) { return r.json(); }).then(function(d) {
                    if (d.success) setTimeout(loadSidebarAndShortcuts, 2000);
                }).catch(function(e) { console.warn('排序失败', e); });
            }
        }
    }
    mgrDraggedRow = null;
}

function enableMgrDrag() {
    var mgmtModal = document.getElementById('_mgmtModal');
    if (!mgmtModal) return;
    mgmtModal.querySelectorAll('.mgmt-tree-panel .tree-item[data-folder-id]:not([data-folder-id=""])').forEach(function(el) {
        if (!el.getAttribute('draggable')) el.setAttribute('draggable', 'true');
    });
}

// ========== 书签 CRUD ==========
function mgrOpenAddBookmark() { openBookmarkModal(); }

async function mgrEditBookmark(id) {
    var token = localStorage.getItem('token');
    try {
        var resp = await fetch('/api/admin/bookmarks?page=1&size=200', {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        var data = await resp.json();
        var records = data.records || [];
        var found = records.find(function(b) { return Number(b.id) === Number(id); });
        if (found) openBookmarkModal(found);
        else alert('未找到该书签');
    } catch(e) { alert('加载失败'); }
}

async function mgrDeleteBookmark(id) {
    if (!confirm('确定删除此书签吗？')) return;
    var token = localStorage.getItem('token');
    try {
        var resp = await fetch('/api/admin/bookmarks/' + id, {
            method: 'DELETE', headers: { 'Authorization': 'Bearer ' + token }
        });
        var data = await resp.json();
        if (data.success) mgrLoadBookmarks();
        else alert('删除失败: ' + (data.message || ''));
    } catch(e) { alert('删除失败'); }
}

function mgrResetSearch() {
    var kw = document.getElementById('mgrKeyword');
    var pt = document.getElementById('mgrPublicType');
    if (kw) kw.value = '';
    if (pt) pt.value = '';
    mgrCurrentPage = 1;
    mgrLoadBookmarks();
}

// ========== 文件夹 CRUD ==========
function mgrAddTopFolder() { mgrOpenFolderModal(null, null); }
function mgrAddSubFolder(parentId, parentName) { mgrOpenFolderModal(null, parentId); }
function mgrEditFolder(id) { mgrOpenFolderModal(id, null); }
function mgrDelFolder(id) { mgrOpenDeleteFolderModal(id); }
