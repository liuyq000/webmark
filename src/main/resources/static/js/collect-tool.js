// ========== 网页收集工具 ==========

function showCollectTool() {
    var existing = document.getElementById('_collectToolModal');
    if (existing) { existing.remove(); return; }
    loadPartial('/partials/collect-tool-panel.html', function(html) {
        var container = document.getElementById('toolModals');
        container.innerHTML = html;
        // 动态设置 bookmarklet 的 href（使用绝对路径，确保从任何网站点击都能正确跳转）
        var baseUrl = window.location.protocol + '//' + window.location.host + '/';
        var bmLink = container.querySelector('#_collectToolModal a.bm-btn-primary');
        if (bmLink) {
            bmLink.href = "javascript:(function(){var d;var ds='';var m=document.getElementsByTagName('meta');for(var x=0,y=m.length;x<y;x++){if(m[x].name.toLowerCase()=='description'){d=m[x];}}if(d){ds='&description='+encodeURIComponent(d.content);}var win=window.open('" + baseUrl + "addbookmark.html?from=webtool&url='+encodeURIComponent(document.URL)+ds+'&title='+encodeURIComponent(document.title)+'&charset='+document.charset,'_blank');win.focus();})();";
        }
    });
}

function closeCollectTool() {
    var el = document.getElementById('_collectToolModal');
    if (el) el.remove();
}
