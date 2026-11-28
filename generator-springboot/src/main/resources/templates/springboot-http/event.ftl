<#assign unit = handlerInfo.sharedIdentity?then(handlerInfo.identity, "")>
package ${packageName};

import eu.cloudhopper.mc.provider.springboot.SpringBootEventHandler;
import ${handlerInfo.handlerFullyQualifiedName};
import ${handlerInfo.inputTypeImport};
import ${handlerInfo.outputTypeImport};

import tools.jackson.core.type.TypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Auto-generated Spring Boot endpoint for the event-triggered function "${handlerInfo.functionId}"
 * (${eventSource?lower_case} "${eventName}"): POST the event data to /events/${handlerInfo.functionId}.
<#if eventSource == "QUEUE">
 * With spring-boot-starter-kafka on the classpath and cloudhopper.events.broker=kafka, the function also
 * consumes the Kafka topic "${eventName}".
</#if>
 */
@RestController
public class ${handlerInfo.handlerClassName}EventController extends SpringBootEventHandler<${handlerInfo.inputType}, ${handlerInfo.outputType}> {

    public ${handlerInfo.handlerClassName}EventController() {
        super(new ${handlerInfo.handlerClassName}(), new TypeReference<${handlerInfo.inputType}>() {},
                "${handlerInfo.functionId}", eu.cloudhopper.mc.annotations.EventSource.${eventSource}, "${eventName}");
    }

    @Override
    public String identity() {
        return "${unit}";
    }

    @PostMapping("/events/${handlerInfo.functionId}")
    public ResponseEntity<Void> onEvent(
            @RequestBody(required = false) String body,
            @RequestHeader HttpHeaders headers) throws java.io.IOException {
        if (!eu.cloudhopper.mc.provider.springboot.Identities.serves("${unit}")) {
            return eu.cloudhopper.mc.provider.springboot.Identities.notServed();
        }
        return handleEvent(body, headers.toSingleValueMap(), "${eventName}");
    }
}
