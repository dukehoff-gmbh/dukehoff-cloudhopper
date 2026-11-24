package eu.cloudhopper.mc.provider.gcp.blobstore;


/*-
 * #%L
 * provider-gcp-blobstore - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.provider.gcp.auth.GcpMetadata;
import eu.cloudhopper.mc.runtime.BlobStore;
import eu.cloudhopper.mc.runtime.spi.BlobStoreFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Stores for Cloud Storage buckets, signed in as the function's service account through the
 * metadata server. The bucket's name is {@code cloudhopper.bucket.<name>.name}, which the
 * generated Terraform sets, or the name of the annotation.
 */
public class GcsBlobStoreFactory implements BlobStoreFactory {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final GcpMetadata METADATA = new GcpMetadata();
    private static final URI API = URI.create("https://storage.googleapis.com/");

    @Override
    public BlobStore bucket(String name, boolean existing) {
        String bucket = ConfigProvider.getConfig()
                .getOptionalValue("cloudhopper.bucket." + name + ".name", String.class).orElse(name);
        return new GcsBlobStore(HTTP, API, bucket, METADATA::accessToken, Duration.ofMillis(100));
    }
}
