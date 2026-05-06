import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service layer for filesystem operations on the virtual storage root.
 * All paths are resolved relative to the configured storage root directory.
 */
public class StorageService {

    private final Path storageRoot;

    /**
     * Metadata for a single file.
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
     * Entry within a directory listing — can be a file or a subdirectory.
     */
    public static class DirectoryEntry {
        private final String name;
        private final String type; // "file" or "directory"
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
     * Listing of a directory's contents.
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
     * Resolves a virtual path to a real filesystem path within the storage root.
     * Throws IllegalArgumentException if the resolved path escapes the storage root.
     */
    private Path resolvePath(String virtualPath) {
        String cleaned = virtualPath;
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        Path resolved = storageRoot.resolve(cleaned).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Path traversal detected: " + virtualPath);
        }
        return resolved;
    }

    /**
     * Checks if the virtual path points to an existing file.
     */
    public boolean fileExists(String virtualPath) {
        Path path = resolvePath(virtualPath);
        return Files.exists(path) && Files.isRegularFile(path);
    }

    /**
     * Checks if the virtual path points to an existing directory.
     */
    public boolean directoryExists(String virtualPath) {
        Path path = resolvePath(virtualPath);
        return Files.exists(path) && Files.isDirectory(path);
    }

    /**
     * Checks if the virtual path exists (file or directory).
     */
    public boolean exists(String virtualPath) {
        Path path = resolvePath(virtualPath);
        return Files.exists(path);
    }

    /**
     * Checks if the virtual path is a directory.
     */
    public boolean isDirectory(String virtualPath) {
        Path path = resolvePath(virtualPath);
        return Files.isDirectory(path);
    }

    /**
     * Reads the entire content of a file and returns it as a byte array.
     */
    public byte[] getFile(String virtualPath) throws IOException {
        Path path = resolvePath(virtualPath);
        return Files.readAllBytes(path);
    }

    /**
     * Writes content to a file. Creates parent directories if needed.
     *
     * @return true if a new file was created, false if an existing file was overwritten.
     */
    public boolean putFile(String virtualPath, byte[] content) throws IOException {
        Path path = resolvePath(virtualPath);
        // Prevent writing over a directory
        if (Files.isDirectory(path)) {
            throw new IOException("Cannot write file: path is a directory");
        }
        Files.createDirectories(path.getParent());
        boolean existed = Files.exists(path);
        Files.write(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return !existed;
    }

    /**
     * Copies a file from source to destination. Creates parent directories if needed.
     *
     * @return true if a new file was created at destination, false if overwritten.
     */
    public boolean copyFile(String sourceVirtualPath, String destVirtualPath) throws IOException {
        Path source = resolvePath(sourceVirtualPath);
        Path dest = resolvePath(destVirtualPath);

        if (!Files.exists(source) || !Files.isRegularFile(source)) {
            throw new NoSuchFileException("Source file not found: " + sourceVirtualPath);
        }
        if (Files.isDirectory(dest)) {
            throw new IOException("Cannot copy: destination path is a directory");
        }

        Files.createDirectories(dest.getParent());
        boolean existed = Files.exists(dest);
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        return !existed;
    }

    /**
     * Deletes a file or directory (recursively).
     */
    public void delete(String virtualPath) throws IOException {
        Path path = resolvePath(virtualPath);
        if (!Files.exists(path)) {
            throw new NoSuchFileException("Not found: " + virtualPath);
        }
        if (Files.isDirectory(path)) {
            deleteRecursively(path);
        } else {
            Files.delete(path);
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
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
     * Returns file metadata (size and last modified timestamp).
     */
    public FileInfo getFileInfo(String virtualPath) throws IOException {
        Path path = resolvePath(virtualPath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new NoSuchFileException("File not found: " + virtualPath);
        }
        long size = Files.size(path);
        long lastModified = Files.getLastModifiedTime(path).toMillis();
        return new FileInfo(size, lastModified);
    }

    /**
     * Lists the contents of a directory.
     */
    public DirectoryListing listDirectory(String virtualPath) throws IOException {
        Path path = resolvePath(virtualPath);
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new NoSuchFileException("Directory not found: " + virtualPath);
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

        // Sort: directories first, then files, alphabetically within each group
        entries.sort((a, b) -> {
            if (!a.getType().equals(b.getType())) {
                return a.getType().equals("directory") ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });

        return new DirectoryListing(virtualPath, entries);
    }

    /**
     * Converts the directory listing to a JSON string.
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
     * Escapes special characters for JSON string values.
     */
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
    }
}
