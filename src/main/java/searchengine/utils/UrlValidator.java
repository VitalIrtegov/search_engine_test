package searchengine.utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

public class UrlValidator {
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$"
    );

    private static final Pattern PROTOCOL_PATTERN = Pattern.compile("^(https)://.*$");
    private static final Pattern DOUBLE_SLASH_PATTERN = Pattern.compile("//{2,}");

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

            // Проверка хоста
            String host = url.getHost();
            if (host == null || host.isEmpty()) {
                return "Invalid host in URL";
            }

            // Проверка домена первого уровня
            if (!isValidDomain(host)) {
                return "Invalid domain format. Must be a valid top-level domain";
            }

            // проверка именно домены первого уровня без путей
            if (url.getPath() != null && !url.getPath().isEmpty() && !url.getPath().equals("/")) {
                return "URL should be only domain (first level), without paths";
            }

            // Дополнительные проверки пути
            String path = url.getPath();
            if (path != null && !path.isEmpty()) {
                if (path.contains("//")) {
                    return "URL path contains consecutive slashes";
                }
                if (path.endsWith("/")) {
                    return "URL should not end with slash";
                }
            }

        } catch (MalformedURLException e) {
            return "Malformed URL: " + e.getMessage();
        }

        return null; // URL валиден
    }

    private static boolean isValidDomain(String domain) {
        return DOMAIN_PATTERN.matcher(domain).matches();
    }

    public static String normalizeUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            String protocol = url.getProtocol().toLowerCase();
            String host = url.getHost().toLowerCase();
            int port = url.getPort();
            String path = url.getPath();
            String query = url.getQuery();

            // Строим нормализованный URL
            StringBuilder normalized = new StringBuilder();
            normalized.append(protocol).append("://").append(host);

            // Добавляем порт только если он не стандартный
            if (port != -1 && !((protocol.equals("http") && port == 80) ||
                    (protocol.equals("https") && port == 443))) {
                normalized.append(":").append(port);
            }

            // Добавляем путь (убираем trailing slash)
            if (path != null && !path.isEmpty()) {
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }
                normalized.append(path);
            }

            // Добавляем query параметры
            if (query != null && !query.isEmpty()) {
                normalized.append("?").append(query);
            }

            return normalized.toString();

        } catch (MalformedURLException e) {
            return urlString;
        }
    }
}
