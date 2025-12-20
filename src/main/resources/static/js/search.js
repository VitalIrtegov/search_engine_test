// search.js - логика для SEARCH вкладки

let searchSitesList = [];
let currentSearchMessage = null;

function initSearch() {
    //console.log('Initializing Search tab...');
    loadSitesForSearch();
    setupSearchEventListeners();
}

function loadSitesForSearch() {
    fetch('/api/statistics')
            .then(response => response.json())
            .then(data => {
                //console.log('API Response:', data);

                if (data.result && data.sites) {
                    searchSitesList = data.sites;
                    //console.log('Loaded sites:', searchSitesList);
                    populateSearchSiteSelect();
                } else {
                    showSearchMessage('Cannot load sites list', 'error');
                }
            })
            .catch(error => {
                console.error('Error loading sites:', error);
                showSearchMessage('Error loading sites: ' + error.message, 'error');
            });
}

function populateSearchSiteSelect() {
    const searchSelect = document.getElementById('search-site-select');

        // Очищаем select
        searchSelect.innerHTML = '<option value="">All sites</option>';

        // Добавляем отдельные сайты
        if (searchSitesList && searchSitesList.length > 0) {
            searchSitesList.forEach(site => {
                const option = document.createElement('option');
                option.value = site.url;
                option.textContent = site.name || site.url;
                searchSelect.appendChild(option);
            });
        }
}

function setupSearchEventListeners() {
    const searchBtn = document.getElementById('search-btn');
        const queryInput = document.getElementById('search-query-input');

        searchBtn.addEventListener('click', performSearch);

        queryInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                performSearch();
            }
        });

        queryInput.addEventListener('input', validateSearchForm);
}

function performSearch() {
    const query = document.getElementById('search-query-input').value.trim();

        if (!query) {
            showSearchMessage('Please enter a search query', 'error');
            return;
        }

        showSearchMessage(`Searching for "${query}"...`, 'info');
        clearSearchResults();
        executeSearch(query);
}

function executeSearch(query) {
    const siteSelect = document.getElementById('search-site-select');
        const selectedSite = siteSelect.value;

        let url = `/api/search?query=${encodeURIComponent(query)}`;
        if (selectedSite) {
            url += `&site=${encodeURIComponent(selectedSite)}`;
        }

        //console.log('Search URL:', url);

        fetch(url)
            .then(response => response.json())
            .then(data => {
                if (!data.result) {
                    throw new Error(data.error || 'Search failed');
                }
                displaySearchResults(data);
            })
            .catch(error => {
                console.error('Search error:', error);
                showSearchMessage(error.message, 'error');
            });
}

function displaySearchResults(data) {
    const container = document.getElementById('search-results-container');

        if (!data.data || data.data.length === 0) {
            container.innerHTML = '<div class="no-results">No results found</div>';
            showSearchMessage('No results found', 'info');
            return;
        }

        container.innerHTML = '';

        data.data.forEach(result => {
            const resultElement = createResultElement(result);
            container.appendChild(resultElement);
        });

        showSearchMessage(`Found ${data.count} results`, 'success');
}

function createResultElement(result) {
    const resultItem = document.createElement('div');
        resultItem.className = 'search-result-item';

        const fullUrl = result.site + (result.uri || '');

        resultItem.innerHTML = `
            <div class="result-title">${result.title || 'No title'}</div>
            <div class="result-url">${fullUrl}</div>
            <div class="result-snippet">${result.snippet || 'No snippet'}</div>
        `;

        return resultItem;
}

function clearSearchResults() {
    const container = document.getElementById('search-results-container');
        container.innerHTML = '';
}

/*function updateSearchStats(count) {
    const statsContainer = document.getElementById('search-stats');
    if (!statsContainer) return;

    statsContainer.innerHTML = `
        <div class="search-stats-info">
            Found: <span class="search-stats-count">${count}</span> results
        </div>
    `;
}

function addShowMoreButton(totalCount) {
    const remaining = totalCount - (currentSearchOffset + currentSearchLimit);

    if (remaining <= 0) return;

    // Удаляем старую кнопку если есть
    const oldButton = document.getElementById('show-more-button');
    if (oldButton) oldButton.remove();

    const container = document.getElementById('search-results-container');
    const button = document.createElement('button');
    button.id = 'show-more-button';
    button.className = 'show-more-button';
    button.textContent = `Show more (${remaining} remaining)`;

    button.addEventListener('click', function() {
        currentSearchOffset += currentSearchLimit;
        executeSearch();
        button.disabled = true;
        button.textContent = 'Loading...';
    });

    container.appendChild(button);
}

function clearSearchResults() {
    const container = document.getElementById('search-results-container');
    const statsContainer = document.getElementById('search-stats');

    container.innerHTML = '';
    if (statsContainer) {
        statsContainer.innerHTML = '';
    }
}*/

function validateSearchForm() {
    const query = document.getElementById('search-query-input').value.trim();
        const button = document.getElementById('search-btn');
        button.disabled = !query;
}

function showSearchMessage(text, type) {
    const container = document.getElementById('search-messages-container');

    // Удаляем предыдущее сообщение, если есть
    if (currentSearchMessage) {
        currentSearchMessage.remove();
    }

    // Создаем новое сообщение
    currentSearchMessage = document.createElement('div');
    currentSearchMessage.className = `search-message ${type}`;
    currentSearchMessage.textContent = text;

    container.appendChild(currentSearchMessage);

    // Автоудаление сообщения через 5 секунд
    /*setTimeout(() => {
        if (currentSearchMessage && currentSearchMessage.parentNode) {
            currentSearchMessage.remove();
            currentSearchMessage = null;
        }
    }, 5000);*/
}