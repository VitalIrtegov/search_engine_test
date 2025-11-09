// management.js - логика для MANAGEMENT вкладки

let sitesList = [];
let isIndexing = false;
let currentMessage = null;

function initManagement() {
    console.log('Initializing Management tab...');
    loadSitesForManagement();
    setupEventListeners();
}

function loadSitesForManagement() {
    fetch('/api/statistics')
        .then(response => response.json())
        .then(data => {
            if (data.result) {
                sitesList = data.statistics.detailed;
                populateSiteSelects();
            }
        })
        .catch(error => {
            console.error('Error loading sites:', error);
            showMessage('Error loading sites data', 'error');
        });
}

function populateSiteSelects() {
    const indexingSelect = document.getElementById('indexing-site-select');
    const deleteSelect = document.getElementById('delete-site-select');

    // Очищаем selects
    indexingSelect.innerHTML = '';
    deleteSelect.innerHTML = '';

    // Добавляем "All sites" только в первый select
    const allSitesOption = document.createElement('option');
    allSitesOption.value = 'all';
    allSitesOption.textContent = 'All sites';
    indexingSelect.appendChild(allSitesOption);

    // Добавляем сайты в оба selects
    sitesList.forEach(site => {
        const option1 = document.createElement('option');
        option1.value = site.url;
        option1.textContent = site.name;
        indexingSelect.appendChild(option1.cloneNode(true));

        const option2 = document.createElement('option');
        option2.value = site.url;
        option2.textContent = site.name;
        deleteSelect.appendChild(option2);
    });
}

function setupEventListeners() {
    // Кнопка START/STOP INDEXING
    document.getElementById('indexing-btn').addEventListener('click', toggleIndexing);

    // Кнопка ADD SITE
    document.getElementById('add-site-btn').addEventListener('click', addSite);

    // Кнопка DELETE SITE
    document.getElementById('delete-site-btn').addEventListener('click', deleteSite);

    // Валидация полей ввода
    document.getElementById('site-name-input').addEventListener('input', validateSiteForm);
    document.getElementById('site-url-input').addEventListener('input', validateSiteForm);
}

function toggleIndexing() {
    const indexingBtn = document.getElementById('indexing-btn');
    const select = document.getElementById('indexing-site-select');
    const selectedSite = select.value;

    if (!isIndexing) {
        // Запуск индексации
        isIndexing = true;
        indexingBtn.textContent = 'STOP INDEXING';
        indexingBtn.classList.add('indexing-active');

        // Блокируем другие кнопки
        setOtherButtonsState(true);

        if (selectedSite === 'all') {
            showMessage('Starting indexing for all sites...', 'info');
            simulateIndexingProcess('all');
        } else {
            const siteName = select.options[select.selectedIndex].text;
            showMessage(`Starting indexing for site: ${siteName}`, 'info');
            simulateIndexingProcess(siteName);
        }

        // Блокируем select во время индексации
        select.disabled = true;

    } else {
        // Остановка индексации
        isIndexing = false;
        indexingBtn.textContent = 'START INDEXING';
        indexingBtn.classList.remove('indexing-active');

        // Разблокируем другие кнопки
        setOtherButtonsState(false);

        showMessage('Indexing stopped by user', 'info');

        // Разблокируем select
        select.disabled = false;

        stopIndexingProcess();
    }
}

// Функция для блокировки/разблокировки других кнопок
function setOtherButtonsState(disabled) {
    const addSiteBtn = document.getElementById('add-site-btn');
    const deleteSiteBtn = document.getElementById('delete-site-btn');
    const siteNameInput = document.getElementById('site-name-input');
    const siteUrlInput = document.getElementById('site-url-input');
    const deleteSelect = document.getElementById('delete-site-select');

    if (disabled) {
        // Блокируем
        addSiteBtn.disabled = true;
        addSiteBtn.classList.add('disabled');
        deleteSiteBtn.disabled = true;
        deleteSiteBtn.classList.add('disabled');
        siteNameInput.disabled = true;
        siteUrlInput.disabled = true;
        deleteSelect.disabled = true;
    } else {
        // Разблокируем
        addSiteBtn.disabled = false;
        addSiteBtn.classList.remove('disabled');
        deleteSiteBtn.disabled = false;
        deleteSiteBtn.classList.remove('disabled');
        siteNameInput.disabled = false;
        siteUrlInput.disabled = false;
        deleteSelect.disabled = false;

        // Перевалидируем форму добавления сайта
        validateSiteForm();
    }
}

function simulateIndexingProcess(site) {
    console.log(`Indexing process started for: ${site}`);

    // В реальном приложении здесь будет вызов API
    // Для демонстрации можно добавить имитацию прогресса
    if (site === 'all') {
        // Имитация индексации всех сайтов (дольше)
        setTimeout(() => {
            if (isIndexing) {
                showMessage('Indexing in progress... 50% complete', 'info');
            }
        }, 2000);

        setTimeout(() => {
            if (isIndexing) {
                // Автоматически завершаем индексацию через 5 секунд
                autoStopIndexing();
            }
        }, 5000);
    } else {
        // Имитация индексации одного сайта (быстрее)
        setTimeout(() => {
            if (isIndexing) {
                autoStopIndexing();
            }
        }, 3000);
    }
}

function autoStopIndexing() {
    if (isIndexing) {
        const indexingBtn = document.getElementById('indexing-btn');
        const select = document.getElementById('indexing-site-select');

        isIndexing = false;
        indexingBtn.textContent = 'START INDEXING';
        indexingBtn.classList.remove('indexing-active');

        // Разблокируем другие кнопки
        setOtherButtonsState(false);

        showMessage('Indexing completed successfully', 'success');

        // Разблокируем select
        select.disabled = false;
    }
}

function stopIndexingProcess() {
    console.log('Indexing process stopped');
    // В реальном приложении здесь будет вызов API для остановки индексации
}

function addSite() {
    if (isIndexing) {
        showMessage('Cannot add site while indexing is in progress', 'error');
        return;
    }

    const name = document.getElementById('site-name-input').value.trim();
    const url = document.getElementById('site-url-input').value.trim();

    if (!name || !url) {
        showMessage('Please fill in all fields', 'error');
        return;
    }

    if (!isValidUrl(url)) {
        showMessage('Please enter a valid URL', 'error');
        return;
    }

    showMessage(`Adding site: ${name} (${url})`, 'info');
    // Здесь будет вызов API для добавления сайта

    // Очищаем поля после добавления
    document.getElementById('site-name-input').value = '';
    document.getElementById('site-url-input').value = '';
    validateSiteForm();

    // Перезагружаем список сайтов
    setTimeout(() => {
        loadSitesForManagement();
    }, 1000);
}

function deleteSite() {
    if (isIndexing) {
            showMessage('Cannot delete site while indexing is in progress', 'error');
            return;
        }

        const select = document.getElementById('delete-site-select');
        const selectedSite = select.value;

        if (!selectedSite) {
            showMessage('Please select a site to delete', 'error');
            return;
        }

        const siteName = select.options[select.selectedIndex].text;

        // Удаляем сразу без подтверждения
        showMessage(`Deleting site: ${siteName}...`, 'info');

        // Здесь будет вызов API для удаления сайта
        // fetch(`/api/sites/${selectedSite}`, { method: 'DELETE' })
        //     .then(response => response.json())
        //     .then(data => {
        //         if (data.success) {
        //             showMessage(`Site "${siteName}" deleted successfully`, 'success');
        //             loadSitesForManagement();
        //         } else {
        //             showMessage(`Error deleting site: ${data.message}`, 'error');
        //         }
        //     })
        //     .catch(error => {
        //         showMessage('Error deleting site', 'error');
        //         console.error('Delete error:', error);
        //     });

        // Временная заглушка для демонстрации
        console.log('API call: DELETE site', selectedSite);

        // Очищаем select после удаления
        select.value = '';

        // Перезагружаем список сайтов и показываем успешное сообщение
        setTimeout(() => {
            loadSitesForManagement();
            showMessage(`Site "${siteName}" deleted successfully`, 'success');
        }, 1000);
}

function validateSiteForm() {
    const name = document.getElementById('site-name-input').value.trim();
    const url = document.getElementById('site-url-input').value.trim();
    const button = document.getElementById('add-site-btn');

    const isValid = name && url && isValidUrl(url) && !isIndexing;
    button.disabled = !isValid;
}

function isValidUrl(string) {
    try {
        new URL(string);
        return true;
    } catch (_) {
        return false;
    }
}

function showMessage(text, type) {
    const container = document.getElementById('messages-container');

    // Удаляем предыдущее сообщение, если есть
    if (currentMessage) {
        currentMessage.remove();
    }

    // Создаем новое сообщение
    currentMessage = document.createElement('div');
    currentMessage.className = `message ${type}`;
    currentMessage.textContent = text;

    container.appendChild(currentMessage);

    // Автоудаление сообщения через 5 секунд
    /*setTimeout(() => {
        if (currentMessage && currentMessage.parentNode) {
            currentMessage.remove();
            currentMessage = null;
        }
    }, 5000);*/
}