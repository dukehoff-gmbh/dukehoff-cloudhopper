package ${handlerInfo.handlerPackage};

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import eu.cloudhopper.mc.provider.azure.AzureBaseFunctionWrapper;
import ${handlerInfo.inputTypeImport};
import ${handlerInfo.outputTypeImport};
import ${handlerInfo.handlerFullyQualifiedName};
import com.google.gson.reflect.TypeToken;


public class Azure${handlerInfo.handlerClassName}ScheduledFunction extends AzureBaseFunctionWrapper<${handlerInfo.inputType}, ${handlerInfo.outputType}> {

    public Azure${handlerInfo.handlerClassName}ScheduledFunction() {
        super(new ${handlerInfo.handlerClassName}(), new TypeToken<${handlerInfo.inputType}>(){}.getType());
    }

    @FunctionName("${handlerInfo.functionId}_scheduled")
    public void runTimer(
            @TimerTrigger(name = "timerInfo", schedule = "${scheduleExpression}") String timerInfo,
            final ExecutionContext context) {
        handleScheduledRequest(context);
    }   
}