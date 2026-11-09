package ${packageName};

import eu.cloudhopper.mc.provider.springboot.SpringBootRequestHandler;
import ${handlerInfo.handlerFullyQualifiedName};
import ${handlerInfo.inputTypeImport};
import ${handlerInfo.outputTypeImport};

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
            @PathVariable Map<String, String> pathParams,
            @RequestParam Map<String, String> queryParams,
            @RequestHeader HttpHeaders headers<#if inputType != "void">,
            @RequestBody(required = false) ${inputType} input</#if>) {
        return handle(<#if inputType != "void">input<#else>null</#if>, pathParams, queryParams, headers.toSingleValueMap());
    }
}
