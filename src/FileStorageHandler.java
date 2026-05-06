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
 * HTTP request handler for the REST file storage API.
 * Supports GET, PUT, HEAD, and DELETE methods.
 * PUT with X-Copy-From header performs a file copy operation.
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
            // Validate path — reject path traversal attempts
            if (path.contains("..")) {
                sendErrorResponse(exchange, HttpURLConnection.HTTP_BAD_REQUEST, "Invalid path: path traversal not allowed");
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
                            "Method not allowed: " + method);
                    break;
            }
        } catch (IllegalArgumentException e) {
            sendErrorResponse(exchange, HttpURLConnection.HTTP_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(exchange, HttpURLConnection.HTTP_INTERNAL_ERROR, "Internal server error");
        } finally {
            exchange.close();
        }
    }

    /**
     * GET — download a file, list a directory, or serve the web UI.
     * If the path is a directory and client accepts text/html (browser) → serve web UI.
     * If the path is a directory and client accepts application/json → serve JSON listing.
     * Otherwise, return the file content as a binary stream.
     */
    private void handleGet(HttpExchange exchange, String path) throws IOException {
        // Check if client is a browser requesting HTML
        String accept = exchange.getRequestHeaders().getFirst("Accept");
        boolean wantsHtml = accept != null && accept.contains("text/html");

        if (!storageService.exists(path)) {
            if (wantsHtml) {
                // For browser: serve the web UI even for non-existent paths
                // (the JavaScript will handle showing "not found")
                serveWebUI(exchange);
                return;
            }
            sendErrorResponse(exchange, HttpURLConnection.HTTP_NOT_FOUND, "Not found: " + path);
            return;
        }

        if (storageService.isDirectory(path)) {
            if (wantsHtml) {
                // Browser requesting a directory → serve the web UI HTML page
                serveWebUI(exchange);
            } else {
                // API client (curl, JavaScript fetch) → serve JSON listing
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
            // Return file content
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
     * PUT — upload a file (body = content) or copy a file (X-Copy-From header).
     * Returns 201 if a new file was created, 204 if an existing file was overwritten.
     */
    private void handlePut(HttpExchange exchange, String path) throws IOException {
        String copyFromHeader = exchange.getRequestHeaders().getFirst("X-Copy-From");

        boolean created;
        if (copyFromHeader != null && !copyFromHeader.isEmpty()) {
            // Copy operation
            String sourcePath = copyFromHeader;
            // Validate source path
            if (sourcePath.contains("..")) {
                sendErrorResponse(exchange, HttpURLConnection.HTTP_BAD_REQUEST,
                        "Invalid source path: path traversal not allowed");
                return;
            }
            if (!storageService.fileExists(sourcePath)) {
                sendErrorResponse(exchange, HttpURLConnection.HTTP_NOT_FOUND,
                        "Source file not found: " + sourcePath);
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
            // Upload operation — read body as file content
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
     * HEAD — return file metadata as HTTP headers without the body.
     * Sets Content-Length and Last-Modified headers.
     */
    private void handleHead(HttpExchange exchange, String path) throws IOException {
        if (!storageService.exists(path)) {
            sendErrorResponse(exchange, HttpURLConnection.HTTP_NOT_FOUND, "Not found: " + path);
            return;
        }

        if (storageService.isDirectory(path)) {
            // For directories, return basic info
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
     * DELETE — remove a file or directory (recursively).
     * Returns 204 on success, 404 if not found.
     */
    private void handleDelete(HttpExchange exchange, String path) throws IOException {
        if (!storageService.exists(path)) {
            sendErrorResponse(exchange, HttpURLConnection.HTTP_NOT_FOUND, "Not found: " + path);
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
     * Reads the entire request body into a byte array.
     */
    private byte[] readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return is.readAllBytes();
        }
    }

    /**
     * Sends an error response with the given status code and message.
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
     * Extracts the file name from a path (the last segment).
     */
    private String getFileName(String path) {
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Formats a timestamp (epoch millis) as an RFC 1123 date string.
     */
    private String formatDate(long epochMillis) {
        return RFC_1123_FORMATTER.format(java.time.Instant.ofEpochMilli(epochMillis));
    }

    /**
     * Serves the web UI HTML page to the browser.
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
