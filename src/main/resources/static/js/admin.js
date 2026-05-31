// 本地书签后台管理脚本

document.addEventListener('DOMContentLoaded', function () {
    // 侧边栏折叠
    const sidebarToggle = document.getElementById('sidebarToggle');
    const sidebar = document.getElementById('sidebar');
    const content = document.querySelector('.content');

    if (sidebarToggle && sidebar) {
        sidebarToggle.addEventListener('click', function () {
            sidebar.classList.toggle('collapsed');
            if (content) {
                content.style.marginLeft = sidebar.classList.contains('collapsed') ? '70px' : '240px';
            }
        });
    }

    // 当前时间
    function updateTime() {
        const el = document.getElementById('currentTime');
        if (el) {
            const now = new Date();
            el.textContent = now.toLocaleString('zh-CN', {
                year: 'numeric', month: '2-digit', day: '2-digit',
                hour: '2-digit', minute: '2-digit', second: '2-digit'
            });
        }
    }
    updateTime();
    setInterval(updateTime, 1000);

    // 当前激活菜单高亮
    const currentPath = window.location.pathname;
    document.querySelectorAll('.sidebar .nav-link').forEach(link => {
        const href = link.getAttribute('href');
        if (href && currentPath.startsWith(href) && href !== '/admin/index') {
            link.classList.add('active');
        } else if (href === currentPath) {
            link.classList.add('active');
        }
    });
});
