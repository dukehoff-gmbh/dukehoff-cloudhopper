package ${handlerInfo.handlerPackage};

import eu.cloudhopper.mc.provider.springboot.SpringBootRequestHandler;
import ${handlerInfo.handlerFullyQualifiedName};
import ${handlerInfo.inputTypeImport};
import ${handlerInfo.outputTypeImport};

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

<#assign noInput = handlerInfo.inputType == "Void" || handlerInfo.inputType == "java.lang.Void" || handlerInfo.inputType == "void">
/**
 * Auto-generated direct invocation endpoint for the function "${handlerInfo.functionId}":
 * GET or POST /functions/${handlerInfo.functionId}<#if !noInput> with the input as JSON body</#if>.
 * Every function gets one, like the HTTP route on Azure or the function URL on GCP. Switch all of
 * them off with the property cloudhopper.functions.direct-endpoint=false.
 */
@RestController
@ConditionalOnProperty(name = "cloudhopper.functions.direct-endpoint", havingValue = "true", matchIfMissing = true)
public class ${handlerInfo.handlerClassName}FunctionController extends SpringBootRequestHandler<${handlerInfo.inputType}, ${handlerInfo.outputType}> {

    public ${handlerInfo.handlerClassName}FunctionController() {
        super(new ${handlerInfo.handlerClassName}());
    }

    @RequestMapping(path = "/functions/${handlerInfo.functionId}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<${handlerInfo.outputType}> invoke(<#if !noInput>@RequestBody(required = false) ${handlerInfo.inputType} input</#if>) {
        return handle(<#if noInput>null<#else>input</#if>);
    }
}
