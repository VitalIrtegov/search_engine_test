// search.js - логика для SEARCH вкладки

let searchSitesList = [];
let currentSearchMessage = null;

function initSearch() {
    console.log('Initializing Search tab...');
    loadSitesForSearch();
    setupSearchEventListeners();
}

function loadSitesForSearch() {
    fetch('/api/statistics')
        .then(response => response.json())
        .then(data => {
            if (data.result) {
                searchSitesList = data.statistics.detailed;
                populateSearchSiteSelect();
            }
        })
        .catch(error => {
            console.error('Error loading sites for search:', error);
            showSearchMessage('Error loading sites data', 'error');
        });
}

function populateSearchSiteSelect() {
    const searchSelect = document.getElementById('search-site-select');

    // Очищаем select
    searchSelect.innerHTML = '';

    // Добавляем опцию для поиска по всем сайтам
    const allSitesOption = document.createElement('option');
    allSitesOption.value = 'all';
    allSitesOption.textContent = 'All sites';
    searchSelect.appendChild(allSitesOption);

    // Добавляем отдельные сайты
    searchSitesList.forEach(site => {
        const option = document.createElement('option');
        option.value = site.url;
        option.textContent = site.name;
        searchSelect.appendChild(option);
    });
}

function setupSearchEventListeners() {
    // Кнопка SEARCH
    document.getElementById('search-btn').addEventListener('click', performSearch);

    // Поиск при нажатии Enter в поле ввода
    document.getElementById('search-query-input').addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            performSearch();
        }
    });

    // Валидация формы поиска
    document.getElementById('search-query-input').addEventListener('input', validateSearchForm);
}

function performSearch() {
    const query = document.getElementById('search-query-input').value.trim();
    const siteSelect = document.getElementById('search-site-select');
    const selectedSite = siteSelect.value;

    if (!query) {
        showSearchMessage('Please enter a search query', 'error');
        return;
    }

    // Показываем сообщение о начале поиска
    const siteName = selectedSite === 'all' ? 'all sites' : siteSelect.options[siteSelect.selectedIndex].text;
    showSearchMessage(`Searching for "${query}" in ${siteName}...`, 'info');

    // Очищаем предыдущие результаты
    clearSearchResults();

    // Здесь будет вызов API для поиска
    // Для демонстрации используем временную заглушку
    simulateSearch(query, selectedSite);
}

function simulateSearch(query, site) {
    console.log(`Searching for: "${query}" in site: ${site}`);

    // Имитация задержки поиска
    setTimeout(() => {
        // Генерируем демонстрационные результаты
        const mockResults = generateMockResults(query, site);
        displaySearchResults(mockResults);

        if (mockResults.length > 0) {
            showSearchMessage(`Found ${mockResults.length} results for "${query}"`, 'success');
        } else {
            showSearchMessage(`No results found for "${query}"`, 'info');
        }
    }, 1000);
}

function generateMockResults(query, site) {
    // Демонстрационные данные
    if (query.toLowerCase().includes('not found') || query.toLowerCase().includes('error')) {
        return [];
    }

    const baseUrl = site === 'all' || site === '' ? 'https://www.example.com' : site;
    const siteName = site === 'all' || site === '' ? 'Example Site' :
                    document.getElementById('search-site-select').options[
                        document.getElementById('search-site-select').selectedIndex
                    ].text;

    return [
        {
            title: `Search Result for "${query}" - 1`,
            url: `${baseUrl}/search/result1`,
            snippet: `This is the first search result for your query "${query}". The search functionality is working correctly and returning relevant results from ${siteName}.`
        },
        {
            title: `Search Result for "${query}" - 2`,
            url: `${baseUrl}/search/result2`,
            snippet: `This is the second search result. Your search for "${query}" found multiple relevant pages in our index. The search engine is processing your request effectively.`
        },
        {
            title: `Search Result for "${query}" - 3`,
            url: `${baseUrl}/search/result3`,
            snippet: `Third result for "${query}". The search system is designed to provide accurate and fast results across all indexed pages from the selected sites.`
        }
    ];
}

function displaySearchResults(results) {
    const container = document.getElementById('search-results-container');

    if (!results || results.length === 0) {
        container.innerHTML = '<div class="no-results">No results found</div>';
        return;
    }

    container.innerHTML = '';

    results.forEach((result, index) => {
        const resultElement = createResultElement(result, index);
        container.appendChild(resultElement);
    });
}

function createResultElement(result, index) {
    const resultItem = document.createElement('div');
    resultItem.className = 'search-result-item';

    resultItem.innerHTML = `
        <div class="result-title">${result.title}</div>
        <div class="result-url">${result.url}</div>
        <div class="result-snippet">${result.snippet}</div>
    `;

    return resultItem;
}

function clearSearchResults() {
    const container = document.getElementById('search-results-container');
    container.innerHTML = '';
}

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