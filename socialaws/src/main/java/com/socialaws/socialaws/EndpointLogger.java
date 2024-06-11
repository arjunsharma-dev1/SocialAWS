package com.socialaws.socialaws;

import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Component
public class EndpointLogger implements ApplicationListener<WebServerInitializedEvent> {

    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private EndpointLogger(RequestMappingHandlerMapping requestMappingHandlerMapping) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        requestMappingHandlerMapping.getHandlerMethods().forEach((key, value) -> System.out.println(key));
    }

    @Override
    public boolean supportsAsyncExecution() {
        return ApplicationListener.super.supportsAsyncExecution();
    }
}
