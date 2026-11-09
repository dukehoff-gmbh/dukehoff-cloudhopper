package ${handlerInfo.handlerPackage};

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import eu.cloudhopper.mc.provider.azure.AzureBaseFunctionWrapper;
import ${handlerInfo.inputTypeImport};
import ${handlerInfo.outputTypeImport};
import ${handlerInfo.handlerFullyQualifiedName};
import com.google.gson.reflect.TypeToken;

<#-- event sources other than QUEUE get no Java function; the class still compiles -->
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
</#if>
}
