// dashboard.js - логика только для dashboard вкладки

let dashboardRefreshInterval = null;
let currentSitesData = [];

function initDashboard() {
    //console.log('Initializing Dashboard...');
    loadSitesData();

    // автообновление каждые 2 секунды
    startDashboardAutoRefresh();
}

function startDashboardAutoRefresh() {
    if (dashboardRefreshInterval) clearInterval(dashboardRefreshInterval);
    dashboardRefreshInterval = setInterval(() => { loadSitesData(); }, 2000); // каждые 2 секунды
}

// Останавка автообновления при переходе на другую вкладку
function stopDashboardAutoRefresh() {
    if (dashboardRefreshInterval) {
        clearInterval(dashboardRefreshInterval);
        dashboardRefreshInterval = null;
    }
}

function loadSitesData() {
    //console.log('Loading sites data from server...');
    fetch('/api/statistics')
            .then(response => response.json())
            .then(data => {
                if (data.result) {
                    updateStatistics(data);
                    updateSitesData(data.sites);
                }
            })
            .catch(error => console.log('Server error:', error));
}

function updateStatistics(data) {
    //console.log('Updating statistics with:', data);
    const sitesElement = document.getElementById('sites-count');
        const pagesElement = document.getElementById('pages-count');
        const lemmasElement = document.getElementById('lemmas-count');
        const indexingElement = document.getElementById('indexing-status');

        if (sitesElement) sitesElement.textContent = data.totalSites || 0;
        if (pagesElement) pagesElement.textContent = data.totalPages || 0;
        if (lemmasElement) lemmasElement.textContent = data.totalLemmas || 0;

        if (indexingElement) {
            indexingElement.textContent = data.indexing ? 'RUNNING' : 'STOPPED';
            indexingElement.className = data.indexing ? 'status indexing' : 'status stopped';
        }
    //console.log('Statistics updated successfully');
}

function updateSitesData(sitesData) {
    const container = document.getElementById('sites-container');
    if (!container) return;

    // Если контейнер пустой или количество сайтов изменилось - создаем заново
    if (container.children.length === 0 || currentSitesData.length !== sitesData.length) {
        currentSitesData = sitesData;
        renderSites(sitesData);
        return;
    }

    // Иначе только обновляем данные
    sitesData.forEach((site, index) => {
        updateSiteElement(site, index);
    });

    currentSitesData = sitesData;
}

function renderSites(sitesData) {
    //console.log('Rendering sites:', sitesData);
    const container = document.getElementById('sites-container');
        if (!container) return;

        container.innerHTML = '';

        if (!sitesData || sitesData.length === 0) {
            container.innerHTML = '<div class="no-sites">No sites available</div>';
            return;
        }

        sitesData.forEach((site, index) => {
            const siteElement = createSiteElement(site, index);
            container.appendChild(siteElement);
        });

        /*sitesData.forEach((site, index) => {
            //console.log(`Site ${index}: ${site.name}, Status: ${site.status}, Status lower: ${site.status.toLowerCase()}`);
            const siteElement = createSiteElement(site, index);
            container.appendChild(siteElement);
        });*/
}

function createSiteElement(site, index) {
const siteItem = document.createElement('div');
    siteItem.className = 'site-item';
    siteItem.id = `site_${index}`;

    const siteId = `site_${index}`;
    const siteInfo = getSiteHTML(site, index);

    siteItem.innerHTML = siteInfo;

    // Обработчик на заголовок сайта
    const siteHeader = siteItem.querySelector('.site-header');
    siteHeader.addEventListener('click', function(e) {
        e.preventDefault();
        const details = document.getElementById(`details-${siteId}`);
        const arrow = document.getElementById(`arrow-${siteId}`);
        if (details && arrow) {
            details.classList.toggle('open');
            arrow.classList.toggle('open');
        }
    });

    return siteItem;
}

function updateSiteElement(site, index) {
    const siteElement = document.getElementById(`site_${index}`);
    if (!siteElement) return;

    // Сохраняем текущее состояние открытости
    const details = document.getElementById(`details-site_${index}`);
    const wasOpen = details && details.classList.contains('open');

    // Обновляем только нужные элементы
    const statusElement = siteElement.querySelector('.status-badge');
    const timeElement = siteElement.querySelector('.detail-item:nth-child(1) .detail-value');
    const pagesElement = siteElement.querySelector('.detail-item:nth-child(2) .detail-value');
    const lemmasElement = siteElement.querySelector('.detail-item:nth-child(3) .detail-value');
    const errorElement = siteElement.querySelector('.error');

    if (statusElement) {
            const colors = getStatusColor(site.status);
            statusElement.style.backgroundColor = colors.bg;
            statusElement.style.color = colors.text;
            statusElement.textContent = site.status || 'UNKNOWN';
        }

    if (timeElement) {
            timeElement.textContent = site.statusTime ?
                new Date(site.statusTime).toLocaleString('ru-RU') : 'N/A';
        }

        if (pagesElement) pagesElement.textContent = site.pages || 0;
        if (lemmasElement) lemmasElement.textContent = site.lemmas || 0;

    // Обновляем или добавляем ошибку
    if (site.error) {
        if (!errorElement) {
            const errorRow = document.createElement('div');
            errorRow.className = 'detail-item';
            errorRow.innerHTML = `<span class="detail-label">Error:</span>
                                 <span class="detail-value error">${site.error}</span>`;
            details.appendChild(errorRow);
        } else {
            errorElement.textContent = site.error;
        }
    } else if (errorElement) {
        errorElement.closest('.detail-item').remove();
    }
}

function getSiteHTML(site, index) {
    const siteId = `site_${index}`;
    const statusTime = site.statusTime ? new Date(site.statusTime).toLocaleString('ru-RU') : 'N/A';
    const colors = getStatusColor(site.status);
    const inlineStyle = `style="background-color: ${colors.bg}; color: ${colors.text}"`;

    return `
        <div class="site-header">
            <div class="site-info">
                <div class="site-name">${site.name || 'Unnamed Site'}</div>
                <div class="site-url">${site.url || 'No URL'}</div>
            </div>
            <div class="site-status">
                <span class="status-badge" ${inlineStyle}>${site.status || 'UNKNOWN'}</span>
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
                <span class="detail-value">${site.pages || 0}</span>
            </div>
            <div class="detail-item">
                <span class="detail-label">Lemmas:</span>
                <span class="detail-value">${site.lemmas || 0}</span>
            </div>
            ${site.error ? `
            <div class="detail-item">
                <span class="detail-label">Error:</span>
                <span class="detail-value error">${site.error}</span>
            </div>
            ` : ''}
        </div>
    `;
}

function getStatusColor(status) {
    if (!status) return { bg: '#f8f9fa', text: '#6c757d' };
    switch(status.toLowerCase()) {
        case 'indexed': return { bg: '#d4edda', text: '#155724' };
        case 'failed': return { bg: '#f8d7da', text: '#721c24' };
        case 'indexing': return { bg: '#fff3cd', text: '#856404' };
        default: return { bg: '#f8f9fa', text: '#6c757d' };
    }
}

function updateSiteStatus(siteId, newStatus) {
    const statusElement = document.querySelector(`[data-site-id="${siteId}"] .status-badge`);
        if (statusElement) {
            const colors = getStatusColor(newStatus);
            statusElement.style.backgroundColor = colors.bg;
            statusElement.style.color = colors.text;
            statusElement.textContent = newStatus;
        }
}

function toggleSiteDetails(siteId) {
    const details = document.getElementById(`details-${siteId}`);
        const arrow = document.getElementById(`arrow-${siteId}`);

        if (details && arrow) {
            details.classList.toggle('open');
            arrow.classList.toggle('open');
        }
}

// Функция для получения цвета по статусу
/*function getStatusColor(status) {
            switch(status.toLowerCase()) {
                case 'indexed':
                    return { bg: '#d4edda', text: '#155724' }; // зеленый
                case 'failed':
                    return { bg: '#f8d7da', text: '#721c24' }; // красный

                case 'indexing':
                    return { bg: '#fff3cd', text: '#856404' }; // желтый

                case 'crawling':
                    //return { bg: '#e2e3e5', text: '#383d41' }; // серый
                    //return { bg: '#a29bfe', text: '#2d2a6b' }; // Фиолетовый d0cdfe
                    //return { bg: '#d0cdfe', text: '#2d2a6b' }; // dedcfe
                    return { bg: '#dedcfe', text: '#383d41' };

                case 'crawled':
                    return { bg: '#cce7ff', text: '#004085' }; // голубой

                default:
                    return { bg: '#f8f9fa', text: '#6c757d' }; // серый по умолчанию
            }
}*/