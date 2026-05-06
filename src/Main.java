import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Entry point for the Remote File Storage REST API server.
 * Starts an HTTP server on the specified port (default: 8080) and registers
 * the FileStorageHandler to serve all requests.
 *
 * Usage: java Main [port] [storage-path]
 *   port          — HTTP port to listen on (default: 8080)
 *   storage-path  — local directory for file storage (default: ./storage)
 */
public class Main {

    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_STORAGE_PATH = "./storage";

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        String storagePath = DEFAULT_STORAGE_PATH;

        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.err.println("Usage: java Main [port] [storage-path]");
                System.exit(1);
            }
        }

        if (args.length >= 2) {
            storagePath = args[1];
        }

        try {
            // Initialize storage service
            StorageService storageService = new StorageService(storagePath);
            System.out.println("Storage root: " + storagePath);

            // Create HTTP server
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new FileStorageHandler(storageService));
            server.setExecutor(null); // default executor

            // Register shutdown hook to release port on Ctrl+C
            Thread shutdownHook = new Thread(() -> {
                System.out.println("\nShutting down server...");
                server.stop(0);
                System.out.println("Server stopped. Port released.");
            });
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            // Start server
            server.start();
            System.out.println("File Storage REST API server started on port " + port);
            System.out.println("Access via: http://localhost:" + port + "/");
            System.out.println();
            System.out.println("Supported operations:");
            System.out.println("  GET    /path/to/file.txt          — Download file");
            System.out.println("  GET    /path/to/dir/               — List directory (JSON)");
            System.out.println("  PUT    /path/to/file.txt          — Upload file");
            System.out.println("  PUT    /path/to/file.txt + X-Copy-From: /src — Copy file");
            System.out.println("  HEAD   /path/to/file.txt          — Get file info");
            System.out.println("  DELETE /path/to/file.txt          — Delete file/directory");
            System.out.println();
            System.out.println("Press Ctrl+C to stop the server.");

        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
