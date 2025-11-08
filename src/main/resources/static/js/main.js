// Функция для открытия вкладок
function openTab(tabName, element) {
    const tabs = document.getElementsByClassName('tab');
    for (let i = 0; i < tabs.length; i++) {
        tabs[i].classList.remove('active');
    }

    const buttons = document.getElementsByClassName('menu-btn');
    for (let i = 0; i < buttons.length; i++) {
        buttons[i].classList.remove('active');
    }

    document.getElementById(tabName).classList.add('active');
    element.classList.add('active');
}

// Функция для загрузки данных о сайтах
function loadSitesData() {
    //console.log('Loading sites data from server...');

    fetch('/api/statistics')
        .then(response => {
            if (!response.ok) {
                throw new Error('Network response was not ok');
            }
            return response.json();
        })
        .then(data => {
            //console.log('Data from server:', data);
            if (data.result) {
                updateStatistics(data.statistics.total);
                renderSites(data.statistics.detailed);
            }
        })
        .catch(error => {
            console.log('Server error:', error);
        });
}

// Функция для обновления статистики dashboard
function updateStatistics(totalData) {
    //console.log('Updating statistics with:', totalData);
    document.getElementById('sites-count').textContent = totalData.sites;
    document.getElementById('pages-count').textContent = totalData.pages;
    document.getElementById('lemmas-count').textContent = totalData.lemmas;
}

// Функция для рендеринга сайтов
function renderSites(sitesData) {
    //console.log('Rendering sites:', sitesData);
    const container = document.getElementById('sites-container');
    if (!container) {
        console.error('sites-container not found!');
        return;
    }

    container.innerHTML = '';

    sitesData.forEach((site, index) => {
        const siteElement = createSiteElement(site, index);
        container.appendChild(siteElement);
    });
}

// Функция для создания элемента сайта
function createSiteElement(site, index) {
    const siteItem = document.createElement('div');
    siteItem.className = 'site-item';

    const statusTime = new Date(site.statusTime).toLocaleString('ru-RU');
    const siteId = `site_${index}`;

    siteItem.innerHTML = `
        <div class="site-header" onclick="toggleSiteDetails('${siteId}')">
            <div class="site-info">
                <div class="site-name">${site.name}</div>
                <div class="site-url">${site.url}</div>
            </div>
            <div class="site-status">
                <span class="status-badge status-${site.status.toLowerCase()}">${site.status}</span>
                <div class="arrow" id="arrow-${siteId}"></div>
            </div>
        </div>
        <div class="site-details" id="details-${siteId}">
            <div class="detail-item">
                <span class="detail-label">Status time:</span>
                <span class="detail-value">${statusTime}</span>
            </div>
            <div class="detail-item">
                <span class="detail-label">Pages:</span>
                <span class="detail-value">${site.pages}</span>
            </div>
            <div class="detail-item">
                <span class="detail-label">Lemmas:</span>
                <span class="detail-value">${site.lemmas}</span>
            </div>
            ${site.error ? `
            <div class="detail-item">
                <span class="detail-label">Error:</span>
                <span class="detail-value error">${site.error}</span>
            </div>
            ` : ''}
        </div>
    `;

    return siteItem;
}

// Функция для переключения отображения деталей сайта
function toggleSiteDetails(siteId) {
    const details = document.getElementById(`details-${siteId}`);
    const arrow = document.getElementById(`arrow-${siteId}`);

    if (details && arrow) {
        details.classList.toggle('open');
        arrow.classList.toggle('open');
    }
}

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', function() {
    //console.log('Page loaded!');
    loadSitesData();
});