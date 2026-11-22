package eu.cloudhopper.mc.provider.azure.publisher;

/*-
 * #%L
 * provider-azure-publisher - a library from the "Cloudhopper" project.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.cloudhopper.mc.provider.azure.auth.StorageAccount;
import eu.cloudhopper.mc.runtime.PublishException;
import eu.cloudhopper.mc.runtime.Publisher;
import eu.cloudhopper.mc.runtime.spi.PublisherFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Publishers for the Storage queues of the function app's storage account, whose connection
 * string the app has as {@code AzureWebJobsStorage}; the queue triggers read the same account.
 */
public class StorageQueuePublisherFactory implements PublisherFactory {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public <T> Publisher<T> queue(String name, boolean existing) {
        String connection = ConfigProvider.getConfig().getOptionalValue("AzureWebJobsStorage", String.class)
                .orElseThrow(() -> new PublishException("No storage account for queue " + name
                        + ": the function app has no AzureWebJobsStorage setting"));
        StorageAccount account = StorageAccount.parse(connection);
        return new StorageQueuePublisher<>(MAPPER, HTTP, account, URI.create(account.endpoint("queue")), name,
                Clock.systemUTC(), Duration.ofMillis(100));
    }
}
