package ${handlerInfo.handlerPackage};

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import eu.cloudhopper.mc.provider.azure.AzureBaseFunctionWrapper;
import ${handlerInfo.inputTypeImport};
import ${handlerInfo.outputTypeImport};
import ${handlerInfo.handlerFullyQualifiedName};
import com.google.gson.reflect.TypeToken;


public class Azure${handlerInfo.handlerClassName}ApiFunction extends AzureBaseFunctionWrapper<${handlerInfo.inputType}, ${handlerInfo.outputType}> {

    public Azure${handlerInfo.handlerClassName}ApiFunction() {
        super(new ${handlerInfo.handlerClassName}(), new TypeToken<${handlerInfo.inputType}>(){}.getType());
    }

    @FunctionName("${handlerInfo.functionId}_api")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", 
                        methods = {HttpMethod.${httpMethod}},
                        authLevel = AuthorizationLevel.ANONYMOUS,
                        route = "${path?remove_beginning("/")}"
                        ) 
            HttpRequestMessage<String> request,
            final ExecutionContext context) {
        return handleRequest(request, context, "${path}");
    }
}
