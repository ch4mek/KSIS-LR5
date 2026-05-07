import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.file.NoSuchFileException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Обработчик HTTP-запросов для REST API файлового хранилища.
 * Поддерживает методы GET, PUT, HEAD и DELETE.
 * PUT с заголовком X-Copy-From выполняет копирование файла.
 */
public class FileStorageHandler implements HttpHandler {

    private final StorageService storageService;
    private static final DateTimeFormatter RFC_1123_FORMATTER =
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));

    public FileStorageHandler(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            // Проверка пути — отклоняем попытки обхода каталогов
            if (path.contains("..")) {
                sendErrorResponse(exchange, HttpURLConnection.HTTP_BAD_REQUEST, "Некорректный путь: обход каталогов запрещён");
                return;
            }

            switch (method.toUpperCase()) {
                case "GET":
                    handleGet(exchange, path);
                    break;
                case "PUT":
                    handlePut(exchange, path);
                    break;
                case "HEAD":
                    handleHead(exchange, path);
                    break;
                case "DELETE":
                    handleDelete(exchange, path);
                    break;
                default:
                    sendErrorResponse(exchange, HttpURLConnection.HTTP_BAD_METHOD,
                            "Метод не поддерживается: " + method);
                    break;
            }
        } catch (IllegalArgumentException e) {
            sendErrorResponse(exchange, HttpURLConnection.HTTP_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, HttpURLConnection.HTTP_INTERNAL_ERROR, "Внутренняя ошибка сервера");
        } finally {
            exchange.close();
        }
    }

    /**
     * GET — скачивание файла, список каталога или веб-интерфейс.
     * Если путь — каталог и клиент принимает text/html (браузер) → отдаём веб-интерфейс.
     * Если путь — каталог и клиент принимает application/json → отдаём JSON-список.
     * Иначе возвращаем содержимое файла как бинарный поток.
     */
    private void handleGet(HttpExchange exchange, String path) throws IOException {
        // Проверяем, является ли клиент браузером
        String accept = exchange.getRequestHeaders().getFirst("Accept");
        boolean wantsHtml = accept != null && accept.contains("text/html");

        if (!storageService.exists(path)) {
            if (wantsHtml) {
                // Для браузера: отдаём веб-интерфейс даже для несуществующих путей
                serveWebUI(exchange);
                return;
            }
            sendErrorResponse(exchange, HttpURLConnection.HTTP_NOT_FOUND, "Не найдено: " + path);
            return;
        }

        if (storageService.isDirectory(path)) {
            if (wantsHtml) {
                // Браузер запрашивает каталог → отдаём HTML-страницу
                serveWebUI(exchange);
            } else {
                // API-клиент (curl, JavaScript fetch) → отдаём JSON-список
                StorageService.DirectoryListing listing = storageService.listDirectory(path);
                String json = storageService.directoryListingToJson(listing);
                byte[] jsonBytes = json.getBytes("UTF-8");

                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(jsonBytes.length));
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, jsonBytes.length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(jsonBytes);
                }
            }
        } else {
            // Возвращаем содержимое файла
            byte[] content = storageService.getFile(path);
            StorageService.FileInfo info = storageService.getFileInfo(path);

            String fileName = getFileName(path);
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(content.length));
            exchange.getResponseHeaders().set("Last-Modified", formatDate(info.getLastModified()));
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"" + fileName + "\"");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, content.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        }
    }

    /**
     * PUT — загрузка файла (тело = содержимое) или копирование файла (заголовок X-Copy-From).
     * Возвращает 201 если создан новый файл, 204 если перезаписан существующий.
     */
    private void handlePut(HttpExchange exchange, String path) throws IOException {
        String copyFromHeader = exchange.getRequestHeaders().getFirst("X-Copy-From");

        boolean created;
        if (copyFromHeader != null && !copyFromHeader.isEmpty()) {
            // Операция копирования
            String sourcePath = copyFromHeader;
            // Проверка исходного пути
            if (sourcePath.contains("..")) {
                sendErrorResponse(exchange, HttpURLConnection.HTTP_BAD_REQUEST,
                        "Некорректный исходный путь: обход каталогов запрещён");
                return;
            }
            if (!storageService.fileExists(sourcePath)) {
                sendErrorResponse(exchange, HttpURLConnection.HTTP_NOT_FOUND,
                        "Исходный файл не найден: " + sourcePath);
                return;
            }
            try {
                created = storageService.copyFile(sourcePath, path);
            } catch (NoSuchFileException e) {
                sendErrorResponse(exchange, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
                return;
            } catch (IOException e) {
                sendErrorResponse(exchange, HttpURLConnection.HTTP_BAD_REQUEST, e.getMessage());
                return;
            }
        } else {
            // Операция загрузки — читаем тело как содержимое файла
            byte[] content = readRequestBody(exchange);
            if (content == null) {
                content = new byte[0];
            }
            try {
                created = storageService.putFile(path, content);
            } catch (IOException e) {
                sendErrorResponse(exchange, HttpURLConnection.HTTP_BAD_REQUEST, e.getMessage());
                return;
            }
        }

        int statusCode = created ? HttpURLConnection.HTTP_CREATED : HttpURLConnection.HTTP_NO_CONTENT;
        exchange.sendResponseHeaders(statusCode, -1);
    }

    /**
     * HEAD — возвращает метаданные файла как HTTP-заголовки без тела.
     * Устанавливает заголовки Content-Length и Last-Modified.
     */
    private void handleHead(HttpExchange exchange, String path) throws IOException {
        if (!storageService.exists(path)) {
            sendErrorResponse(exchange, HttpURLConnection.HTTP_NOT_FOUND, "Не найдено: " + path);
            return;
        }

        if (storageService.isDirectory(path)) {
            // Для каталогов возвращаем базовую информацию
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
            return;
        }

        StorageService.FileInfo info = storageService.getFileInfo(path);
        String fileName = getFileName(path);

        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(info.getSize()));
        exchange.getResponseHeaders().set("Last-Modified", formatDate(info.getLastModified()));
        exchange.getResponseHeaders().set("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"");
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
    }

    /**
     * DELETE — удаление файла или каталога (рекурсивно).
     * Возвращает 204 при успехе, 404 если не найден.
     */
    private void handleDelete(HttpExchange exchange, String path) throws IOException {
        if (!storageService.exists(path)) {
            sendErrorResponse(exchange, HttpURLConnection.HTTP_NOT_FOUND, "Не найдено: " + path);
            return;
        }

        try {
            storageService.delete(path);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_NO_CONTENT, -1);
        } catch (NoSuchFileException e) {
            sendErrorResponse(exchange, HttpURLConnection.HTTP_NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Читает всё тело запроса в массив байтов.
     */
    private byte[] readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return is.readAllBytes();
        }
    }

    /**
     * Отправляет ответ об ошибке с указанным кодом состояния и сообщением.
     */
    private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] responseBytes = message.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(responseBytes.length));
            exchange.sendResponseHeaders(statusCode, -1);
        } else {
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    /**
     * Извлекает имя файла из пути (последний сегмент).
     */
    private String getFileName(String path) {
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Форматирует временную метку (epoch millis) как строку даты RFC 1123.
     */
    private String formatDate(long epochMillis) {
        return RFC_1123_FORMATTER.format(java.time.Instant.ofEpochMilli(epochMillis));
    }

    /**
     * Отдаёт HTML-страницу веб-интерфейса браузеру.
     */
    private void serveWebUI(HttpExchange exchange) throws IOException {
        byte[] htmlBytes = WebUI.HTML.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(htmlBytes.length));
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, htmlBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(htmlBytes);
        }
    }
}
