// ========== 文件夹弹窗相关函数 ==========
var currentDelFolderId = null;

function mgrOpenFolderModal(id, parentId) {
    var isNew = !id;
    loadPartial('/partials/folder-modal.html', function(html) {
        var container = document.getElementById('folderModals');
        container.innerHTML = html;
        buildFolderParentTreeSelector(parentId || null);
        if (!isNew) {
            var folder = mgrFolderTreeData ? findFolderInTree(mgrFolderTreeData, id) : null;
            var nameEl = document.getElementById('_folderName');
            var nameEnEl = document.getElementById('_folderNameEn');
            var iconEl = document.getElementById('_folderIcon');
            var folderIdEl = document.getElementById('_folderId');
            if (nameEl) nameEl.value = folder ? (folder.name || '') : '';
            if (nameEnEl) nameEnEl.value = folder ? (folder.nameEn || '') : '';
            if (iconEl) iconEl.value = folder ? (folder.icon || '') : '';
            if (folderIdEl) folderIdEl.value = id || '';
        }
    });
}

function findFolderInTree(list, id) {
    for (var i = 0; i < list.length; i++) {
        if (list[i].id == id) return list[i];
        if (list[i].children) { var f = findFolderInTree(list[i].children, id); if (f) return f; }
    }
    return null;
}

function toggleFolderParentPanel() {
    var dd = document.getElementById('_folderParentDropdown');
    if (!dd) return;
    dd.classList.toggle('open');
    if (dd.classList.contains('open')) {
        var input = dd.parentElement.querySelector('.tree-select-input');
        if (input) {
            var r = input.getBoundingClientRect();
            dd.style.position = 'fixed';
            dd.style.top = (r.bottom + 2) + 'px';
            dd.style.left = r.left + 'px';
            dd.style.width = r.width + 'px';
            dd.style.zIndex = '1070';
        }
    } else {
        dd.style.position = '';
        dd.style.top = '';
        dd.style.left = '';
        dd.style.width = '';
    }
}

function buildFolderParentTreeSelector(selectedParentId) {
    var dropdown = document.getElementById('_folderParentDropdown');
    var hidden = document.getElementById('_folderParentId');
    var label = document.getElementById('_folderParentLabel');
    if (!dropdown) return;
    function setLabel(v, t) { hidden.value = v; label.textContent = t; }
    setLabel('', '无（顶级文件夹）');
    dropdown.innerHTML = '';
    var topItem = document.createElement('div');
    topItem.className = 'tree-select-item' + (!selectedParentId ? ' select-selected' : '');
    topItem.style.paddingLeft = '12px';
    topItem.innerHTML = '<span class="ts-empty"></span><span>无（顶级文件夹）</span>';
    topItem.onclick = function() {
        setLabel('', '无（顶级文件夹）');
        dropdown.querySelectorAll('.select-selected').forEach(function(e) { e.classList.remove('select-selected'); });
        topItem.classList.add('select-selected');
        dropdown.classList.remove('open');
    };
    dropdown.appendChild(topItem);
    function renderTree(list, level, parentEl) {
        var container = parentEl || dropdown;
        list.forEach(function(f) {
            if (f.name === '首页' || f.name === '未读列表') { return; }
            var hasChildren = f.children && f.children.length > 0;
            var div = document.createElement('div');
            div.className = 'tree-select-item' + (f.id == selectedParentId ? ' select-selected' : '');
            div.style.paddingLeft = (12 + level * 20) + 'px';
            var ts = document.createElement('span');
            if (hasChildren) {
                ts.className = 'ts-toggle';
                ts.innerHTML = '<i class="bi bi-chevron-down"></i>';
                ts.onclick = function(e) {
                    e.stopPropagation();
                    var cd = div.nextElementSibling;
                    if (cd && cd.classList.contains('tree-select-children')) {
                        cd.classList.toggle('open');
                        var ex = cd.classList.contains('open');
                        ts.querySelector('i').className = ex ? 'bi bi-chevron-down' : 'bi bi-chevron-right';
                    }
                };
            } else { ts.className = 'ts-empty'; }
            div.appendChild(ts);
            div.appendChild(document.createTextNode(f.name));
            div.onclick = function() {
                setLabel(f.id, f.name);
                dropdown.querySelectorAll('.select-selected').forEach(function(e) { e.classList.remove('select-selected'); });
                div.classList.add('select-selected');
                dropdown.classList.remove('open');
            };
            container.appendChild(div);
            if (hasChildren) {
                var cd = document.createElement('div');
                cd.className = 'tree-select-children';
                container.appendChild(cd);
                renderTree(f.children, level + 1, cd);
            }
        });
    }
    if (mgrFolderTreeData) renderTree(mgrFolderTreeData, 0);
}

async function mgrSaveFolder() {
    var token = localStorage.getItem('token');
    var name = document.getElementById('_folderName').value.trim();
    if (!name) { alert('请输入文件夹名称'); return; }
    var id = document.getElementById('_folderId').value;
    var pid = document.getElementById('_folderParentId').value;
    var body = {
        name: name,
        nameEn: document.getElementById('_folderNameEn').value.trim(),
        icon: document.getElementById('_folderIcon').value.trim(),
        parentId: pid ? Number(pid) : null
    };
    try {
        var url = '/api/folders' + (id ? '/' + id : '');
        var method = id ? 'PUT' : 'POST';
        var resp = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
            body: JSON.stringify(body)
        });
        if (resp.ok) { var el = document.getElementById('_folderModal'); if (el) el.remove(); mgrLoadFolderTree(); }
        else { var d = await resp.json(); alert(d.message || '保存失败'); }
    } catch(e) { alert('保存失败'); }
}

function mgrOpenDeleteFolderModal(id) {
    currentDelFolderId = id;
    loadPartial('/partials/folder-delete-modal.html', function(html) {
        var container = document.getElementById('folderModals');
        container.innerHTML = html;
        var warnEl = document.getElementById('_delFolderWarn');
        var errEl = document.getElementById('_delFolderErr');
        var btn = document.getElementById('_delFolderBtn');
        if (warnEl) warnEl.classList.add('d-none');
        if (errEl) errEl.classList.add('d-none');
        if (btn) btn.onclick = function() { mgrConfirmDelFolder(id); };
    });
}

async function mgrConfirmDelFolder(id) {
    var token = localStorage.getItem('token');
    var btn = document.getElementById('_delFolderBtn');
    if (btn) { btn.disabled = true; btn.textContent = '删除中...'; }
    try {
        var resp = await fetch('/api/folders/' + id + '?force=true', {
            method: 'DELETE',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        var data = await resp.json();
        if (data.success) { var el = document.getElementById('_delFolderModal'); if (el) el.remove(); mgrLoadFolderTree(); }
        else { alert(data.message || '删除失败'); }
    } catch(e) { alert('删除失败'); }
    if (btn) { btn.disabled = false; btn.textContent = '删除'; }
}

// ========== 导入/导出/图标管理 ==========
function mgrOpenImport() {
    loadPartial('/partials/import-modal.html', function(html) {
        var div = document.createElement('div');
        div.innerHTML = html;
        while (div.firstChild) document.body.appendChild(div.firstChild);
    });
}

async function mgrDoImport() {
    var token = localStorage.getItem('token');
    if (!token) { alert('请先登录'); return; }
    var file = document.getElementById('_importFile');
    if (!file || !file.files.length) { alert('请选择文件'); return; }
    var fd = new FormData();
    fd.append('htmlFile', file.files[0]);
    if (document.getElementById('_importStructure') && document.getElementById('_importStructure').checked) fd.append('structure', 'YES');
    if (document.getElementById('_importPrivate') && document.getElementById('_importPrivate').checked) fd.append('type', 'PRIVATE');
    var st = document.getElementById('_importStatus');
    if (st) { st.textContent = '导入中...'; st.style.color = 'var(--text-secondary)'; }
    try {
        var resp = await fetch('/admin/tool/import', { method: 'POST', headers: { 'Authorization': 'Bearer ' + token }, body: fd });
        var d = await resp.json();
        if (st) { st.innerHTML = d.message; st.style.color = d.success ? '#28a745' : '#e74c3c'; }
        if (d.success) setTimeout(function() { var el = document.getElementById('_importModal'); if (el) el.remove(); mgrLoadFolderTree(); loadSidebarAndShortcuts(); }, 1500);
    } catch(e) { if (st) { st.textContent = '导入失败'; st.style.color = '#e74c3c'; } }
}

function mgrOpenExport() {
    loadPartial('/partials/export-modal.html', function(html) {
        var div = document.createElement('div');
        div.innerHTML = html;
        while (div.firstChild) document.body.appendChild(div.firstChild);
    });
}

async function mgrDoExport() {
    var token = localStorage.getItem('token');
    if (!token) { alert('请先登录'); return; }
    var fmt = document.querySelector('input[name="_exportFmt"]:checked');
    var format = fmt ? fmt.value : 'html';
    try {
        var resp = await fetch('/admin/tool/export/download?scope=all&format=' + format, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!resp.ok) { alert('导出失败'); return; }
        var blob = await resp.blob();
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = 'bookmarks.' + (format === 'csv' ? 'csv' : format === 'json' ? 'json' : 'html');
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    } catch(e) { alert('导出失败'); }
    var el = document.getElementById('_exportModal');
    if (el) el.remove();
}

function mgrOpenIcons() {
    loadPartial('/partials/icon-modal.html', function(html) {
        var div = document.createElement('div');
        div.innerHTML = html;
        while (div.firstChild) document.body.appendChild(div.firstChild);
        var commonIcons = ['bi-folder','bi-folder-fill','bi-folder2','bi-folder2-open','bi-bookmark','bi-bookmark-fill','bi-bookmark-star','bi-bookmark-star-fill','bi-code-slash','bi-gear','bi-star','bi-star-fill','bi-heart','bi-heart-fill','bi-tag','bi-tags','bi-globe','bi-globe2','bi-link-45deg','bi-link','bi-search','bi-file-text','bi-file-code','bi-file-earmark','bi-image','bi-camera','bi-music-note','bi-play-btn','bi-cart','bi-bag','bi-envelope','bi-chat','bi-person','bi-people','bi-calendar','bi-clock','bi-geo-alt','bi-telephone','bi-printer','bi-cloud','bi-download','bi-upload','bi-share','bi-lock','bi-unlock','bi-eye','bi-pencil','bi-trash','bi-plus-circle','bi-x-circle','bi-check-circle','bi-info-circle','bi-exclamation-circle','bi-question-circle'];
        var html = '<p style="font-size:13px;color:var(--text-secondary);margin-bottom:10px">点击图标复制类名</p><div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(70px,1fr));gap:6px">';
        commonIcons.forEach(function(ic) {
            html += '<div onclick="mgrCopyIcon(\'' + ic + '\')" style="display:flex;flex-direction:column;align-items:center;padding:10px 4px;border-radius:8px;cursor:pointer;border:1px solid var(--border-color);transition:all .12s" onmouseover="this.style.borderColor=\'var(--primary)\'" onmouseout="this.style.borderColor=\'var(--border-color)\'"><i class="' + ic + '" style="font-size:22px"></i><span style="font-size:10px;color:#999;margin-top:4px;text-align:center;word-break:break-all">' + ic.replace('bi-','') + '</span></div>';
        });
        html += '</div>';
        var content = document.getElementById('_iconContent');
        if (content) content.innerHTML = html;
    });
}

function mgrCopyIcon(name) {
    if (navigator.clipboard) {
        navigator.clipboard.writeText(name).then(function() { alert('已复制: ' + name); }).catch(function() {});
    }
}
