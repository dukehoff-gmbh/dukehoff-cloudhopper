package eu.cloudhopper.mc.test.functions;

/*-
 * #%L
 * demo - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.annotations.Bucket;
import eu.cloudhopper.mc.annotations.Bucket.Access;
import eu.cloudhopper.mc.annotations.Function;
import eu.cloudhopper.mc.runtime.BlobAccessDeniedException;
import eu.cloudhopper.mc.runtime.BlobStore;
import eu.cloudhopper.mc.runtime.BlobStoreException;
import eu.cloudhopper.mc.runtime.spi.BlobStoreFactory;
import java.nio.charset.StandardCharsets;
import java.util.ServiceLoader;

/**
 * Two functions on one bucket: "blobwriter" may read and write it, "blobreader" only read. The
 * reader's {@code put} goes through its {@code @Bucket} parameter, which refuses it;
 * {@code putUnchecked} bypasses that check and calls the target's store directly, so that only
 * the platform's permissions of the reader's identity can refuse it. Targets without
 * {@code @Bucket} warn at build time and fail the calls.
 */
public class BlobStoreFunctions {

    static final String BUCKET = "cloudhopper-tck-blobs";

    // GCP: a Java function calling a cloud API over HTTP exceeds 128 MB; the writer shares an
    // identity, so that the TCK deployment on GCP stays within the quota of service accounts
    // created per minute
    @Function(name = "blobwriter", memory = 256, identity = "tck-shared")
    public String write(BlobRequest request, @Bucket(name = BUCKET, access = Access.READ_WRITE) BlobStore bucket) {
        return run(request, bucket);
    }

    // the identity of the readers of buckets and tables (KeyValueFunctions)
    @Function(name = "blobreader", memory = 256, identity = "tck-reader")
    public String read(BlobRequest request, @Bucket(name = BUCKET) BlobStore bucket) {
        if ("putUnchecked".equals(request.getOp())) {
            BlobStoreFactory factory = ServiceLoader.load(BlobStoreFactory.class, BlobStoreFunctions.class.getClassLoader())
                    .findFirst().orElseThrow();
            return run(put(request), factory.bucket(BUCKET, false));
        }
        return run(request, bucket);
    }

    private static BlobRequest put(BlobRequest request) {
        BlobRequest put = new BlobRequest();
        put.setOp("put");
        put.setKey(request.getKey());
        put.setContent(request.getContent());
        return put;
    }

    private static String run(BlobRequest request, BlobStore bucket) {
        try {
            switch (request.getOp()) {
                case "put":
                    bucket.put(request.getKey(), request.getContent().getBytes(StandardCharsets.UTF_8), "text/plain");
                    return "stored";
                case "get":
                    return bucket.get(request.getKey())
                            .map(content -> new String(content, StandardCharsets.UTF_8))
                            .orElse("missing");
                case "list":
                    return String.join(",", bucket.list(request.getKey()));
                case "delete":
                    bucket.delete(request.getKey());
                    return "deleted";
                default:
                    return "unknown operation " + request.getOp();
            }
        } catch (BlobAccessDeniedException e) {
            System.out.println("Bucket operation refused: " + e.getMessage());
            return "denied";
        } catch (BlobStoreException e) {
            System.out.println("Bucket operation failed: " + e.getMessage());
            return "failed: " + e.getMessage();
        }
    }
}
