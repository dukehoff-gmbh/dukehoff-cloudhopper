package ${handlerInfo.handlerPackage};

import ${handlerInfo.inputTypeImport};
import ${handlerInfo.outputTypeImport};
import ${handlerInfo.handlerFullyQualifiedName};

import eu.cloudhopper.mc.provider.gcp.GcpCloudFunctionRequestHandler;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.reflect.TypeToken;

public class Gcp${handlerInfo.handlerClassName}Function extends GcpCloudFunctionRequestHandler<${handlerInfo.inputType}, ${handlerInfo.outputType}> {

    public Gcp${handlerInfo.handlerClassName}Function() {
        super(new ${handlerInfo.handlerClassName}(), new TypeToken<${handlerInfo.inputType}>() {});
    }

    @Override
    protected String getRoutePattern() {
        return null;
    }
 
}