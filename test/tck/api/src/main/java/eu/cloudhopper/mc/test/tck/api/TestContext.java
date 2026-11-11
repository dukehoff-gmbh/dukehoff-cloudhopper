package eu.cloudhopper.mc.test.tck.api;

/*-
 * #%L
 * test-tck-api - a library from the "Cloudhopper" project.
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

import java.util.List;

public interface TestContext {
    String getHttpUrl(String functionName);
    Object invokeFunctionDirect(String functionName, Object input);
    List<String> fetchLogs(String functionName);
    void deployTestFunctions();
    void cleanupTestFunctions();

    /**
     * Sends a message to the queue an {@code @EventTrigger(source = QUEUE)} function listens to.
     *
     * @param queueName the name given in the annotation
     * @param message   the message body (JSON)
     */
    default void sendQueueMessage(String queueName, String message) {
        throw new UnsupportedOperationException("This binding cannot send queue messages");
    }

    /**
     * Uploads an object to the bucket an {@code @EventTrigger(source = STORAGE)} function listens to.
     *
     * @param bucket  the name given in the annotation
     * @param key     the object key
     * @param content the object content
     */
    default void uploadObject(String bucket, String key, String content) {
        throw new UnsupportedOperationException("This binding cannot upload objects");
    }

    /**
     * Deletes an object uploaded with {@link #uploadObject}, so that the bucket can be destroyed.
     *
     * @param bucket the name given in the annotation
     * @param key    the object key
     */
    default void deleteObject(String bucket, String key) {
        throw new UnsupportedOperationException("This binding cannot delete objects");
    }
}
