// dashboard.js - логика только для dashboard вкладки

function initDashboard() {
    //console.log('Initializing Dashboard...');
    loadSitesData();
}

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
                updateStatistics(data);
                renderSites(data.sites);
            }
        })
        .catch(error => {
            console.log('Server error:', error);
        });
}

function updateStatistics(data) {
    //console.log('Updating statistics with:', data);

    // Простое обновление статистики
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

function renderSites(sitesData) {
    //console.log('Rendering sites:', sitesData);
    const container = document.getElementById('sites-container');

    if (!container) {
        console.error('sites-container element not found!');
        return;
    }

        container.innerHTML = '';

        if (!sitesData || sitesData.length === 0) {
            container.innerHTML = '<div class="no-sites">No sites available</div>';
            return;
        }

        sitesData.forEach((site, index) => {
            //console.log(`Site ${index}: ${site.name}, Status: ${site.status}, Status lower: ${site.status.toLowerCase()}`);
            const siteElement = createSiteElement(site, index);
            container.appendChild(siteElement);
        });
}

function createSiteElement(site, index) {
    const siteItem = document.createElement('div');
        siteItem.className = 'site-item';

        const statusTime = site.statusTime ? new Date(site.statusTime).toLocaleString('ru-RU') : 'N/A';
        const siteId = `site_${index}`;
        const statusClass = site.status ? site.status.toLowerCase() : 'unknown';


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

        // Функция для получения цвета по статусу
            function getStatusColor(status) {
                if (!status) return { bg: '#f8f9fa', text: '#6c757d' };

                switch(status.toLowerCase()) {
                    case 'indexed':
                        return { bg: '#d4edda', text: '#155724' }; // зеленый
                    case 'failed':
                        return { bg: '#f8d7da', text: '#721c24' }; // красный
                    case 'indexing':
                        return { bg: '#fff3cd', text: '#856404' }; // желтый
                    case 'crawling':
                        return { bg: '#dedcfe', text: '#383d41' };
                    case 'crawled':
                        return { bg: '#cce7ff', text: '#004085' }; // голубой
                    default:
                        return { bg: '#f8f9fa', text: '#6c757d' }; // серый по умолчанию
                }
            }

        const colors = getStatusColor(site.status);
        const inlineStyle = `style="background-color: ${colors.bg}; color: ${colors.text}"`;

        siteItem.innerHTML = `
                <div class="site-header" onclick="toggleSiteDetails('${siteId}')">
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

        return siteItem;
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

// Функция для получения цвета по статусу (должна быть доступна глобально)
/*function getStatusColor(status) {
    if (!status) return { bg: '#f8f9fa', text: '#6c757d' };

    switch(status.toLowerCase()) {
        case 'indexed':
            return { bg: '#d4edda', text: '#155724' };
        case 'failed':
            return { bg: '#f8d7da', text: '#721c24' };
        case 'indexing':
            return { bg: '#fff3cd', text: '#856404' };
        case 'crawling':
            return { bg: '#dedcfe', text: '#383d41' };
        case 'crawled':
            return { bg: '#cce7ff', text: '#004085' };
        default:
            return { bg: '#f8f9fa', text: '#6c757d' };
    }
}*/