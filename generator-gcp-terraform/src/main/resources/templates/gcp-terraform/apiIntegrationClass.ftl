package ${handlerInfo.handlerPackage};

import ${handlerInfo.handlerFullyQualifiedName};

/**
 * GCP HTTP-triggered API integration handler for ${handlerInfo.handlerClassName}.
 * This class provides the route pattern for path parameter extraction.
 */
public class Gcp${className} extends Gcp${handlerInfo.handlerClassName}Function {

    @Override
    protected String getRoutePattern() {
        return "${path}";
    }
}