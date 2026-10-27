package ${handlerInfo.handlerPackage};

import ${handlerInfo.inputTypeImport};
import ${handlerInfo.outputTypeImport};
import ${handlerInfo.handlerFullyQualifiedName};

import eu.cloudhopper.mc.provider.aws.AwsLambdaRequestHandler;
import eu.cloudhopper.mc.provider.aws.ApiGatewayV2ProxyRequestHandler;
import eu.cloudhopper.mc.provider.aws.ApiGatewayV2HttpEventHandler;
import eu.cloudhopper.mc.provider.aws.ApiGatewayEventRouter;

/**
 * Generated AWS Lambda handler class for ${handlerInfo.handlerClassName}.
 */
public class AwsLambda${handlerInfo.handlerClassName}Handler {

    /**
     * Direct Lambda invocation (POJO input).
     */
    public static class Plain extends AwsLambdaRequestHandler<${handlerInfo.inputType}, ${handlerInfo.outputType}> {
        public Plain() {
            super(new ${handlerInfo.handlerClassName}());
        }
    }

    /**
     * API Gateway V2 proxy integration.
     */
    public static class ApiProxyV2 extends ApiGatewayV2ProxyRequestHandler<${handlerInfo.inputType}, ${handlerInfo.outputType}> {
        public ApiProxyV2() {
            super(new ${handlerInfo.handlerClassName}(), ${handlerInfo.inputType}.class);
        }
    }

    /**
     * API Gateway V2 typed HTTP event integration.
     */
    public static class ApiHttpV2 extends ApiGatewayV2HttpEventHandler<${handlerInfo.inputType}, ${handlerInfo.outputType}> {
        public ApiHttpV2() {
            super(new ${handlerInfo.handlerClassName}(), ${handlerInfo.inputType}.class);
        }
    }

    /**
     * Smart router for all supported event types (proxy, http, scheduled, plain).
     */
    public static class Auto extends ApiGatewayEventRouter<${handlerInfo.inputType}, ${handlerInfo.outputType}> {
        public Auto() {
            super(new Plain(), new ApiProxyV2(), new ApiHttpV2());
        }
    }
}