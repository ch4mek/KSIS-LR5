import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Сервисный слой для файловых операций в корневом каталоге хранилища.
 * Все пути разрешаются относительно настроенного корневого каталога хранилища.
 */
public class StorageService {

    private final Path storageRoot;

    /**
     * Метаданные файла.
     */
    public static class FileInfo {
        private final long size;
        private final long lastModified;

        public FileInfo(long size, long lastModified) {
            this.size = size;
            this.lastModified = lastModified;
        }

        public long getSize() {
            return size;
        }

        public long getLastModified() {
            return lastModified;
        }
    }

    /**
     * Запись в списке каталога — файл или подкаталог.
     */
    public static class DirectoryEntry {
        private final String name;
        private final String type; // "file" или "directory"
        private final long size;
        private final long lastModified;

        public DirectoryEntry(String name, String type, long size, long lastModified) {
            this.name = name;
            this.type = type;
            this.size = size;
            this.lastModified = lastModified;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public long getSize() {
            return size;
        }

        public long getLastModified() {
            return lastModified;
        }
    }

    /**
     * Список содержимого каталога.
     */
    public static class DirectoryListing {
        private final String path;
        private final List<DirectoryEntry> entries;

        public DirectoryListing(String path, List<DirectoryEntry> entries) {
            this.path = path;
            this.entries = entries;
        }

        public String getPath() {
            return path;
        }

        public List<DirectoryEntry> getEntries() {
            return entries;
        }
    }

    public StorageService(String storageRootPath) throws IOException {
        this.storageRoot = Paths.get(storageRootPath).toAbsolutePath().normalize();
        if (!Files.exists(this.storageRoot)) {
            Files.createDirectories(this.storageRoot);
        }
    }

    /**
     * Преобразует виртуальный путь в реальный путь файловой системы внутри корня хранилища.
     * Выбрасывает IllegalArgumentException если результирующий путь выходит за пределы хранилища.
     */
    private Path resolvePath(String virtualPath) {
        String cleaned = virtualPath;
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        Path resolved = storageRoot.resolve(cleaned).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Обнаружен обход каталогов: " + virtualPath);
        }
        return resolved;
    }

    /**
     * Проверяет, указывает ли виртуальный путь на существующий файл.
     */
    public boolean fileExists(String virtualPath) {
        Path path = resolvePath(virtualPath);
        return Files.exists(path) && Files.isRegularFile(path);
    }

    /**
     * Проверяет, указывает ли виртуальный путь на существующий каталог.
     */
    public boolean directoryExists(String virtualPath) {
        Path path = resolvePath(virtualPath);
        return Files.exists(path) && Files.isDirectory(path);
    }

    /**
     * Проверяет, существует ли виртуальный путь (файл или каталог).
     */
    public boolean exists(String virtualPath) {
        Path path = resolvePath(virtualPath);
        return Files.exists(path);
    }

    /**
     * Проверяет, является ли виртуальный путь каталогом.
     */
    public boolean isDirectory(String virtualPath) {
        Path path = resolvePath(virtualPath);
        return Files.isDirectory(path);
    }

    /**
     * Читает всё содержимое файла и возвращает как массив байтов.
     */
    public byte[] getFile(String virtualPath) throws IOException {
        Path path = resolvePath(virtualPath);
        return Files.readAllBytes(path);
    }

    /**
     * Записывает содержимое в файл. Создаёт родительские каталоги при необходимости.
     *
     * @return true если создан новый файл, false если перезаписан существующий.
     */
    public boolean putFile(String virtualPath, byte[] content) throws IOException {
        Path path = resolvePath(virtualPath);
        // Предотвращаем запись поверх каталога
        if (Files.isDirectory(path)) {
            throw new IOException("Невозможно записать файл: путь является каталогом");
        }
        Files.createDirectories(path.getParent());
        boolean existed = Files.exists(path);
        Files.write(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return !existed;
    }

    /**
     * Копирует файл из источника в назначение. Создаёт родительские каталоги при необходимости.
     *
     * @return true если создан новый файл в назначении, false если перезаписан.
     */
    public boolean copyFile(String sourceVirtualPath, String destVirtualPath) throws IOException {
        Path source = resolvePath(sourceVirtualPath);
        Path dest = resolvePath(destVirtualPath);

        if (!Files.exists(source) || !Files.isRegularFile(source)) {
            throw new NoSuchFileException("Исходный файл не найден: " + sourceVirtualPath);
        }
        if (Files.isDirectory(dest)) {
            throw new IOException("Невозможно копировать: путь назначения является каталогом");
        }

        Files.createDirectories(dest.getParent());
        boolean existed = Files.exists(dest);
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        return !existed;
    }

    /**
     * Удаляет файл или каталог (рекурсивно).
     */
    public void delete(String virtualPath) throws IOException {
        Path path = resolvePath(virtualPath);
        if (!Files.exists(path)) {
            throw new NoSuchFileException("Не найдено: " + virtualPath);
        }
        if (Files.isDirectory(path)) {
            deleteRecursively(path);
        } else {
            Files.delete(path);
        }
    }

    /**
     * Рекурсивно удаляет каталог и всё его содержимое.
     */
    private void deleteRecursively(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Возвращает метаданные файла (размер и временную метку последнего изменения).
     */
    public FileInfo getFileInfo(String virtualPath) throws IOException {
        Path path = resolvePath(virtualPath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new NoSuchFileException("Файл не найден: " + virtualPath);
        }
        long size = Files.size(path);
        long lastModified = Files.getLastModifiedTime(path).toMillis();
        return new FileInfo(size, lastModified);
    }

    /**
     * Возвращает список содержимого каталога.
     */
    public DirectoryListing listDirectory(String virtualPath) throws IOException {
        Path path = resolvePath(virtualPath);
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new NoSuchFileException("Каталог не найден: " + virtualPath);
        }

        List<DirectoryEntry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(path)) {
            stream.forEach(child -> {
                String name = child.getFileName().toString();
                if (Files.isDirectory(child)) {
                    entries.add(new DirectoryEntry(name, "directory", 0, 0));
                } else {
                    try {
                        long size = Files.size(child);
                        long lastModified = Files.getLastModifiedTime(child).toMillis();
                        entries.add(new DirectoryEntry(name, "file", size, lastModified));
                    } catch (IOException e) {
                        entries.add(new DirectoryEntry(name, "file", -1, 0));
                    }
                }
            });
        }

        // Сортировка: сначала каталоги, потом файлы, по алфавиту в каждой группе
        entries.sort((a, b) -> {
            if (!a.getType().equals(b.getType())) {
                return a.getType().equals("directory") ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });

        return new DirectoryListing(virtualPath, entries);
    }

    /**
     * Преобразует список каталога в строку JSON.
     */
    public String directoryListingToJson(DirectoryListing listing) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"path\": \"").append(escapeJson(listing.getPath())).append("\",\n");
        sb.append("  \"files\": [");

        List<DirectoryEntry> entries = listing.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            DirectoryEntry entry = entries.get(i);
            sb.append("\n    {\n");
            sb.append("      \"name\": \"").append(escapeJson(entry.getName())).append("\",\n");
            sb.append("      \"type\": \"").append(entry.getType()).append("\"");
            if (entry.getType().equals("file")) {
                sb.append(",\n");
                sb.append("      \"size\": ").append(entry.getSize()).append(",\n");
                sb.append("      \"lastModified\": \"").append(Instant.ofEpochMilli(entry.getLastModified()).toString()).append("\"");
            }
            sb.append("\n    }");
            if (i < entries.size() - 1) {
                sb.append(",");
            }
        }

        if (!entries.isEmpty()) {
            sb.append("\n  ");
        }
        sb.append("]\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Экранирует специальные символы для строковых значений JSON.
     */
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
    }
}
