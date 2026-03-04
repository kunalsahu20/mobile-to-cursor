/** Vexra Theme Toggle — persists across all pages via localStorage */
(function () {
    if (localStorage.getItem('vexra-theme') === 'light') {
        document.documentElement.classList.add('light');
    }
})();

function toggleTheme() {
    document.documentElement.classList.toggle('light');
    var isLight = document.documentElement.classList.contains('light');
    localStorage.setItem('vexra-theme', isLight ? 'light' : 'dark');
    updateToggleIcons();
}

function updateToggleIcons() {
    var isLight = document.documentElement.classList.contains('light');
    var sun = document.getElementById('icon-sun');
    var moon = document.getElementById('icon-moon');
    if (sun) sun.style.display = isLight ? 'none' : 'block';
    if (moon) moon.style.display = isLight ? 'block' : 'none';
}

document.addEventListener('DOMContentLoaded', updateToggleIcons);
