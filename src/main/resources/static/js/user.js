// ========== 用户管理相关函数 ==========
var userPageNum = 1, userPageSize = 10, userKeyword = '';

function showUserMgmt() {
    var token = localStorage.getItem('token');
    if (!token) { window.location.href = '/login.html'; return; }
    var existing = document.getElementById('_mgmtModal');
    if (existing) { existing.remove(); return; }
    loadPartial('/partials/user-mgmt-panel.html', function(html) {
        var container = document.getElementById('toolModals');
        container.innerHTML = html;
        loadUserList(1);
    });
}

function closeUserMgmt() {
    var el = document.getElementById('_mgmtModal');
    if (el) el.remove();
}

function userSearchInput() {
    userKeyword = document.getElementById('userSearchInput').value.trim();
    loadUserList(1);
}

function loadUserList(page) {
    userPageNum = page || 1;
    var token = localStorage.getItem('token');
    var url = '/admin/api/user/list?pageNum=' + userPageNum + '&pageSize=' + userPageSize;
    if (userKeyword) url += '&keyword=' + encodeURIComponent(userKeyword);
    fetch(url, { headers: { 'Authorization': 'Bearer ' + token } })
        .then(function(r) { return r.json(); })
        .then(function(d) {
            var list = (d.data && d.data.list) || [];
            var total = (d.data && d.data.total) || 0;
            renderUserTable(list, total);
        })
        .catch(function() {
            var tbody = document.getElementById('userTableBody');
            if (tbody) tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:30px;color:#999">加载失败</td></tr>';
        });
}

function renderUserTable(users, total) {
    var tbody = document.getElementById('userTableBody');
    if (!tbody) return;
    if (!users || !users.length) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:30px;color:#999">暂无用户</td></tr>';
        var totalText = document.getElementById('userTotalText');
        var pageInfo = document.getElementById('userPageInfo');
        if (totalText) totalText.textContent = '共 0 个用户';
        if (pageInfo) pageInfo.textContent = '第 0-0 条，共 0 条';
        var ph = document.getElementById('userPaginationHtml');
        if (ph) ph.innerHTML = '';
        return;
    }
    function esc(s) { return (s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }
    function fmtTime(t) {
        if (!t) return '';
        if (Array.isArray(t)) return t.slice(0,3).join('-') + ' ' + t.slice(3,6).map(function(v){return String(v).padStart(2,'0');}).join(':');
        return String(t).replace('T',' ').substring(0,16);
    }
    tbody.innerHTML = users.map(function(u) {
        var roleBadge = u.role === 'ROLE_ADMIN' ? '<span class="badge-admin">管理员</span>' : '<span class="badge-user">普通用户</span>';
        var createTime = fmtTime(u.createTime);
        var isAdminUser = u.userName === 'admin';
        var editBtn = isAdminUser ? '<span class="page-info-text">-</span>' : '<button class="page-link-btn" onclick="openUserEditModal(' + u.id + ')" title="编辑"><i class="bi bi-pencil"></i></button>';
        var delBtn = isAdminUser ? '' : '<button class="page-link-btn" onclick="deleteUserConfirm(' + u.id + ',\'' + esc(u.userName) + '\')" title="删除" style="color:#dc3545"><i class="bi bi-trash"></i></button>';
        return '<tr><td class="small text-muted">' + u.id + '</td><td><strong>' + esc(u.userName) + '</strong></td><td class="small">' + esc(u.email||'-') + '</td><td>' + roleBadge + '</td><td class="small text-muted">' + createTime + '</td><td>' + editBtn + ' ' + delBtn + '</td></tr>';
    }).join('');
    var totalText = document.getElementById('userTotalText');
    if (totalText) totalText.textContent = '共 ' + total + ' 个用户';
    var start = (userPageNum - 1) * userPageSize + 1;
    var end = Math.min(userPageNum * userPageSize, total);
    var pageInfo = document.getElementById('userPageInfo');
    if (pageInfo) pageInfo.textContent = '第 ' + start + '-' + end + ' 条，共 ' + total + ' 条';
    renderUserPagination(total);
}

function renderUserPagination(total) {
    var pages = Math.ceil(total / userPageSize) || 1;
    var ph = document.getElementById('userPaginationHtml');
    if (!ph) return;
    var html = '';
    html += '<button class="page-link-btn" onclick="loadUserList(1)"' + (userPageNum<=1?' disabled':'') + '>首页</button> ';
    html += '<button class="page-link-btn" onclick="loadUserList(' + (userPageNum-1) + ')"' + (userPageNum<=1?' disabled':'') + '><i class="bi bi-chevron-left"></i></button> ';
    var start = Math.max(1, userPageNum-2), end = Math.min(pages, userPageNum+2);
    if (start > 1) html += '<span class="page-info-text">...</span> ';
    for (var i = start; i <= end; i++) {
        html += '<button class="page-link-btn' + (i===userPageNum?' active':'') + '" onclick="loadUserList(' + i + ')">' + i + '</button> ';
    }
    if (end < pages) html += '<span class="page-info-text">...</span> ';
    html += '<button class="page-link-btn" onclick="loadUserList(' + (userPageNum+1) + ')"' + (userPageNum>=pages?' disabled':'') + '><i class="bi bi-chevron-right"></i></button> ';
    html += '<button class="page-link-btn" onclick="loadUserList(' + pages + ')"' + (userPageNum>=pages?' disabled':'') + '>末页</button>';
    ph.innerHTML = html;
}

// ========== 用户编辑弹窗 ==========
function openUserEditModal(id) {
    var isNew = !id;
    loadPartial('/partials/user-edit-modal.html', function(html) {
        var div = document.createElement('div');
        div.innerHTML = html;
        while (div.firstChild) document.body.appendChild(div.firstChild);
        if (!isNew) {
            var token = localStorage.getItem('token');
            fetch('/admin/api/user/detail?id=' + id, {
                headers: { 'Authorization': 'Bearer ' + token }
            }).then(function(r) { return r.json(); }).then(function(d) {
                if (d.success && d.data) {
                    var nameEl = document.getElementById('_ueName');
                    var emailEl = document.getElementById('_ueEmail');
                    var roleEl = document.getElementById('_ueRole');
                    var introEl = document.getElementById('_ueIntro');
                    if (nameEl) nameEl.value = d.data.userName || '';
                    if (emailEl) emailEl.value = d.data.email || '';
                    if (roleEl) roleEl.value = d.data.role || 'ROLE_USER';
                    if (introEl) introEl.value = d.data.introduction || '';
                }
            }).catch(function() {});
        }
    });
}

async function saveUserData(id) {
    var name = document.getElementById('_ueName').value.trim();
    var email = document.getElementById('_ueEmail').value.trim();
    var pass = document.getElementById('_uePass').value;
    var role = document.getElementById('_ueRole').value;
    var intro = document.getElementById('_ueIntro').value.trim();
    var err = document.getElementById('_ueError');
    if (!name) { if (err) err.textContent = '用户名不能为空'; return; }
    if (!id && !pass) { if (err) err.textContent = '新建用户时密码不能为空'; return; }
    var body = { userName: name, email: email, role: role, introduction: intro };
    if (pass) body.password = pass;
    if (id) body.id = id;
    var token = localStorage.getItem('token');
    try {
        var resp = await fetch('/admin/api/user/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
            body: JSON.stringify(body)
        });
        var d = await resp.json();
        if (d.success) {
            var el = document.getElementById('_userEditModal');
            if (el) el.remove();
            loadUserList(userPageNum);
        } else {
            if (err) err.textContent = d.message || '保存失败';
        }
    } catch(e) { if (err) err.textContent = '保存失败，请重试'; }
}

function deleteUserConfirm(id, name) {
    if (!confirm('确定要删除用户「' + name + '」吗？此操作不可恢复。')) return;
    var token = localStorage.getItem('token');
    fetch('/admin/api/user/delete?id=' + id, {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + token }
    }).then(function(r) { return r.json(); }).then(function(d) {
        if (d.success) loadUserList(userPageNum);
        else alert(d.message || '删除失败');
    }).catch(function() { alert('删除失败'); });
}
