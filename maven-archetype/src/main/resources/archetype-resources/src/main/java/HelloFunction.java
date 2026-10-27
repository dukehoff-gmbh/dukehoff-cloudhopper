package ${package};


import eu.cloudhopper.mc.annotations.HttpTrigger;
import eu.cloudhopper.mc.annotations.Function;
import eu.cloudhopper.mc.annotations.PathParam;

import java.util.Map;

public class HelloFunction  {

    @Function(name = "hello")
    @HttpTrigger(
        method      = "GET",
        path        = "/hello/{name}",
        summary     = "Say Hello",
        description = "Returns a friendly greeting"
    )
    public String handleRequest(@PathParam String name) {
        return "hello "+name;
    }
}

