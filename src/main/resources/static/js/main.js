
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

function highlightText(text, keyword) {
    if (!keyword || !text) return escapeHtml(text || '');
    var escaped = escapeHtml(text);
    var escapedKw = keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    var regex = new RegExp('(' + escapedKw + ')', 'gi');
    return escaped.replace(regex, '<mark class="search-highlight">$1</mark>');
}

// ========== 文件夹浮层面板 ==========
var floatTimer = null;
function showFolderFloat(el) {
    clearTimeout(floatTimer);
    var panel = document.getElementById('folderFloatPanel');
    var content = el.querySelector('.folder-float-content');
    if (content) {
        panel.innerHTML = content.innerHTML;
    } else {
        panel.innerHTML = '<div class="float-empty">暂无书签</div>';
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

    // =================== 新建书签 Modal ===================
    window.openBookmarkModal = function () {
        var modal = document.getElementById('bookmarkModal');
        if (!modal) return;
        document.getElementById('bookmarkId').value = '';
        document.getElementById('bookmarkModalTitle').textContent = '新建书签';
        document.getElementById('bookmarkTitle').value = '';
        document.getElementById('bookmarkUrl').value = '';
        document.getElementById('bookmarkDescription').value = '';
        document.getElementById('bookmarkLogoUrl').value = '';
        document.getElementById('bookmarkTags').value = '';
        var publicType1 = document.querySelector('input[name="bookmarkPublicType"][value="1"]');
        if (publicType1) publicType1.checked = true;
        buildBookmarkTreeSelector(null);
        new bootstrap.Modal(modal).show();
    };

    window.buildBookmarkTreeSelector = function (selectedFolderId) {
        var dropdown = document.getElementById('bookmarkTreeSelectDropdown');
        var hidden = document.getElementById('bookmarkFolderId');
        var label = document.getElementById('bookmarkFolderLabel');
        if (!dropdown || !hidden) return;

        function setLabel(val, text) {
            hidden.value = val;
            label.textContent = text;
        }
        if (!selectedFolderId) {
            setLabel('', '请选择文件夹');
        } else {
            (function findName(folders) {
                for (var i = 0; i < folders.length; i++) {
                    var f = folders[i];
                    if (f.id == selectedFolderId) { setLabel(f.id, f.name); return; }
                    if (f.children) findName(f.children);
                }
            })(folderTreeData || []);
        }

        dropdown.innerHTML = '';
        if (typeof folderTreeData === 'undefined') return;

        function renderTree(folders, level, parentEl) {
            var container = parentEl || dropdown;
            folders.forEach(function (f) {
                var hasChildren = f.children && f.children.length > 0;
                var div = document.createElement('div');
                div.className = 'tree-select-item' + (f.id == selectedFolderId ? ' select-selected' : '');
                div.style.paddingLeft = (12 + level * 20) + 'px';

                var toggleSpan = document.createElement('span');
                if (hasChildren) {
                    toggleSpan.className = 'ts-toggle';
                    toggleSpan.innerHTML = '<i class="bi bi-chevron-down"></i>';
                    toggleSpan.onclick = function (e) {
                        e.stopPropagation();
                        var cd = div.nextElementSibling;
                        if (cd && cd.classList.contains('tree-select-children')) {
                            var h = cd.style.display === 'none';
                            cd.style.display = h ? '' : 'none';
                            toggleSpan.querySelector('i').className = h ? 'bi bi-chevron-down' : 'bi bi-chevron-right';
                        }
                    };
                } else {
                    toggleSpan.className = 'ts-empty';
                }
                div.appendChild(toggleSpan);

                var nameSpan = document.createElement('span');
                nameSpan.className = 'ts-name';
                nameSpan.textContent = f.name;
                div.appendChild(nameSpan);

                div.onclick = function () {
                    setLabel(f.id, f.name);
                    dropdown.querySelectorAll('.select-selected').forEach(function (el) { el.classList.remove('select-selected'); });
                    div.classList.add('select-selected');
                    closeBookmarkTreePanel();
                };
                container.appendChild(div);

                if (hasChildren) {
                    var cd = document.createElement('div');
                    cd.className = 'tree-select-children';
                    cd.style.display = 'none';
                    container.appendChild(cd);
                    renderTree(f.children, level + 1, cd);
                }
            });
        }
        renderTree(folderTreeData, 0);
    };

    window.toggleBookmarkTreePanel = function () {
        var dd = document.getElementById('bookmarkTreeSelectDropdown');
        if (dd) dd.classList.toggle('open');
    };

    window.closeBookmarkTreePanel = function () {
        var dd = document.getElementById('bookmarkTreeSelectDropdown');
        if (dd) dd.classList.remove('open');
    };

    document.addEventListener('click', function (e) {
        if (!e.target.closest('#bookmarkTreeSelectDropdown, .tree-select-input')) {
            var dd = document.getElementById('bookmarkTreeSelectDropdown');
            if (dd) dd.classList.remove('open');
        }
    });

    window.submitBookmarkFromModal = function () {
        var id = document.getElementById('bookmarkId').value;
        var title = document.getElementById('bookmarkTitle').value.trim();
        var url = document.getElementById('bookmarkUrl').value.trim();

        if (!title) { alert('请输入标题'); return; }
        if (!url) { alert('请输入URL'); return; }
        if (!url.startsWith('http://') && !url.startsWith('https://') && !url.startsWith('/')) {
            alert('请输入有效的URL（以 http:// 或 https:// 开头）');
            return;
        }

        var folderId = document.getElementById('bookmarkFolderId').value;
        var description = document.getElementById('bookmarkDescription').value.trim();
        var logoUrl = document.getElementById('bookmarkLogoUrl').value.trim();
        var tags = document.getElementById('bookmarkTags').value.trim();
        var publicTypeRadio = document.querySelector('input[name="bookmarkPublicType"]:checked');
        if (!publicTypeRadio) { alert('请选择类型（公开/私密）'); return; }
        var publicType = parseInt(publicTypeRadio.value);

        var body = {
            title: title, url: url,
            folderId: folderId ? Number(folderId) : null,
            description: description, logoUrl: logoUrl,
            tags: tags, publicType: publicType
        };

        fetch('/api/admin/bookmarks', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        }).then(function (resp) {
            if (resp.ok) {
                bootstrap.Modal.getInstance(document.getElementById('bookmarkModal')).hide();
                location.reload();
            } else {
                return resp.json().then(function (err) {
                    alert('保存失败：' + (err.message || '服务器错误'));
                });
            }
        }).catch(function () {
            alert('保存失败：网络错误');
        });
    };

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
