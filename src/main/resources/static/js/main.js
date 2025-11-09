// main.js - общая логика для всех вкладок

// Функция для открытия вкладок
function openTab(tabName, element) {
    // Скрыть все вкладки
    const tabs = document.getElementsByClassName('tab');
    for (let i = 0; i < tabs.length; i++) {
        tabs[i].classList.remove('active');
    }

    // Убрать активный класс у всех кнопок
    const buttons = document.getElementsByClassName('menu-btn');
    for (let i = 0; i < buttons.length; i++) {
        buttons[i].classList.remove('active');
    }

    // Показать выбранную вкладку и активировать кнопку
    document.getElementById(tabName).classList.add('active');
    element.classList.add('active');

    // Инициализация конкретной вкладки
    initTab(tabName);
}

// Функция для инициализации вкладки
function initTab(tabName) {
    switch(tabName) {
        case 'dashboard':
            if (typeof initDashboard === 'function') {
                initDashboard();
            }
            break;
        case 'management':
            if (typeof initManagement === 'function') {
                initManagement();
            }
            break;
        case 'search':
            if (typeof initSearch === 'function') {
                initSearch();
            }
            break;
    }
}

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', function() {
    console.log('Page loaded!');
    // Инициализируем активную вкладку (обычно dashboard)
    const activeTab = document.querySelector('.tab.active');
    if (activeTab) {
        initTab(activeTab.id);
    }
});