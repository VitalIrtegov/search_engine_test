// management.js - логика для MANAGEMENT вкладки

let sitesList = [];
let isCrawling = false;
let isIndexing = false;
let currentMessage = null;

function initManagement() {
    //console.log('Initializing Management tab...');
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
    const crawlingSelect = document.getElementById('crawling-site-select');
    const indexingSelect = document.getElementById('indexing-site-select');
    const deleteSelect = document.getElementById('delete-site-select');

    // Очищаем selects
    crawlingSelect.innerHTML = '';
    indexingSelect.innerHTML = '';
    deleteSelect.innerHTML = '';

    // "All sites" В CRAWLING SELECT
    const allSitesOptionCrawling = document.createElement('option');
    allSitesOptionCrawling.value = 'all';
    allSitesOptionCrawling.textContent = 'All sites';
    crawlingSelect.appendChild(allSitesOptionCrawling);

    // ДОБАВЛЯЕМ "All sites" в indexing select (как было)
    const allSitesOptionIndexing = document.createElement('option');
    allSitesOptionIndexing.value = 'all';
    allSitesOptionIndexing.textContent = 'All sites';
    indexingSelect.appendChild(allSitesOptionIndexing);

    // Для delete select пустой option
    const emptyOption = document.createElement('option');
    emptyOption.value = '';
    emptyOption.textContent = '-- Select site to delete --';
    deleteSelect.appendChild(emptyOption);

    // Добавляем сайты во все selects
    sitesList.forEach(site => {
        // Для crawling select
        const option1 = document.createElement('option');
        option1.value = site.url;
        option1.textContent = site.name;
        crawlingSelect.appendChild(option1);

        // Для indexing select
        const option2 = document.createElement('option');
        option2.value = site.url;
        option2.textContent = site.name;
        indexingSelect.appendChild(option2);

        // Для delete select
        const option3 = document.createElement('option');
        option3.value = site.url;
        option3.textContent = site.name;
        deleteSelect.appendChild(option3);
    });
}

function setupEventListeners() {
    // Кнопка START/STOP CRAWLING
    document.getElementById('crawling-btn').addEventListener('click', toggleCrawling);

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

// crawling(обход)
/*function toggleCrawling() {
    const crawlingBtn = document.getElementById('crawling-btn');
        const select = document.getElementById('crawling-site-select');
        const selectedSite = select.value;

        if (!isCrawling) {
            // Запуск crawling
            if (!selectedSite) {
                showMessage('Please select a site for crawling', 'error');
                return;
            }

            isCrawling = true;
            crawlingBtn.textContent = 'STOP CRAWLING';
            crawlingBtn.classList.add('crawling-active');

            // Блокируем другие кнопки
            setOtherButtonsState(true);

            // Вызов API для запуска crawling
            fetch(`/api/crawling/start?siteUrl=${encodeURIComponent(selectedSite)}`, {
                method: 'POST'
            })
            .then(response => response.json())
            .then(data => {
                if (data.result) {
                    showMessage(data.message, 'info');
                } else {
                    showMessage(data.message, 'error');
                    // Откатываем состояние если ошибка
                    isCrawling = false;
                    crawlingBtn.textContent = 'START CRAWLING';
                    crawlingBtn.classList.remove('crawling-active');
                    setOtherButtonsState(false);
                }
            })
            .catch(error => {
                console.error('Error starting crawling:', error);
                showMessage('Error starting crawling', 'error');
                isCrawling = false;
                crawlingBtn.textContent = 'START CRAWLING';
                crawlingBtn.classList.remove('crawling-active');
                setOtherButtonsState(false);
            });

            // Блокируем select во время crawling
            select.disabled = true;

        } else {
            // Остановка crawling
            fetch(`/api/crawling/stop?siteUrl=${encodeURIComponent(selectedSite)}`, {
                method: 'POST'
            })
            .then(response => response.json())
            .then(data => {
                if (data.result) {
                    showMessage(data.message, 'info');
                } else {
                    showMessage(data.message, 'error');
                }
            })
            .catch(error => {
                console.error('Error stopping crawling:', error);
                showMessage('Error stopping crawling', 'error');
            })
            .finally(() => {
                isCrawling = false;
                crawlingBtn.textContent = 'START CRAWLING';
                crawlingBtn.classList.remove('crawling-active');
                setOtherButtonsState(false);
                select.disabled = false;
            });
        }
}*/

function toggleCrawling() {
    const btn = document.getElementById('crawling-btn');
        const select = document.getElementById('crawling-site-select');

        const selected = select.value;
        const siteParam = selected === 'all' ? 'all' : selected;

        if (!isCrawling) {

            isCrawling = true;
            btn.textContent = 'STOP CRAWLING';
            btn.classList.add('crawling-active');
            setOtherButtonsState(true);
            select.disabled = true;

            fetch(`/api/crawling/start?site=${encodeURIComponent(siteParam)}`, {
                method: 'POST'
            })
                .then(r => r.text())
                .then(t => showMessage(t, 'info'))
                .catch(err => showMessage('Error: ' + err.message, 'error'));

        } else {

            isCrawling = false;
            btn.textContent = 'START CRAWLING';
            btn.classList.remove('crawling-active');
            setOtherButtonsState(false);
            select.disabled = false;

            fetch(`/api/crawling/stop?site=${encodeURIComponent(siteParam)}`, {
                method: 'POST'
            })
                .then(r => r.text())
                .then(t => showMessage(t, 'info'))
                .catch(err => showMessage('Error: ' + err.message, 'error'));
        }
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
    const indexingBtn = document.getElementById('indexing-btn');
        const crawlingBtn = document.getElementById('crawling-btn');
        const addSiteBtn = document.getElementById('add-site-btn');
        const deleteSiteBtn = document.getElementById('delete-site-btn');
        const siteNameInput = document.getElementById('site-name-input');
        const siteUrlInput = document.getElementById('site-url-input');
        const deleteSelect = document.getElementById('delete-site-select');
        const indexingSelect = document.getElementById('indexing-site-select');
        const crawlingSelect = document.getElementById('crawling-site-select');

        if (disabled) {
            // Блокируем все кроме активной кнопки
            if (!isIndexing) indexingBtn.disabled = true;
            if (!isCrawling) crawlingBtn.disabled = true;
            addSiteBtn.disabled = true;
            deleteSiteBtn.disabled = true;
            siteNameInput.disabled = true;
            siteUrlInput.disabled = true;
            deleteSelect.disabled = true;

            // Блокируем неактивные selects
            if (!isIndexing) indexingSelect.disabled = true;
            if (!isCrawling) crawlingSelect.disabled = true;

        } else {
            // Разблокируем все кнопки
            indexingBtn.disabled = false;
            crawlingBtn.disabled = false;
            addSiteBtn.disabled = false;
            deleteSiteBtn.disabled = false;
            siteNameInput.disabled = false;
            siteUrlInput.disabled = false;
            deleteSelect.disabled = false;
            indexingSelect.disabled = false;
            crawlingSelect.disabled = false;

            // Перевалидируем форму добавления сайта
            validateSiteForm();
        }
}

function stopCrawlingProcess() {
    console.log('Crawling process stopped');
    // В реальном приложении здесь будет вызов API для остановки crawling
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

        // Показываем сообщение о начале процесса
        showMessage(`Adding site: ${name}...`, 'info');

        // Вызов API для добавления сайта
        fetch('/api/sites', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                name: name,
                url: url
            })
        })
        .then(response => {
            console.log('Response status:', response.status);
            if (!response.ok) {
                // Если статус не 200-299, пробуем прочитать ошибку
                return response.json().then(errorData => {
                    throw new Error(errorData.message || `HTTP error! status: ${response.status}`);
                }).catch(() => {
                    throw new Error(`HTTP error! status: ${response.status}`);
                });
            }
            return response.json();
        })
        .then(data => {
            console.log('Response data:', data);
            if (data.result) {
                showMessage(data.message, 'success');

                // Очищаем поля после успешного добавления
                document.getElementById('site-name-input').value = '';
                document.getElementById('site-url-input').value = '';
                validateSiteForm();

                // Перезагружаем список сайтов
                setTimeout(() => {
                    loadSitesForManagement();
                    // Если открыта dashboard, обновляем её тоже
                    if (document.getElementById('dashboard').classList.contains('active')) {
                        loadSitesData();
                    }
                }, 1000);
            } else {
                showMessage(data.message, 'error');
            }
        })
        .catch(error => {
            console.error('Error adding site:', error);
            showMessage('Error adding site: ' + error.message, 'error');
        });
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

    showMessage(`Deleting site: ${siteName}...`, 'info');

    // Вызов API для удаления сайта по имени
    fetch(`/api/sites?siteName=${encodeURIComponent(siteName)}`, {
        method: 'DELETE',
        headers: {
            'Content-Type': 'application/json',
        }
    })
    .then(response => {
        console.log('Delete response status:', response.status);

        if (response.status === 400) {
            return response.json().then(data => {
                return { isValidationError: true, ...data };
            });
        }

        if (!response.ok) {
            return response.json().then(errorData => {
                throw new Error(errorData.message || `HTTP error! status: ${response.status}`);
            });
        }
        return response.json();
    })
    .then(data => {
        console.log('Delete response data:', data);

        if (data.isValidationError) {
            showMessage(data.message, 'error');
        } else if (data.result) {
            showMessage(data.message, 'success');

            // Очищаем select после успешного удаления
            select.value = '';

            // Перезагружаем список сайтов
            setTimeout(() => {
                loadSitesForManagement();
                if (document.getElementById('dashboard').classList.contains('active')) {
                    loadSitesData();
                }
            }, 1000);
        } else {
            showMessage(data.message, 'error');
        }
    })
    .catch(error => {
        console.error('Error deleting site:', error);
        showMessage('Error deleting site: ' + error.message, 'error');
    });
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

        // Автоудаление сообщения через 10 секунд для ошибок, 5 для успеха
        /*const timeout = type === 'error' ? 10000 : 5000;
        setTimeout(() => {
            if (currentMessage && currentMessage.parentNode) {
                currentMessage.remove();
                currentMessage = null;
            }
        }, timeout);*/
}