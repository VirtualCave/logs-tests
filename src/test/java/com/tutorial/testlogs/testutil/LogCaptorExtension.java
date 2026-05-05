package com.tutorial.testlogs.testutil;

import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;

public class LogCaptorExtension implements ParameterResolver, BeforeEachCallback, AfterEachCallback {

    private static final String CAPTORS_STORE_KEY = "logcaptors";
    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(LogCaptorExtension.class);

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Parameter parameter = parameterContext.getParameter();
        return parameter.getType() == LogCaptor.class &&
               parameter.isAnnotationPresent(LogCaptorSource.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Parameter parameter = parameterContext.getParameter();
        LogCaptorSource annotation = parameter.getAnnotation(LogCaptorSource.class);

        if (annotation == null) {
            throw new ParameterResolutionException("@LogCaptorSource no encontrada en el parámetro");
        }

        Class<?> targetClass = annotation.value();
        LogCaptor captor = LogCaptor.forClass(targetClass);

        Map<Class<?>, LogCaptor> captors = getCaptorsMap(extensionContext);
        captors.put(targetClass, captor);

        return captor;
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, LogCaptor> getCaptorsMap(ExtensionContext extensionContext) {
        return extensionContext.getStore(NAMESPACE)
            .getOrComputeIfAbsent(CAPTORS_STORE_KEY, k -> new HashMap<Class<?>, LogCaptor>(), 
                                  Map.class);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterEach(ExtensionContext extensionContext) {
        Map<Class<?>, LogCaptor> captors = (Map<Class<?>, LogCaptor>) extensionContext.getStore(NAMESPACE)
            .get(CAPTORS_STORE_KEY);
        if (captors != null) {
            captors.values().forEach(LogCaptor::close);
            captors.clear();
        }
    }
}


