package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Component;
import com.ll.framework.ioc.annotations.Configuration;
import com.ll.standard.util.Ut;
import lombok.Getter;
import lombok.SneakyThrows;
import org.reflections.Reflections;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

public class BeanScanner {
    Reflections reflections;
    @Getter
    Map<String, Object> recipes;
    @Getter
    Map<String, Object> beans;

    Map<String, List<String>> typeMapper;

    public BeanScanner(String basePackage) {
        reflections = new Reflections(basePackage); // 패키지 루트
        recipes= new HashMap<String, Object>();
        beans = new HashMap<String, Object>();
        typeMapper = new  HashMap<>();
    }

    public void init() {
        Set<Class<?>> comps = reflections.getTypesAnnotatedWith(Component.class);
        addRecipe(comps);
    }

    public void addRecipe(Set<Class<?>> classes) {
        for (Class<?> clazz : classes) {
            String className = Ut.str.lcfirst(clazz.getSimpleName());
            recipes.put(className, clazz);
            typeMapper.putIfAbsent(clazz.getTypeName(), new ArrayList<>());
            typeMapper.get(clazz.getTypeName()).add(className);

            if (clazz.isAnnotationPresent(Configuration.class)) {
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    String methodName = Ut.str.lcfirst(method.getName());
                    if (!method.isAnnotationPresent(Bean.class)) continue;
                    recipes.put(methodName, method);
                    typeMapper.putIfAbsent(method.getReturnType().getTypeName(), new ArrayList<>());
                    typeMapper.get(method.getReturnType().getTypeName()).add(methodName);
                }
            }
        }
    }

    public List<String> getFieldNameList(Class<?> clazz) throws Exception {
        List<String> FieldList = new ArrayList<>(){};

        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            if (!recipes.containsKey(field.getName())) continue; // 예외 복구하기
            FieldList.add(field.getName());
        }

        return FieldList;
    }

    public List<Class<?>> getFieldClassList(List<String> fieldNameList) throws Exception {
        List<Class<?>> classList = new ArrayList<>(){};
        for (String name : fieldNameList) {
            if (!recipes.containsKey(name)) continue;
            classList.add((Class<?>) recipes.get(name));
        }

        return classList;
    }

    public List<String> getParameterNameList(Method method) throws Exception {
        List<String> fieldList = new ArrayList<>(){};
        Parameter[] parameters = method.getParameters();
        for(Parameter parameter : parameters){
            String typeName = parameter.getType().getTypeName();
            fieldList.add(typeMapper.get(typeName).getFirst());
        }

        return fieldList;
    }

    @SneakyThrows
    public Object genBean(String beanName) {
        if (beans.containsKey(beanName)) return beans.get(beanName);
        if (!recipes.containsKey(beanName)) throw new RuntimeException("Bean 이름을 다시 확인해주세요.");

        Object what = recipes.get(beanName);
        Object bean = null;
        if (what instanceof Class<?> clazz) {
            List<String> fieldNameList = getFieldNameList(clazz);
            List<Class<?>> fieldClasses = getFieldClassList(fieldNameList);
            Class<?>[] fieldClassArray = fieldClasses.toArray(Class<?>[]::new);

            Object[] obs = fieldNameList.stream().map(this::genBean).toArray();

            clazz.getDeclaredConstructor(fieldClassArray).setAccessible(true);
            bean = clazz.getDeclaredConstructor(fieldClassArray).newInstance(obs);
            beans.put(beanName, bean);
        } else if (what instanceof Method method) {
            List<String> paramNameList = getParameterNameList(method);
            String parentClassName = method.getDeclaringClass().getSimpleName();

            Object[] obs = paramNameList.stream().map(this::genBean).toArray();

            bean = method.invoke(genBean(Ut.str.lcfirst(parentClassName)), obs);
            beans.put(beanName, bean);
            return bean;
        }


        return bean;
    }
}
