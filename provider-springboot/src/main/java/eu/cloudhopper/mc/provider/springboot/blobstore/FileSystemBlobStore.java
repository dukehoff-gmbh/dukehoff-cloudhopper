package eu.cloudhopper.mc.provider.springboot.blobstore;

/*-
 * #%L
 * provider-springboot - a library from the "Cloudhopper" project.
 * 
 * Dukehoff GmbH designates this particular file as subject to the "Classpath"
 * exception as provided in the LICENSE-EXCEPTIONS file that accompanies this code.
 * %%
 * Copyright (C) 2024 - 2026 Dukehoff GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import eu.cloudhopper.mc.runtime.BlobStore;
import eu.cloudhopper.mc.runtime.BlobStoreException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A bucket as a directory: every object is a file, its key the relative path with "/" as the
 * separator. For local runs and tests; several instances share it only on a shared volume.
 */
public class FileSystemBlobStore implements BlobStore {

    private static final String TEMP_SUFFIX = ".cloudhopper-tmp";

    private final Path root;

    /**
     * @param root the bucket's directory; it is created when the first object is written
     */
    public FileSystemBlobStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(Files.readAllBytes(file(key)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new BlobStoreException("Cannot read " + key + " in " + root, e);
        }
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        Path file = file(key);
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), ".", TEMP_SUFFIX);
            Files.write(temp, content);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BlobStoreException("Cannot write " + key + " in " + root, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(file(key));
        } catch (IOException e) {
            throw new BlobStoreException("Cannot delete " + key + " in " + root, e);
        }
    }

    @Override
    public List<String> list(String prefix) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> !file.getFileName().toString().endsWith(TEMP_SUFFIX))
                    .map(file -> root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/"))
                    .filter(key -> key.startsWith(prefix))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new BlobStoreException("Cannot list " + root, e);
        }
    }

    /** The file of a key; keys that would leave the bucket's directory are refused. */
    private Path file(String key) {
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root) || file.equals(root) || key.startsWith("/") || key.endsWith("/")
                || key.endsWith(TEMP_SUFFIX)) {
            throw new BlobStoreException("The key " + key + " cannot be stored as a file in " + root);
        }
        return file;
    }
}
