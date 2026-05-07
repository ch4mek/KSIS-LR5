import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Точка входа для REST API сервера удалённого файлового хранилища.
 * Запускает HTTP-сервер на указанном порту и регистрирует обработчик запросов.
 *
 * Использование: java Main [порт] [путь_к_хранилищу]
 *   порт          — HTTP-порт (по умолчанию: 8080)
 *   путь_к_хранилищу  — локальная директория для хранения файлов (по умолчанию: ./storage)
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
                System.err.println("Некорректный номер порта: " + args[0]);
                System.err.println("Использование: java Main [порт] [путь_к_хранилищу]");
                System.exit(1);
            }
        }

        if (args.length >= 2) {
            storagePath = args[1];
        }

        try {
            // Инициализация сервиса хранилища
            StorageService storageService = new StorageService(storagePath);
            System.out.println("Корневой каталог хранилища: " + storagePath);

            // Создание HTTP-сервера
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new FileStorageHandler(storageService));
            server.setExecutor(null); // исполнитель по умолчанию

            // Обработчик завершения для освобождения порта при Ctrl+C
            Thread shutdownHook = new Thread(() -> {
                System.out.println("\nОстановка сервера...");
                server.stop(0);
                System.out.println("Сервер остановлен. Порт освобождён.");
            });
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            // Запуск сервера
            server.start();
            System.out.println("REST API сервер файлового хранилища запущен на порту " + port);
            System.out.println("Доступ: http://localhost:" + port + "/");
            System.out.println();
            System.out.println("Поддерживаемые операции:");
            System.out.println("  GET    /path/to/file.txt          — Скачать файл");
            System.out.println("  GET    /path/to/dir/               — Список каталога (JSON)");
            System.out.println("  PUT    /path/to/file.txt          — Загрузить файл");
            System.out.println("  PUT    /path/to/file.txt + X-Copy-From: /src — Копировать файл");
            System.out.println("  HEAD   /path/to/file.txt          — Информация о файле");
            System.out.println("  DELETE /path/to/file.txt          — Удалить файл/каталог");
            System.out.println();
            System.out.println("Нажмите Ctrl+C для остановки сервера.");

        } catch (IOException e) {
            System.err.println("Ошибка запуска сервера: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
