package eu.cloudhopper.mc.provider.aws.publisher;

/*-
 * #%L
 * provider-aws-publisher - a library from the "Cloudhopper" project.
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
import eu.cloudhopper.mc.provider.aws.auth.AwsCredentials;
import eu.cloudhopper.mc.runtime.PublishException;
import eu.cloudhopper.mc.runtime.Publisher;
import eu.cloudhopper.mc.runtime.spi.PublisherFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Publishers for SQS queues. The deployment passes each queue's URL as the configuration
 * property {@code cloudhopper.queue.<name>.url}; the region is taken from the URL, or from
 * {@code AWS_REGION}.
 */
public class SqsPublisherFactory implements PublisherFactory {

    private static final Pattern SQS_HOST = Pattern.compile("sqs\\.([a-z0-9-]+)\\.amazonaws\\.com(\\.cn)?");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public <T> Publisher<T> queue(String name, boolean existing) {
        String property = "cloudhopper.queue." + name + ".url";
        String url = ConfigProvider.getConfig().getOptionalValue(property, String.class)
                .orElseThrow(() -> new PublishException("No URL for queue " + name + ": the deployment sets it as "
                        + property.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(java.util.Locale.ROOT)));
        URI queue = URI.create(url);
        URI endpoint = URI.create(queue.getScheme() + "://" + queue.getRawAuthority() + "/");
        return new SqsPublisher<>(MAPPER, HTTP, endpoint, region(queue), url, AwsCredentials::fromEnvironment,
                Clock.systemUTC(), Duration.ofMillis(100));
    }

    static String region(URI queue) {
        Matcher matcher = SQS_HOST.matcher(queue.getHost());
        if (matcher.matches()) {
            return matcher.group(1);
        }
        String region = System.getenv("AWS_REGION");
        if (region == null || region.isBlank()) {
            throw new PublishException("Cannot tell the region of queue " + queue + ": set AWS_REGION");
        }
        return region;
    }
}
