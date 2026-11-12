package ${handlerInfo.handlerPackage};

import eu.cloudhopper.mc.provider.springboot.SpringBootRequestHandler;
import ${handlerInfo.handlerFullyQualifiedName};
import ${handlerInfo.inputTypeImport};
import ${handlerInfo.outputTypeImport};

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

<#-- Spring cron expressions start with a seconds field; a Unix cron expression has five fields -->
<#assign cronParts = scheduleExpression?trim?split("\\s+", "r")>
<#if cronParts?size == 5>
<#assign springCron = "0 " + cronParts?join(" ")>
<#else>
<#assign springCron = cronParts?join(" ")>
</#if>
/**
 * Auto-generated in-process schedule for the function "${handlerInfo.functionId}":
 * runs it on the cron expression "${scheduleExpression?replace("*/", "*&#47;")}" in the time zone ${scheduleTimezone}.
 * Every running instance fires on its own, so when the service runs with several replicas or
 * scales to zero, set cloudhopper.schedules.in-process=false and call
 * POST /schedules/${handlerInfo.functionId} from an external scheduler instead.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "cloudhopper.schedules.in-process", havingValue = "true", matchIfMissing = true)
public class ${handlerInfo.handlerClassName}ScheduledJob extends SpringBootRequestHandler<${handlerInfo.inputType}, ${handlerInfo.outputType}> {

    public ${handlerInfo.handlerClassName}ScheduledJob() {
        super(new ${handlerInfo.handlerClassName}());
    }

    @Scheduled(cron = "${springCron}", zone = "${scheduleTimezone}")
    public void run() {
        handle(null);
    }
}
