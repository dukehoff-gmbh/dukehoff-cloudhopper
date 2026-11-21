package eu.cloudhopper.mc.provider.gcp.publisher;

/*-
 * #%L
 * provider-gcp-publisher - a library from the "Cloudhopper" project.
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

import com.google.gson.Gson;
import eu.cloudhopper.mc.provider.gcp.auth.GcpMetadata;
import eu.cloudhopper.mc.runtime.PublishException;
import eu.cloudhopper.mc.runtime.Publisher;
import eu.cloudhopper.mc.runtime.spi.PublisherFactory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Publishers for Pub/Sub topics. A queue is the topic of the same name in the function's project,
 * as the generated configuration creates it; the configuration property
 * {@code cloudhopper.queue.<name>.url} can name another topic ({@code projects/<p>/topics/<t>}).
 * The function's service account needs {@code roles/pubsub.publisher} on the topic.
 */
public class PubSubPublisherFactory implements PublisherFactory {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final GcpMetadata METADATA = new GcpMetadata();
    private static final Gson GSON = new Gson();

    @Override
    public <T> Publisher<T> queue(String name, boolean existing) {
        String topic = topic(name);
        return new PubSubPublisher<>(GSON, HTTP, URI.create("https://pubsub.googleapis.com/v1/" + topic + ":publish"),
                topic, METADATA::accessToken, Duration.ofMillis(100));
    }

    private static String topic(String name) {
        Optional<String> configured = ConfigProvider.getConfig()
                .getOptionalValue("cloudhopper.queue." + name + ".url", String.class);
        if (configured.isPresent()) {
            return configured.get();
        }
        try {
            return "projects/" + METADATA.projectId() + "/topics/" + name;
        } catch (IOException e) {
            throw new PublishException("Cannot tell the project of topic " + name + ": set GOOGLE_CLOUD_PROJECT", e);
        }
    }
}
