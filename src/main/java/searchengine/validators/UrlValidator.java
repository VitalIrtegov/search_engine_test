package searchengine.validators;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

public class UrlValidator {
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$"
    );
    private static final Pattern PROTOCOL_PATTERN = Pattern.compile("^(https)://.*$");
    private static final Pattern DOUBLE_SLASH_PATTERN = Pattern.compile("//{2,}");

    /** Валидирует корневой URL домена для добавления в поисковый движок
     *  Разрешает только домены первого уровня без путей и параметров
     *  @param urlString URL для проверки
     *  @return null если валиден, сообщение об ошибке если нет */

    public static String validateUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return "URL cannot be empty";
        }

        // Проверка на двойные слеши
        if (DOUBLE_SLASH_PATTERN.matcher(urlString).find()) {
            return "URL contains consecutive slashes (//)";
        }

        // Проверка протокола - ТОЛЬКО HTTPS
        if (!PROTOCOL_PATTERN.matcher(urlString).matches()) {
            return "URL must start with https://";
        }

        try {
            URL url = new URL(urlString);
            String host = url.getHost();

            // Проверка хоста
            if (host == null || host.isEmpty()) {
                return "Invalid host in URL";
            }

            // Проверка домена первого уровня
            if (!isValidDomain(host)) {
                return "Invalid domain format. Must be a valid top-level domain";
            }

            // Проверяем что это корневой домен без путей
            String path = url.getPath();
            if (path != null && !path.isEmpty() && !path.equals("/")) {
                return "URL must be root domain only (without paths). Example: https://example.com";
            }

            // Проверяем что нет query параметров
            String query = url.getQuery();
            if (query != null && !query.isEmpty()) {
                return "URL must not contain query parameters. Example: https://example.com";
            }

            // Проверяем что нет порта (если только не стандартный для HTTPS)
            int port = url.getPort();
            if (port != -1 && port != 443) {
                return "URL must not contain custom port. Use standard HTTPS port (443)";
            }

            // Дополнительные проверки пути
            if (path != null && !path.isEmpty()) {
                if (path.contains("//")) {
                    return "URL path contains consecutive slashes";
                } else if (path.endsWith("/")) {
                    return "URL should not end with slash";
                }
            }

        } catch (MalformedURLException e) {
            return "Malformed URL: " + e.getMessage();
        }

        return null; // URL валиден
    }

    /** Проверяет валидность доменного имени **/
    private static boolean isValidDomain(String domain) {
        return DOMAIN_PATTERN.matcher(domain).matches();
    }

    /** Нормализует URL: приводит к стандартному формату корневого домена
     *  Приводит к нижнему регистру, убирает trailing slash, убирает порт если стандартный  */
    public static String normalizeUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            String protocol = "https"; // Всегда HTTPS
            String host = url.getHost().toLowerCase().trim();

            // Для корневого домена игнорируем путь и параметры
            StringBuilder normalized = new StringBuilder();
            normalized.append(protocol).append("://").append(host);

            return normalized.toString();

        } catch (MalformedURLException e) {
            return urlString; // Возвращаем оригинал при ошибке
        }
    }

    /** дополнительный метод для проверки что URL является корневым доменом */
    public static boolean isRootDomain(String urlString) {
        try {
            URL url = new URL(urlString);
            String path = url.getPath();
            String query = url.getQuery();

            // Корневой домен: (путь пустой или "/") и нет query параметров
            return (path == null || path.isEmpty() || path.equals("/"))
                    && (query == null || query.isEmpty());
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /** Извлекает корневой домен из любого URL */
    public static String extractRootDomain(String urlString) {
        try {
            URL url = new URL(urlString);
            return "https://" + url.getHost().toLowerCase();
        } catch (MalformedURLException e) {
            return urlString;
        }
    }
}
