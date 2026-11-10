package eu.cloudhopper.mc.annotations;

/*-
 * #%L
 * deployment-config-api - a library from the "Cloudhopper" project.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 * Declares that the annotated function is invoked for each event from a source such as a queue
 * or a storage bucket.
 *
 * <p>Use it together with {@link Function}. The generator creates the source (unless
 * {@link #create()} is {@code false}) and connects it to the function on the target platform:</p>
 *
 * <table>
 *   <caption>{@link EventSource#QUEUE} per target</caption>
 *   <tr><th>Target</th><th>Resource</th></tr>
 *   <tr><td>AWS</td><td>SQS queue</td></tr>
 *   <tr><td>Azure</td><td>Storage queue in the function app's storage account</td></tr>
 *   <tr><td>GCP</td><td>Pub/Sub topic, delivered through Eventarc</td></tr>
 *   <tr><td>Spring Boot</td><td>{@code POST /events/<function name>}</td></tr>
 * </table>
 *
 * <table>
 *   <caption>{@link EventSource#STORAGE} per target</caption>
 *   <tr><th>Target</th><th>Resource</th></tr>
 *   <tr><td>AWS</td><td>S3 bucket with an event notification</td></tr>
 *   <tr><td>Azure</td><td>Blob container in the function app's storage account, delivered through
 *       Event Grid and a storage queue</td></tr>
 *   <tr><td>GCP</td><td>Cloud Storage bucket, delivered through Eventarc</td></tr>
 *   <tr><td>Spring Boot</td><td>{@code POST /events/<function name>}</td></tr>
 * </table>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @Function(name = "orderReceived")
 * @EventTrigger(source = EventSource.QUEUE, name = "orders")
 * public void onOrder(Order order, EventMetadata meta) {
 *     // the message body is deserialized from JSON, like an HTTP request body
 * }
 *
 * @Function(name = "invoiceUploaded")
 * @EventTrigger(source = EventSource.STORAGE, name = "invoices")
 * public void onUpload(StorageEvent event) {
 *     // bucket, key and size of the new object; read the content yourself if needed
 * }
 * }</pre>
 *
 * <p>The function receives one message per invocation. Delivery is at least once: if the
 * function throws, the platform delivers the message again, so the function should tolerate
 * duplicates. A parameter of type {@link eu.cloudhopper.mc.runtime.EventMetadata} receives the
 * message id, source, time, delivery attempt and attributes.</p>
 */
@Retention(SOURCE)
@Target(ElementType.METHOD)
public @interface EventTrigger {

    /**
     * The kind of event source.
     *
     * @return the event source
     */
    EventSource source();

    /**
     * The name of the queue (or topic) or bucket the function listens to.
     *
     * <p>Use 3 to 63 lowercase letters, digits and single hyphens, starting with a letter, so
     * that the name is valid on every target. Bucket names on AWS and GCP are global across all
     * accounts: choose a name nobody else uses, for example one that starts with your domain.</p>
     *
     * @return the source name
     */
    String name();

    /**
     * Whether the generator creates the source.
     *
     * <p>{@code true} (the default) creates it together with the function. {@code false}
     * connects the function to an existing source of that name.</p>
     *
     * @return {@code true} to create the source
     */
    boolean create() default true;

    /**
     * Constants representing attribute names of {@link EventTrigger}, for generators that
     * declare their support via {@code @GeneratorFeature}.
     *
     * <p>This class is not intended for application developers and can be ignored
     * in function code.</p>
     */
    class EventTriggerAttribute {
        public static final String SOURCE = "source";
        public static final String NAME = "name";
        public static final String CREATE = "create";
    }
}
