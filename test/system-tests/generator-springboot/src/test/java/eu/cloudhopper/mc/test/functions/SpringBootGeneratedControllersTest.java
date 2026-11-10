package eu.cloudhopper.mc.test.functions;

/*-
 * #%L
 * system-tests-generator-springboot - a library from the "Cloudhopper" project.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.cloudhopper.mc.test.functions.QueueMessageFunction;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Starts the controllers generated for the springboot-http target from the shared test functions
 * and calls them over HTTP: header (#13), path and query parameters (#15) must reach the function,
 * and queue and storage events posted to /events/<function> (#2) must invoke it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = SpringBootGeneratedControllersTest.App.class)
class SpringBootGeneratedControllersTest {

    @SpringBootApplication
    static class App {
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    void queueEventsArePostedToTheEventEndpoint() {
        List<String> messages = new ArrayList<>();
        Handler collector = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger logger = Logger.getLogger(QueueMessageFunction.class.getName());
        logger.addHandler(collector);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("ce-id", "evt-1");
            ResponseEntity<Void> response = rest.postForEntity("/events/queuemessage",
                    new HttpEntity<>("{\"id\":1,\"name\":\"Alice\",\"ranking\":3}", headers), Void.class);

            assertEquals(204, response.getStatusCode().value());
            assertEquals(List.of("Queue message received: Alice (attempt 0, id evt-1)"), messages);

            // a body that is not a player is an error, so that the sender can deliver it again
            ResponseEntity<String> bad = rest.postForEntity("/events/queuemessage",
                    new HttpEntity<>("not json", headers), String.class);
            assertTrue(bad.getStatusCode().is5xxServerError());
        } finally {
            logger.removeHandler(collector);
        }
    }

    @Test
    void headerParamsAreMatchedCaseInsensitively() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-cloudhopper-test", "hello");
        headers.set("X-CLOUDHOPPER-COUNT", "3");

        ResponseEntity<String> response = rest.exchange("/echo-header", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("hello:3", response.getBody());
    }

    @Test
    void pathParamReachesTheFunction() {
        ResponseEntity<String> response = rest.getForEntity("/player/7", String.class);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"id\":7,\"name\":\"Player7\",\"ranking\":42}", response.getBody());
    }

    @Test
    void queryParamsReachTheFunction() {
        ResponseEntity<String> response = rest.getForEntity("/players/search?minRanking=10&maxRanking=20",
                String.class);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("[{\"id\":10,\"name\":\"Player10\",\"ranking\":10},{\"id\":20,\"name\":\"Player20\",\"ranking\":20}]",
                response.getBody());
    }
    @Test
    void storageEventsArePostedToTheEventEndpoint() {
        List<String> messages = new ArrayList<>();
        Handler collector = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger logger = Logger.getLogger(StorageUploadFunction.class.getName());
        logger.addHandler(collector);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Void> response = rest.postForEntity("/events/storageupload",
                    new HttpEntity<>("{\"key\":\"in/a.txt\",\"size\":7}", headers), Void.class);

            assertEquals(204, response.getStatusCode().value());
            // the bucket defaults to the name in the annotation
            assertEquals(List.of("Object uploaded: dukehoff-cloudhopper-tck-uploads/in/a.txt (7 bytes)"), messages);
        } finally {
            logger.removeHandler(collector);
        }
    }

}
