// management.js - логика для MANAGEMENT вкладки

let sitesList = [];
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
                sitesList = data.sites;
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

    // ДОБАВЛЯЕМ "All sites" в indexing select
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
        // indexing select
        const option_indexing = document.createElement('option');
        option_indexing.value = site.url;
        option_indexing.textContent = site.name;
        indexingSelect.appendChild(option_indexing);

        // Для delete select
        const option_delete = document.createElement('option');
        option_delete.value = site.name;
        option_delete.textContent = site.name;
        deleteSelect.appendChild(option_delete);
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
            if (!selectedSite) {
                showMessage('Please select a site for indexing', 'error');
                return;
            }

            isIndexing = true;
            indexingBtn.textContent = 'STOP INDEXING';
            indexingBtn.classList.add('indexing-active');

            // Блокируем другие кнопки
            setOtherButtonsState(true);

            // ВЫЗОВ API ДЛЯ ЗАПУСКА ИНДЕКСАЦИИ
            const apiUrl = selectedSite === 'all'
                ? '/api/startIndexingAll'
                : `/api/startIndexing?site=${encodeURIComponent(selectedSite)}`;

            fetch(apiUrl, {
                method: 'GET'
            })
            .then(response => response.json())
            .then(data => {
                if (data.result) {
                    showMessage(data.message, 'info');
                } else {
                    showMessage(data.message, 'error');
                    // Откатываем состояние если ошибка
                    isIndexing = false;
                    indexingBtn.textContent = 'START INDEXING';
                    indexingBtn.classList.remove('indexing-active');
                    setOtherButtonsState(false);
                }
            })
            .catch(error => {
                console.error('Error starting indexing:', error);
                showMessage('Error starting indexing', 'error');
                isIndexing = false;
                indexingBtn.textContent = 'START INDEXING';
                indexingBtn.classList.remove('indexing-active');
                setOtherButtonsState(false);
            });

            // Блокируем select во время индексации
            select.disabled = true;

        } else {
            // Остановка индексации
            fetch('/api/stopIndexing', {
                method: 'GET'
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
                console.error('Error stopping indexing:', error);
                showMessage('Error stopping indexing', 'error');
            })
            .finally(() => {
                isIndexing = false;
                indexingBtn.textContent = 'START INDEXING';
                indexingBtn.classList.remove('indexing-active');
                setOtherButtonsState(false);
                select.disabled = false;
            });
        }
}

// Функция для блокировки/разблокировки других кнопок
function setOtherButtonsState(disabled) {
    const indexingBtn = document.getElementById('indexing-btn');
        const addSiteBtn = document.getElementById('add-site-btn');
        const deleteSiteBtn = document.getElementById('delete-site-btn');
        const siteNameInput = document.getElementById('site-name-input');
        const siteUrlInput = document.getElementById('site-url-input');
        const deleteSelect = document.getElementById('delete-site-select');
        const indexingSelect = document.getElementById('indexing-site-select');

        if (disabled) {
            // Блокируем все кроме активной кнопки
            if (!isIndexing) indexingBtn.disabled = true;
            addSiteBtn.disabled = true;
            deleteSiteBtn.disabled = true;
            siteNameInput.disabled = true;
            siteUrlInput.disabled = true;
            deleteSelect.disabled = true;

            // Блокируем неактивные selects
            if (!isIndexing) indexingSelect.disabled = true;

        } else {
            // Разблокируем все кнопки
            indexingBtn.disabled = false;
            addSiteBtn.disabled = false;
            deleteSiteBtn.disabled = false;
            siteNameInput.disabled = false;
            siteUrlInput.disabled = false;
            deleteSelect.disabled = false;
            indexingSelect.disabled = false;

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

function addSite() {
    const nameInput = document.getElementById('site-name-input');
        const urlInput = document.getElementById('site-url-input');

        if (!nameInput || !urlInput) {
            showMessage('Form elements not found', 'error');
            return;
        }

        const name = nameInput.value.trim();
        const url = urlInput.value.trim();

        if (!name || !url) {
            showMessage('Please fill in all fields', 'error');
            return;
        }

        // Показываем сообщение о начале процесса
        showMessage(`Adding site: ${name}...`, 'info');

        console.log('Sending add site request:', { name, url });

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
            return response.json().then(data => {
                return {
                    status: response.status,
                    data: data
                };
            });
        })
        .then(({ status, data }) => {
            console.log('Response data:', data);

            if (status === 200 && data.result) {
                showMessage(data.message || 'Site added successfully', 'success');

                // Очищаем поля после успешного добавления
                nameInput.value = '';
                urlInput.value = '';
                validateSiteForm();

                // Перезагружаем список сайтов
                setTimeout(() => {
                    loadSitesForManagement();
                }, 1000);
            } else {
                showMessage(data.message || 'Error adding site', 'error');
            }
        })
        .catch(error => {
            console.error('Error adding site:', error);
            showMessage('Error adding site: ' + error.message, 'error');
        });
}

function deleteSite() {
    const select = document.getElementById('delete-site-select');

        if (!select) {
            showMessage('Delete select not found', 'error');
            return;
        }

        const selectedSite = select.value;

        if (!selectedSite) {
            showMessage('Please select a site to delete', 'error');
            return;
        }

        // Используем значение (site name) для удаления
        const siteName = selectedSite;
        showMessage(`Deleting site: ${siteName}...`, 'info');

        console.log('Sending delete request for site:', siteName);

        // Вызов API для удаления сайта по имени
        fetch(`/api/sites?siteName=${encodeURIComponent(siteName)}`, {
            method: 'DELETE'
        })
        .then(response => {
            console.log('Delete response status:', response.status);
            return response.json().then(data => {
                return {
                    status: response.status,
                    data: data
                };
            });
        })
        .then(({ status, data }) => {
            console.log('Delete response data:', data);

            if (status === 200 && data.result) {
                showMessage(data.message || 'Site deleted successfully', 'success');

                // Очищаем select после успешного удаления
                select.value = '';

                // Перезагружаем список сайтов
                setTimeout(() => {
                    loadSitesForManagement();
                }, 1000);
            } else {
                showMessage(data.message || 'Error deleting site', 'error');
            }
        })
        .catch(error => {
            console.error('Error deleting site:', error);
            showMessage('Error deleting site: ' + error.message, 'error');
        });
}

function validateSiteForm() {
    const nameInput = document.getElementById('site-name-input');
    const urlInput = document.getElementById('site-url-input');
    const button = document.getElementById('add-site-btn');

    if (!nameInput || !urlInput || !button) {
        return;
    }

    const name = nameInput.value.trim();
    const url = urlInput.value.trim();

    const isValid = name && url && isValidUrl(url);
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