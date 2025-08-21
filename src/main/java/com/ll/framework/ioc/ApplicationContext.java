package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Repository;
import com.ll.framework.ioc.annotations.Service;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.beans.Introspector;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ApplicationContext {
    private final String basePackage;
    private final Map<String, Class<?>> providers = new HashMap<>();
    private final Map<String, Method> methods = new HashMap<>();
    private final Map<String, Object> beans = new HashMap<>();

    public ApplicationContext(String basePackage) {
        this.basePackage = basePackage;
    }

    public void init() {
        Reflections reflections = new Reflections(
                new ConfigurationBuilder()
                .forPackages(basePackage)
                .addScanners(
                    Scanners.TypesAnnotated, Scanners.MethodsAnnotated
                )
        );

        for (Class<?> clazz : reflections.getTypesAnnotatedWith(Service.class)) {
            String name = Introspector.decapitalize(clazz.getSimpleName());
            System.out.println(name);
            providers.put(name, clazz);
        }

        for (Class<?> clazz : reflections.getTypesAnnotatedWith(Repository.class)) {
            String name = Introspector.decapitalize(clazz.getSimpleName());
            System.out.println(name);
            providers.put(name, clazz);
        }

        Set<Method> beanMethods = reflections.get(Scanners.MethodsAnnotated.with(Bean.class)
                .as(Method.class));
        for (Method method : beanMethods) {
            String name = Introspector.decapitalize(method.getName());
            System.out.println(name);
            methods.put(name, method);
        }
    }

    public <T> T genBean(String beanName) {
        Object bean = beans.get(beanName);
        if (bean != null) return (T) bean;

        Class<?> clazz = providers.get(beanName);
        if (clazz != null) {
            try {
                Constructor<?> constructor = clazz.getDeclaredConstructors()[0];
                constructor.setAccessible(true);

                Object[] args = new Object[constructor.getParameterTypes().length];
                for (int i = 0; i < args.length; i++) {
                    String name = Introspector.decapitalize(constructor.getParameterTypes()[i].getSimpleName());
                    args[i] = genBean(name);
                }
                Object instance = constructor.newInstance(args);
                beans.put(beanName, instance);
                return (T) instance;
            } catch (Exception e) {
                return null;
            }
        }

        Method method = methods.get(beanName);
        if (method != null) {
            try {
                Constructor<?> constructor = method.getDeclaringClass().getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                Object config = constructor.newInstance();
                Class<?>[] parameterTypes = method.getParameterTypes();
                Object[] args = new Object[parameterTypes.length];
                for (int i = 0; i < args.length; i++) {
                    args[i] = getBeanByType(parameterTypes[i]);
                }
                Object instance = method.invoke(config, args);
                beans.put(beanName, instance);
                return (T) instance;
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }

    private Object getBeanByType(Class<?> type) {
        for (Object bean : beans.values()) {
            if (type.isInstance(bean)) {
                return bean;
            }
        }
        // providers / methods 에서 새로 생성 시도
        for (Map.Entry<String, Class<?>> entry : providers.entrySet()) {
            if (type.isAssignableFrom(entry.getValue())) {
                return genBean(entry.getKey());
            }
        }
        for (Map.Entry<String, Method> entry : methods.entrySet()) {
            if (type.isAssignableFrom(entry.getValue().getReturnType())) {
                return genBean(entry.getKey());
            }
        }
        throw new RuntimeException("타입 [" + type.getName() + "]에 맞는 빈을 찾을 수 없습니다.");
    }
}
