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

import eu.cloudhopper.mc.runtime.BlobStoreException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileSystemBlobStoreTest {

    @TempDir
    Path directory;

    @Test
    public void putGetListAndDelete() {
        FileSystemBlobStore store = new FileSystemBlobStore(directory.resolve("reports"));
        assertEquals(List.of(), store.list(""));
        byte[] content = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

        store.put("2026/11/a.json", content, "application/json");
        store.put("2026/12/b.json", content, "application/json");
        store.put("2027.json", content, "application/json");

        assertArrayEquals(content, store.get("2026/11/a.json").orElseThrow());
        assertTrue(store.get("2026/11/none.json").isEmpty());
        assertEquals(List.of("2026/11/a.json", "2026/12/b.json"), store.list("2026/"));
        assertEquals(List.of("2026/11/a.json", "2026/12/b.json", "2027.json"), store.list("202"));
        store.delete("2026/11/a.json");
        store.delete("2026/11/a.json");
        assertEquals(List.of("2026/12/b.json", "2027.json"), store.list(""));
    }

    @Test
    public void keysCannotLeaveTheBucketsDirectory() {
        FileSystemBlobStore store = new FileSystemBlobStore(directory.resolve("reports"));
        for (String key : List.of("../other/a", "a/../../b", "/etc/passwd", "a/")) {
            assertThrows(BlobStoreException.class, () -> store.put(key, new byte[]{1}, "text/plain"), key);
        }
    }
}
