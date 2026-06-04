/**
 * Drag Editor — 可视化 CSS 布局编辑器
 * 使用方法：在 URL 后加 ?edit=true 进入编辑模式
 */
(function () {
    'use strict';

    // ==================== 状态 ====================
    var changes = {};           // { ".selector": { "property": "value", ... } }
    var selectedEl = null;
    var isEditing = false;
    var hoverEl = null;
    var changeCount = 0;

    var hoverHighlight, hoverTooltip, selectedHighlight, selectedInfo, toolbar, panel;
    var handles = [];
    var ignoreNextClick = false;
    var toolbarDragState = null;

    // ==================== 初始化 ====================
    function init() {
        if (!(window.location.search.match(/[\?&]edit=true/) || window._DE_ACTIVE)) return;
        if (document.querySelector('.de-toolbar')) return;
        console.log('[Drag Editor] 进入编辑模式');
        isEditing = true;
        window._DE_ACTIVE = true;
        document.body.classList.add('de-edit-mode');
        createToolbar();
        createPanel();
        createHighlightElements();
        bindEvents();
    }

    // ==================== DOM 创建 ====================
    function createHighlightElements() {
        hoverHighlight = document.createElement('div');
        hoverHighlight.className = 'de-hover-highlight';
        hoverHighlight.style.display = 'none';
        document.body.appendChild(hoverHighlight);

        hoverTooltip = document.createElement('div');
        hoverTooltip.className = 'de-hover-tooltip';
        hoverTooltip.style.display = 'none';
        document.body.appendChild(hoverTooltip);

        selectedHighlight = document.createElement('div');
        selectedHighlight.className = 'de-selected-highlight';
        selectedHighlight.style.display = 'none';
        document.body.appendChild(selectedHighlight);

        selectedInfo = document.createElement('div');
        selectedInfo.className = 'de-selected-info';
        selectedInfo.style.display = 'none';
        document.body.appendChild(selectedInfo);
    }

    function createToolbar() {
        // 创建可拖拽工具栏
        toolbar = document.createElement('div');
        toolbar.className = 'de-toolbar';
        toolbar.innerHTML =
            '<div class="de-toolbar-drag-hint">☰ 拖拽移动</div>' +
            '<div class="de-toolbar-header">拖拽布局编辑器</div>' +
            '<div class="de-status" title="已修改属性数"><span>改动</span><span class="de-change-count">0</span></div>' +
            '<button class="de-btn-save" onclick="window._DE_save()">💾 保存</button>' +
            '<button class="de-btn-exit" onclick="window._DE_exit()">✕ 退出</button>' +
            '<div class="de-toolbar-hint">点击元素选中<br/>拖拽边框调整</div>';
        document.body.appendChild(toolbar);

        // 工具栏拖拽移动
        toolbar.addEventListener('pointerdown', startToolbarDrag);

        window._DE_save = saveChanges;
        window._DE_exit = exitEdit;
        window._DE_selectBySelector = function (sel) { selectElement(document.querySelector(sel)); };
        updateChangeCounter();
    }

    function startToolbarDrag(e) {
        // 不拦截按钮点击
        if (e.target.tagName === 'BUTTON') return;
        e.preventDefault();
        e.stopPropagation();
        toolbarDragState = {
            startX: e.clientX,
            startY: e.clientY,
            origLeft: parseInt(toolbar.style.left) || 0,
            origTop: parseInt(toolbar.style.top) || 80
        };
        document.addEventListener('pointermove', onToolbarDrag);
        document.addEventListener('pointerup', endToolbarDrag);
    }

    function onToolbarDrag(e) {
        if (!toolbarDragState) return;
        var dx = e.clientX - toolbarDragState.startX;
        var dy = e.clientY - toolbarDragState.startY;
        var newLeft = Math.max(0, toolbarDragState.origLeft + dx);
        var newTop = Math.max(0, toolbarDragState.origTop + dy);
        toolbar.style.left = newLeft + 'px';
        toolbar.style.top = newTop + 'px';
        toolbar.style.right = 'auto'; // 清除 right，使用 left
    }

    function endToolbarDrag() {
        toolbarDragState = null;
        document.removeEventListener('pointermove', onToolbarDrag);
        document.removeEventListener('pointerup', endToolbarDrag);
    }

    function createPanel() {
        panel = document.createElement('div');
        panel.className = 'de-panel';
        panel.id = 'dePanel';
        panel.innerHTML =
            '<div class="de-panel-header">' +
            '<h3>CSS 属性</h3>' +
            '<button class="de-panel-close" title="关闭面板" onclick="document.getElementById(\'dePanel\').classList.remove(\'open\')">×</button>' +
            '</div>' +
            '<div class="de-panel-body" id="dePanelBody">' +
            '<div style="color:#8a8fa0;text-align:center;padding:20px">点击页面元素开始编辑</div>' +
            '</div>';
        document.body.appendChild(panel);
    }

    function createHandles(el) {
        removeHandles();
        var rect = el.getBoundingClientRect();
        var positions = ['top', 'right', 'bottom', 'left'];
        positions.forEach(function (pos) {
            var h = document.createElement('div');
            h.className = 'de-handle de-handle-' + pos;
            h.setAttribute('data-handle', pos);
            h.addEventListener('pointerdown', function (e) { startDrag(e, el, pos); });
            document.body.appendChild(h);
            handles.push(h);
        });
        updateHandlePositions(el);
    }

    function removeHandles() {
        handles.forEach(function (h) { if (h.parentNode) h.parentNode.removeChild(h); });
        handles = [];
    }

    function updateHandlePositions(el) {
        var rect = el.getBoundingClientRect();
        var posMap = {
            top:    { left: rect.left + rect.width / 2, top: rect.top },
            right:  { left: rect.right, top: rect.top + rect.height / 2 },
            bottom: { left: rect.left + rect.width / 2, top: rect.bottom },
            left:   { left: rect.left, top: rect.top + rect.height / 2 }
        };
        handles.forEach(function (h) {
            var pos = h.getAttribute('data-handle');
            if (posMap[pos]) {
                h.style.left = posMap[pos].left + 'px';
                h.style.top = posMap[pos].top + 'px';
            }
        });
    }

    // ==================== 事件 ====================
    function bindEvents() {
        document.addEventListener('mousemove', onMouseMove, true);
        document.addEventListener('click', onClick, true);
        document.addEventListener('scroll', onScroll, true);
        document.addEventListener('keydown', onKeyDown, true);
        window.addEventListener('resize', onResize);
        // 拖拽监听放在 document 上，只注册一次
        document.addEventListener('pointermove', onDragMove);
        document.addEventListener('pointerup', endDrag);
    }

    function unbindEvents() {
        document.removeEventListener('mousemove', onMouseMove, true);
        document.removeEventListener('click', onClick, true);
        document.removeEventListener('scroll', onScroll, true);
        document.removeEventListener('keydown', onKeyDown, true);
        window.removeEventListener('resize', onResize);
    }

    function isEditorElement(el) {
        if (!el) return false;
        return el.closest('.de-toolbar,.de-panel,.de-handle,.de-hover-highlight,.de-hover-tooltip,.de-selected-highlight,.de-selected-info');
    }

    function onMouseMove(e) {
        var el = document.elementFromPoint(e.clientX, e.clientY);
        if (!el || isEditorElement(el) || !isEditing) {
            if (hoverEl) { hideHover(); }
            return;
        }
        if (el === hoverEl) return;

        if (hoverEl) { hideHover(); }

        // 跳过 body, html, 以及编辑器元素
        var tag = el.tagName.toLowerCase();
        if (tag === 'body' || tag === 'html') return;

        hoverEl = el;
        showHover(el);
    }

    function showHover(el) {
        if (el === selectedEl) return;
        var rect = el.getBoundingClientRect();
        hoverHighlight.style.display = 'block';
        hoverHighlight.style.left = rect.left + 'px';
        hoverHighlight.style.top = rect.top + 'px';
        hoverHighlight.style.width = rect.width + 'px';
        hoverHighlight.style.height = rect.height + 'px';

        hoverTooltip.style.display = 'block';
        hoverTooltip.style.left = (rect.left + rect.width / 2) + 'px';
        hoverTooltip.style.top = rect.top + 'px';

        var sel = getCSSSelector(el);
        hoverTooltip.innerHTML =
            '<span style="color:#0d6efd">' + escHtml(sel) + '</span> ' +
            '<span style="color:#8a8fa0">' + Math.round(rect.width) + '×' + Math.round(rect.height) + '</span>';
    }

    function hideHover() {
        hoverHighlight.style.display = 'none';
        hoverTooltip.style.display = 'none';
        hoverEl = null;
    }

    function onClick(e) {
        if (!isEditing) return;
        if (ignoreNextClick) { ignoreNextClick = false; return; }

        var el = document.elementFromPoint(e.clientX, e.clientY);
        if (!el || isEditorElement(el)) return;

        var tag = el.tagName.toLowerCase();
        if (tag === 'body' || tag === 'html') return;

        // 编辑模式下阻止交互元素默认行为（不跳转、不提交）
        // 向上查找到最近的交互元素，防止点击 a/button 内部的子元素时仍然跳转
        var interactiveEl = el.closest('a,button,input,select,textarea');
        if (interactiveEl) {
            e.preventDefault();
            e.stopPropagation();
            // 如果点击的是子元素，选中交互元素本身而非子元素
            if (interactiveEl !== el) el = interactiveEl;
        }

        selectElement(el);
    }

    function onScroll() {
        if (selectedEl) {
            updateHandlePositions(selectedEl);
            updateHighlight(selectedEl);
        }
        hideHover();
    }

    function onResize() {
        onScroll();
    }

    function onKeyDown(e) {
        if (!isEditing) return;
        if (e.key === 'Escape') {
            deselectElement();
        }
    }

    // ==================== 选择元素 ====================
    function selectElement(el) {
        deselectElement();
        selectedEl = el;
        selectedOrigStyles = getElementStyles(el); // 保存原始快照
        hideHover();

        updateHighlight(el);
        createHandles(el);
        updatePanel(el);
        panel.classList.add('open');
    }

    function deselectElement() {
        selectedEl = null;
        selectedOrigStyles = null;
        selectedHighlight.style.display = 'none';
        selectedInfo.style.display = 'none';
        removeHandles();
        panel.classList.remove('open');
    }

    function updateHighlight(el) {
        var rect = el.getBoundingClientRect();
        selectedHighlight.style.display = 'block';
        selectedHighlight.style.left = rect.left + 'px';
        selectedHighlight.style.top = rect.top + 'px';
        selectedHighlight.style.width = rect.width + 'px';
        selectedHighlight.style.height = rect.height + 'px';

        var sel = getCSSSelector(el);
        selectedInfo.style.display = 'block';
        selectedInfo.style.left = (rect.left + rect.width / 2) + 'px';
        selectedInfo.style.top = rect.top + 'px';
        selectedInfo.innerHTML = escHtml(sel) + ' ' + Math.round(rect.width) + '×' + Math.round(rect.height);
    }

    // ==================== 拖拽手柄 ====================
    var dragState = null;

    function startDrag(e, el, handlePos) {
        if (!selectedEl || selectedEl !== el) return;
        e.preventDefault();
        e.stopPropagation();
        dragState = {
            el: el,
            pos: handlePos,
            startX: e.clientX,
            startY: e.clientY,
            origRect: el.getBoundingClientRect(),
            origStyles: getElementStyles(el)
        };
        document.body.style.userSelect = 'none';
    }

    function onDragMove(e) {
        if (!dragState) return;
        e.preventDefault();
        var dx = e.clientX - dragState.startX;
        var dy = e.clientY - dragState.startY;

        var prop, delta;
        switch (dragState.pos) {
            case 'left':   prop = 'margin-left'; delta = dx; break;
            case 'right':  prop = 'margin-right'; delta = dx; break;
            case 'top':    prop = 'margin-top'; delta = dy; break;
            case 'bottom': prop = 'margin-bottom'; delta = dy; break;
            default: return;
        }

        // 实时更新 CSS
        var curVal = dragState.origStyles[prop] || '0px';
        var curNum = parseFloat(curVal) || 0;
        var unit = curVal.replace(/[\d.-]/g, '') || 'px';
        var newVal = curNum + delta + unit;

        dragState.el.style[prop.replace(/-([a-z])/g, function (m, c) { return c.toUpperCase(); })] = newVal;

        // 实时更新手柄和选中框位置
        updateHandlePositions(dragState.el);
        updateHighlight(dragState.el);

        // 更新面板
        updatePanelValues(dragState.el);
    }

    function updateDragPreview(el, pos, dx, dy) {
        // 移除旧的辅助线
        var existing = document.querySelector('.de-guide-h,.de-guide-v');
        if (existing) existing.parentNode.removeChild(existing);

        var rect = el.getBoundingClientRect();
        if (pos === 'top' || pos === 'bottom') {
            var guide = document.createElement('div');
            guide.className = 'de-guide-h';
            guide.style.top = (pos === 'top' ? rect.top + dy : rect.bottom + dy) + 'px';
            document.body.appendChild(guide);
        }
        if (pos === 'left' || pos === 'right') {
            var guide = document.createElement('div');
            guide.className = 'de-guide-v';
            guide.style.left = (pos === 'left' ? rect.left + dx : rect.right + dx) + 'px';
            document.body.appendChild(guide);
        }
    }

    function endDrag(e) {
        if (!dragState) return;
        document.body.style.userSelect = '';

        // 清除辅助线
        var guides = document.querySelectorAll('.de-guide-h,.de-guide-v');
        guides.forEach(function (g) { g.parentNode.removeChild(g); });

        // 记录变更
        var el = dragState.el;
        var prop, delta;
        switch (dragState.pos) {
            case 'left':   prop = 'margin-left'; delta = e.clientX - dragState.startX; break;
            case 'right':  prop = 'margin-right'; delta = e.clientX - dragState.startX; break;
            case 'top':    prop = 'margin-top'; delta = e.clientY - dragState.startY; break;
            case 'bottom': prop = 'margin-bottom'; delta = e.clientY - dragState.startY; break;
            default: dragState = null; return;
        }

        var curVal = dragState.origStyles[prop] || '0px';
        var curNum = parseFloat(curVal) || 0;
        var unit = curVal.replace(/[\d.-]/g, '') || 'px';
        var newVal = (curNum + delta) + unit;

        // 只有当值确实变了才记录
        if (curVal !== newVal) {
            recordChange(el, prop, newVal);
        }

        dragState = null;
        updateHandlePositions(el);
        updateHighlight(el);
    }

    // ==================== CSS 变更记录 ====================
    var selectedOrigStyles = null; // 选中元素时的原始 computed 样式快照

    function recordChange(el, prop, value) {
        var selector = getCSSSelector(el);

        // 对比选中元素时的原始值（而非当前已修改内联样式后的值）
        if (selectedOrigStyles && selectedOrigStyles[prop] === value) {
            if (changes[selector] && changes[selector][prop]) {
                delete changes[selector][prop];
                if (Object.keys(changes[selector]).length === 0) delete changes[selector];
                changeCount--;
                updateChangeCounter();
            }
            return;
        }

        if (!changes[selector]) changes[selector] = {};
        if (!changes[selector][prop]) changeCount++;
        changes[selector][prop] = value;
        updateChangeCounter();
    }

    function getElementStyles(el) {
        var styles = {};
        var props = ['margin-top', 'margin-right', 'margin-bottom', 'margin-left',
                      'padding-top', 'padding-right', 'padding-bottom', 'padding-left'];
        var cs = window.getComputedStyle(el);
        props.forEach(function (p) { styles[p] = cs.getPropertyValue(p); });
        return styles;
    }

    function updateChangeCounter() {
        var countEl = toolbar ? toolbar.querySelector('.de-change-count') : null;
        if (countEl) countEl.textContent = changeCount;
    }

    // ==================== 属性面板 ====================
    function updatePanel(el) {
        var body = document.getElementById('dePanelBody');
        if (!body) return;

        var selector = getCSSSelector(el);
        var styles = getElementStyles(el);
        var cs = window.getComputedStyle(el);
        var rect = el.getBoundingClientRect();

        var marginProps = [
            { name: 'margin-top',    label: 'T', css: 'margin-top' },
            { name: 'margin-right',  label: 'R', css: 'margin-right' },
            { name: 'margin-bottom', label: 'B', css: 'margin-bottom' },
            { name: 'margin-left',   label: 'L', css: 'margin-left' }
        ];
        var paddingProps = [
            { name: 'padding-top',    label: 'T', css: 'padding-top' },
            { name: 'padding-right',  label: 'R', css: 'padding-right' },
            { name: 'padding-bottom', label: 'B', css: 'padding-bottom' },
            { name: 'padding-left',   label: 'L', css: 'padding-left' }
        ];

        var html = '';
        html += '<div class="de-panel-section">';
        html += '<div class="de-panel-label"><span>选择器</span></div>';
        html += '<div class="de-selector-display">' + escHtml(selector) + '</div>';
        html += '<div style="font-size:11px;color:#8a8fa0;margin-bottom:12px">' +
                Math.round(rect.width) + '×' + Math.round(rect.height) + 'px</div>';
        html += '</div>';

        // Margin
        html += '<div class="de-panel-section">';
        html += '<div class="de-panel-label"><span>Margin</span></div>';
        html += buildPropertyGrid(marginProps, styles, selector);
        html += '</div>';

        // Padding
        html += '<div class="de-panel-section">';
        html += '<div class="de-panel-label"><span>Padding</span></div>';
        html += buildPropertyGrid(paddingProps, styles, selector);
        html += '</div>';

        // 盒模型
        html += '<div class="de-panel-section">';
        html += '<div class="de-panel-label"><span>盒模型</span></div>';
        html += '<div class="de-box-model">';
        html += '<div class="de-box-row de-box-margin">margin: ' +
                (styles['margin-top']||'0') + ' / ' + (styles['margin-right']||'0') + ' / ' +
                (styles['margin-bottom']||'0') + ' / ' + (styles['margin-left']||'0') + '</div>';
        html += '<div class="de-box-row de-box-border">border: ' +
                cs.borderTopWidth + ' / ' + cs.borderRightWidth + '</div>';
        html += '<div class="de-box-row de-box-padding">padding: ' +
                (styles['padding-top']||'0') + ' / ' + (styles['padding-right']||'0') + ' / ' +
                (styles['padding-bottom']||'0') + ' / ' + (styles['padding-left']||'0') + '</div>';
        html += '<div class="de-box-row de-box-content">' +
                Math.round(el.clientWidth) + '×' + Math.round(el.clientHeight) + 'px</div>';
        html += '</div>';
        html += '</div>';

        body.innerHTML = html;
        bindPanelInputs();
    }

    function buildPropertyGrid(props, styles, selector) {
        var h = '<div style="display:grid;grid-template-columns:repeat(4,1fr);gap:4px">';
        props.forEach(function (p) {
            var val = styles[p.name] || '';
            var changed = changes[selector] && changes[selector][p.css];
            var cls = changed ? ' de-changed' : '';
            h += '<div style="text-align:center;font-size:10px;color:#8a8fa0">' + p.label + '</div>';
        });
        h += '</div><div style="display:grid;grid-template-columns:repeat(4,1fr);gap:4px">';
        props.forEach(function (p) {
            var val = styles[p.name] || '';
            var changed = changes[selector] && changes[selector][p.css];
            var cls = changed ? ' de-changed' : '';
            h += '<input class="de-property-input' + cls + '" ' +
                 'data-prop="' + p.css + '" ' +
                 'data-selector="' + escHtmlAttr(selector) + '" ' +
                 'value="' + escHtmlAttr(val.replace('px', '')) + '" type="text" style="width:100%">';
        });
        h += '</div>';
        return h;
    }

    function updatePanelValues(el) {
        var body = document.getElementById('dePanelBody');
        if (!body || !selectedEl) return;

        var styles = getElementStyles(el);
        var inputs = body.querySelectorAll('.de-property-input');
        inputs.forEach(function (input) {
            var prop = input.getAttribute('data-prop');
            var cssProp = prop.replace(/-([a-z])/g, function (m, c) { return c.toUpperCase(); });
            var val = el.style[cssProp] || styles[prop] || '';
            var numVal = parseFloat(val);
            if (!isNaN(numVal)) {
                input.value = numVal;
            } else {
                input.value = val.replace('px', '');
            }
            var selector = input.getAttribute('data-selector');
            if (changes[selector] && changes[selector][prop]) {
                input.classList.add('de-changed');
            } else {
                input.classList.remove('de-changed');
            }
        });

        // 更新盒模型
        var cs = window.getComputedStyle(el);
        var boxModel = body.querySelector('.de-box-model');
        if (boxModel) {
            var marginDiv = boxModel.querySelector('.de-box-margin');
            var paddingDiv = boxModel.querySelector('.de-box-padding');
            var contentDiv = boxModel.querySelector('.de-box-content');
            if (marginDiv) {
                marginDiv.textContent = 'margin: ' + (styles['margin-top']||'0') + ' / ' +
                    (styles['margin-right']||'0') + ' / ' + (styles['margin-bottom']||'0') +
                    ' / ' + (styles['margin-left']||'0');
            }
            if (paddingDiv) {
                paddingDiv.textContent = 'padding: ' + (styles['padding-top']||'0') + ' / ' +
                    (styles['padding-right']||'0') + ' / ' + (styles['padding-bottom']||'0') +
                    ' / ' + (styles['padding-left']||'0');
            }
            if (contentDiv) {
                contentDiv.textContent = Math.round(el.clientWidth) + '×' + Math.round(el.clientHeight) + 'px';
            }
        }
    }

    function bindPanelInputs() {
        var body = document.getElementById('dePanelBody');
        if (!body) return;
        body.querySelectorAll('.de-property-input').forEach(function (input) {
            input.addEventListener('input', function () {
                if (!selectedEl) return;
                var prop = this.getAttribute('data-prop');
                var val = parseFloat(this.value);
                if (isNaN(val)) return;
                var newVal = val + 'px';

                // 直接应用到元素
                var cssProp = prop.replace(/-([a-z])/g, function (m, c) { return c.toUpperCase(); });
                selectedEl.style[cssProp] = newVal;

                recordChange(selectedEl, prop, newVal);
                updateHandlePositions(selectedEl);
                updateHighlight(selectedEl);
                updatePanelValues(selectedEl);
            });
        });
    }

    // ==================== CSS 选择器生成 ====================
    function getCSSSelector(el) {
        if (el.id) return '#' + el.id;

        // 只用元素自身的 class，不拼完整路径（更容易匹配 style.css 中的已有规则）
        var meaningful = [];
        if (el.classList && el.classList.length > 0) {
            for (var i = 0; i < el.classList.length; i++) {
                var c = el.classList[i];
                if (/^(d-|flex-|align-|justify-|gap-|p-|m-|w-|h-|text-|bg-|border-|rounded-|col-|row|mb-|mt-|ms-|me-|pb-|pt-|ps-|pe-|fs-|fw-|lh-|position-|overflow-|opacity-|show|active|open|mini|expanded|selected|copied|de-)/.test(c)) continue;
                meaningful.push(c);
            }
        }
        if (meaningful.length > 0) return '.' + meaningful.join('.');
        return el.tagName.toLowerCase();
    }

    // ==================== 保存 ====================
    function saveChanges() {
        if (Object.keys(changes).length === 0) {
            alert('没有需要保存的改动');
            return;
        }

        var cssText = generateCSSPatch();
        var count = changeCount;

        var headers = { 'Content-Type': 'application/json' };
        var token = localStorage.getItem('token');
        if (token) headers['Authorization'] = 'Bearer ' + token;

        fetch('/api/admin/save-css', {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({ changes: changes, cssText: cssText })
        }).then(function (resp) {
            if (resp.ok) {
                alert('已保存 ' + count + ' 处改动到 style.css');
                // 应用变更，清除选中效果的内联样式
                applyInlineToStylesheet();
                // 清空变更记录
                changes = {};
                changeCount = 0;
                updateChangeCounter();
                // 清除内联 style
                clearInlineStyles();
                if (selectedEl) updatePanel(selectedEl);
            } else {
                return resp.json().then(function (err) {
                    alert('保存失败：' + (err.message || '服务器错误'));
                });
            }
        }).catch(function () {
            alert('保存失败：网络错误');
        });
    }

    function generateCSSPatch() {
        var lines = [];
        Object.keys(changes).forEach(function (selector) {
            lines.push(selector + ' {');
            Object.keys(changes[selector]).forEach(function (prop) {
                lines.push('    ' + prop + ': ' + changes[selector][prop] + ';');
            });
            lines.push('}');
            lines.push('');
        });
        return lines.join('\n');
    }

    function applyInlineToStylesheet() {
        // 将已保存的 element.style 应用到持久化样式
        var style = document.createElement('style');
        style.textContent = generateCSSPatch();
        document.head.appendChild(style);
    }

    function clearInlineStyles() {
        // 清除已保存的内联样式，让 CSS 文件中的样式生效
        Object.keys(changes).forEach(function (selector) {
            var els = document.querySelectorAll(selector);
            els.forEach(function (el) {
                ['margin-top','margin-right','margin-bottom','margin-left',
                 'padding-top','padding-right','padding-bottom','padding-left'].forEach(function (prop) {
                    if (changes[selector][prop]) {
                        el.style[prop.replace(/-([a-z])/g, function (m, c) { return c.toUpperCase(); })] = '';
                    }
                });
            });
        });
    }

    // ==================== 退出 ====================
    function exitEdit() {
        if (changeCount > 0 && !confirm('有 ' + changeCount + ' 处未保存的改动，确定退出？')) return;
        isEditing = false;
        window._DE_ACTIVE = false;
        document.body.classList.remove('de-edit-mode');
        deselectElement();
        hideHover();
        clearInlineStyles();
        unbindEvents();
        endToolbarDrag(); // 清理拖拽状态
        var els = document.querySelectorAll(
            '.de-toolbar,.de-panel,.de-hover-highlight,.de-hover-tooltip,.de-selected-highlight,.de-selected-info,.de-handle,.de-guide-h,.de-guide-v'
        );
        els.forEach(function (el) { el.parentNode.removeChild(el); });
        toolbar = null; panel = null; changes = {}; changeCount = 0;
    }

    // ==================== 工具函数 ====================
    function escHtml(s) {
        return (s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
    function escHtmlAttr(s) {
        return (s || '').replace(/&/g, '&amp;').replace(/"/g, '&quot;');
    }

    // ==================== 启动 ====================
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
