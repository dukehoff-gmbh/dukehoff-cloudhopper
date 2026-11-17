 {
    "operationId": "${operationId}",
<#-- the JSON goes through templatefile(), so ${ and %{ in the texts are escaped -->
<#if summary?has_content>
    "summary": "${summary?json_string?replace(r"${", r"$${")?replace(r"%{", r"%%{")}",
</#if>
<#if description?has_content>
    "description": "${description?json_string?replace(r"${", r"$${")?replace(r"%{", r"%%{")}",
</#if>
<#if parameters?has_content>
    "parameters": [
      <#list parameters as param>
      {
        "name": "${param.name}",
        "in": "${param.in?lower_case}",
        "required": true,
        "type": "string"
      }<#if param_has_next>,</#if>
      </#list>
    ],
</#if>
    "responses": {
      "default": {
        "description": "default"
      }
    },
    "x-google-backend": {
        "address": "https://${"$"}{region}-${"$"}{project}.cloudfunctions.net/${handlerInfo.functionId}-api",
        "protocol": "h2",
        "path_translation": "APPEND_PATH_TO_ADDRESS" 
    }
  }

