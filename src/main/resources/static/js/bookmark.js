// ========== 书签弹窗相关函数 ==========

function openBookmarkModal(editData) {
    var token = localStorage.getItem('token');
    if (!token) { window.location.href = '/login.html'; return; }
    var existing = document.getElementById('_bookmarkModal');
    if (existing) { existing.remove(); return; }
    loadPartial('/partials/bookmark-modal.html', function(html) {
        var container = document.getElementById('bookmarkModals');
        container.innerHTML = html;
        var isEdit = !!editData;
        // 填充动态标题
        var titleEl = document.getElementById('_bmModalTitle');
        if (titleEl) {
            titleEl.innerHTML = '<i class="bi ' + (isEdit ? 'bi-pencil-square' : 'bi-plus-circle') + ' me-2"></i>' + (isEdit ? '编辑书签' : '新建书签');
        }
        // 填充表单数据
        if (editData) {
            var urlEl = document.getElementById('_bmUrl');
            var titleEl2 = document.getElementById('_bmTitle');
            var descEl = document.getElementById('_bmDesc');
            var logoEl = document.getElementById('_bmLogo');
            var tagsEl = document.getElementById('_bmTags');
            if (urlEl) urlEl.value = editData.url || '';
            if (titleEl2) titleEl2.value = editData.title || '';
            if (descEl) descEl.value = editData.description || '';
            if (logoEl) logoEl.value = editData.logoUrl || '';
            if (tagsEl) tagsEl.value = editData.tags || '';
            // 设置 radio 选中状态
            var radios = document.querySelectorAll('input[name="bmPublic"]');
            radios.forEach(function(r) { r.checked = (parseInt(r.value) === editData.publicType); });
            // 设置提交按钮
            var submitBtn = document.getElementById('_bmSubmitBtn');
            if (submitBtn) submitBtn.onclick = function() { submitBookmarkModal(editData.id); };
        } else {
            var submitBtn2 = document.getElementById('_bmSubmitBtn');
            if (submitBtn2) submitBtn2.onclick = function() { submitBookmarkModal(null); };
        }
        // 构建文件夹树选择器
        buildBookmarkTreeSelector(editData ? editData.folderId : null);
    });
}

function toggleBookmarkTreePanel() {
    var dd = document.getElementById('_bmTreeSelectDropdown');
    if (!dd) return;
    var isOpen = dd.classList.toggle('open');
    if (isOpen) {
        var input = dd.parentElement.querySelector('.tree-select-input');
        if (input) {
            var rect = input.getBoundingClientRect();
            dd.style.position = 'fixed';
            dd.style.top = (rect.bottom + 2) + 'px';
            dd.style.left = rect.left + 'px';
            dd.style.width = rect.width + 'px';
            dd.style.zIndex = '1070';
        }
    } else {
        dd.style.position = '';
        dd.style.top = '';
        dd.style.left = '';
        dd.style.width = '';
        dd.style.zIndex = '';
    }
}

function buildBookmarkTreeSelector(selectedFolderId) {
    var dropdown = document.getElementById('_bmTreeSelectDropdown');
    var hidden = document.getElementById('_bmFolderId');
    var label = document.getElementById('_bmFolderLabel');
    if (!dropdown || !hidden || !label) return;
    function setLabel(v, t) { hidden.value = v; label.textContent = t; }
    if (!selectedFolderId) { setLabel('', '请选择文件夹'); }
    dropdown.innerHTML = '';
    fetch('/api/folders').then(function(r) { return r.json(); }).then(function(folders) {
        function renderTree(list, level, parentEl) {
            var container = parentEl || dropdown;
            list.forEach(function(f) {
                var hasChildren = f.children && f.children.length > 0;
                var div = document.createElement('div');
                div.className = 'tree-select-item' + (f.id == selectedFolderId ? ' select-selected' : '');
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
                            var expanded = cd.classList.contains('open');
                            ts.querySelector('i').className = expanded ? 'bi bi-chevron-down' : 'bi bi-chevron-right';
                        }
                    };
                } else { ts.className = 'ts-empty'; ts.innerHTML = ''; }
                div.appendChild(ts);
                var ns = document.createElement('span');
                ns.textContent = f.name;
                div.appendChild(ns);
                div.onclick = function() {
                    setLabel(f.id, f.name);
                    dropdown.querySelectorAll('.select-selected').forEach(function(el) { el.classList.remove('select-selected'); });
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
        renderTree(folders, 0);
        if (selectedFolderId) {
            (function findName(list) {
                for (var i = 0; i < list.length; i++) {
                    if (list[i].id == selectedFolderId) { setLabel(list[i].id, list[i].name); return; }
                    if (list[i].children) findName(list[i].children);
                }
            })(folders);
        }
    }).catch(function() {});
}

// 点击外部关闭树形选择器
document.addEventListener('click', function(e) {
    var dd = document.getElementById('_bmTreeSelectDropdown');
    if (dd && dd.classList.contains('open') && !e.target.closest('#_bmTreeSelectDropdown') && !e.target.closest('.tree-select')) {
        dd.classList.remove('open');
        dd.style.position = '';
        dd.style.top = '';
        dd.style.left = '';
        dd.style.width = '';
    }
});

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
    var folderId = document.getElementById('_bmFolderId').value;
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
        if (data.success) { closeBookmarkModal(); loadSidebarAndShortcuts(); if (editId) alert('更新成功'); }
        else { alert('操作失败: ' + (data.message || '未知错误')); }
    } catch(e) { alert('操作失败: ' + e.message); }
}
