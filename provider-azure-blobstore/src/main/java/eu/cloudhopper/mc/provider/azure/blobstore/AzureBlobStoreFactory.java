package eu.cloudhopper.mc.provider.azure.blobstore;


/*-
 * #%L
 * provider-azure-blobstore - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.provider.azure.auth.StorageAccount;
import eu.cloudhopper.mc.runtime.BlobStore;
import eu.cloudhopper.mc.runtime.BlobStoreException;
import eu.cloudhopper.mc.runtime.spi.BlobStoreFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Stores for the blob containers of the function app's storage account, whose connection string
 * the app has as {@code AzureWebJobsStorage}. The container's name is
 * {@code cloudhopper.bucket.<name>.name}, or the name of the annotation.
 */
public class AzureBlobStoreFactory implements BlobStoreFactory {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public BlobStore bucket(String name, boolean existing) {
        Config config = ConfigProvider.getConfig();
        String connection = config.getOptionalValue("AzureWebJobsStorage", String.class)
                .orElseThrow(() -> new BlobStoreException("No storage account for bucket " + name
                        + ": the function app has no AzureWebJobsStorage setting"));
        StorageAccount account = StorageAccount.parse(connection);
        String container = config.getOptionalValue("cloudhopper.bucket." + name + ".name", String.class).orElse(name);
        return new AzureBlobStore(HTTP, account, URI.create(account.endpoint("blob")), container, Clock.systemUTC(),
                Duration.ofMillis(100));
    }
}
