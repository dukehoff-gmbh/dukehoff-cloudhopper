<#include "storageQueueName.ftl">
package ${handlerInfo.handlerPackage};

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import eu.cloudhopper.mc.provider.azure.AzureBaseFunctionWrapper;
import ${handlerInfo.inputTypeImport};
import ${handlerInfo.outputTypeImport};
import ${handlerInfo.handlerFullyQualifiedName};
import com.google.gson.reflect.TypeToken;

public class Azure${handlerInfo.handlerClassName}EventFunction extends AzureBaseFunctionWrapper<${handlerInfo.inputType}, ${handlerInfo.outputType}> {

    public Azure${handlerInfo.handlerClassName}EventFunction() {
        super(new ${handlerInfo.handlerClassName}(), new TypeToken<${handlerInfo.inputType}>(){}.getType());
    }
<#if eventSource == "QUEUE">

    @FunctionName("${handlerInfo.functionId}_event")
    public void onQueueMessage(
            @QueueTrigger(name = "message", queueName = "${eventName}", connection = "AzureWebJobsStorage") String message,
            @BindingName("Id") String id,
            @BindingName("DequeueCount") long dequeueCount,
            @BindingName("InsertionTime") String insertionTime,
            final ExecutionContext context) {
        handleQueueMessage(message, id, dequeueCount, insertionTime, "${eventName}", context);
    }
<#elseif eventSource == "STORAGE">

    <#-- Event Grid puts the "blob created" events of the container on this queue (see storage.ftl) -->
    @FunctionName("${handlerInfo.functionId}_event")
    public void onStorageEvent(
            @QueueTrigger(name = "message", queueName = "${storageEventQueue(eventName)}", connection = "AzureWebJobsStorage") String message,
            @BindingName("DequeueCount") long dequeueCount,
            final ExecutionContext context) {
        handleBlobCreatedEvent(message, dequeueCount, context);
    }
</#if>
}
