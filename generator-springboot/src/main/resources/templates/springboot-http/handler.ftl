package ${packageName};

import eu.cloudhopper.mc.provider.springboot.SpringBootRequestHandler;
import ${handlerInfo.handlerFullyQualifiedName};
import ${handlerInfo.inputTypeImport};
import ${handlerInfo.outputTypeImport};
import javax.ws.rs.*;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

<#-- Import required path variable annotations -->
<#list parameters as param>
    <#if param.in == "PATH">
import org.springframework.web.bind.annotation.PathVariable;
    </#if>
</#list>

<#-- Class JavaDoc -->
/**
 * Auto-generated Spring Boot REST controller for API operation:
 * ${summary}
 * 
 * ${description}
 */
@RestController
@RequestMapping("${path}")
public class ${className} extends SpringBootRequestHandler<${inputType}, ${outputType}> {

    public ${className}() {
        super(new ${handlerClassName}());
    }

   @${httpMethod?lower_case?cap_first}Mapping
    public ResponseEntity<${outputType}> handle(
        <#-- If path parameters exist, include them in the method signature -->
        <#list parameters as param>
            <#if param.in == "PATH">
                @PathVariable("${param.name}") String ${param.name}<#if param_has_next>,</#if>
            </#if>
        </#list>
        <#-- Add input payload as body param if present -->
        <#if inputType != "void">
            <#if parameters?has_content>, </#if>@RequestBody ${inputType} input
        </#if>
    ) {
        <#-- Build call to handler -->
        <#if inputType != "void">
        return handle(input);
        <#else>
        return handle(null);
        </#if>
    }
}
