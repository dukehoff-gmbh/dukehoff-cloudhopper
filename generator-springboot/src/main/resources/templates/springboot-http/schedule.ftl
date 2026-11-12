package ${handlerInfo.handlerPackage};

import eu.cloudhopper.mc.provider.springboot.SpringBootRequestHandler;
import ${handlerInfo.handlerFullyQualifiedName};
import ${handlerInfo.inputTypeImport};
import ${handlerInfo.outputTypeImport};

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Auto-generated trigger endpoint for the scheduled function "${handlerInfo.functionId}"
 * (cron "${scheduleExpression?replace("*/", "*&#47;")}", ${scheduleTimezone}): POST /schedules/${handlerInfo.functionId} runs it once.
 * An external scheduler calls it, for example a Kubernetes CronJob or the cron trigger of a
 * platform that scales to zero. Switch all of these endpoints off with the property
 * cloudhopper.schedules.endpoint=false.
 */
@RestController
@ConditionalOnProperty(name = "cloudhopper.schedules.endpoint", havingValue = "true", matchIfMissing = true)
public class ${handlerInfo.handlerClassName}ScheduleController extends SpringBootRequestHandler<${handlerInfo.inputType}, ${handlerInfo.outputType}> {

    public ${handlerInfo.handlerClassName}ScheduleController() {
        super(new ${handlerInfo.handlerClassName}());
    }

    @PostMapping("/schedules/${handlerInfo.functionId}")
    public ResponseEntity<Void> trigger() {
        handle(null);
        return ResponseEntity.noContent().build();
    }
}
