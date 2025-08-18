package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Component;
import com.ll.framework.ioc.annotations.Configuration;
import com.ll.framework.ioc.annotations.Primary;
import com.ll.standard.util.Ut;
import lombok.SneakyThrows;
import org.reflections.Reflections;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

public class BeanScanner {
    private final Reflections reflections;
    private final Map<String, Object> recipes;
    private final Map<String, Object> beans;
    private final Map<String, List<String>> typeMapper;

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
            addTypeMapper(clazz.getTypeName(), className);
            
            // 해당 클래스에 Configuration 어노테이션까지 붙어있을 경우 Bean 어노테이션 메소드도 레시피 등록
            if (clazz.isAnnotationPresent(Configuration.class)) {
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    String methodName = Ut.str.lcfirst(method.getName());
                    if (!method.isAnnotationPresent(Bean.class)) continue;
                    recipes.put(methodName, method);
                    addTypeMapper(method.getReturnType().getTypeName(), methodName);
                }
            }
        }
    }

    public void addTypeMapper(String keyName, String classOrMethodName) {
        typeMapper.putIfAbsent(keyName, new ArrayList<>());
        typeMapper.get(keyName).add(classOrMethodName);
    }

    public List<String> getFieldNameList(Class<?> clazz) {
        List<String> FieldList = new ArrayList<>(){};

        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            if (!recipes.containsKey(field.getName())) continue;
            FieldList.add(field.getName());
        }

        return FieldList;
    }

    public List<Class<?>> getFieldClassList(List<String> fieldNameList) {
        List<Class<?>> classList = new ArrayList<>(){};
        for (String name : fieldNameList) {
            if (!recipes.containsKey(name)) continue;
            classList.add((Class<?>) recipes.get(name));
        }

        return classList;
    }

    public List<String> getParameterNameList(Method method) {
        List<String> fieldList = new ArrayList<>(){};
        Parameter[] parameters = method.getParameters();
        for(Parameter parameter : parameters){
            String typeName = parameter.getType().getTypeName();
            if (!typeMapper.containsKey(typeName)) continue;
            List<String> beanNames = typeMapper.get(typeName);
            if (beanNames.size()==1) fieldList.add(beanNames.getFirst());
            else {
                String primaryBeanName = beanNames.stream().
                        filter((name)->((Method) recipes.get(name)).isAnnotationPresent(Primary.class))
                        .findFirst().orElse(null);
                fieldList.add(primaryBeanName);
            }

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
        } else if (what instanceof Method method) {
            List<String> paramNameList = getParameterNameList(method);
            String parentClassName = method.getDeclaringClass().getSimpleName();

            Object[] obs = paramNameList.stream().map(this::genBean).toArray();

            bean = method.invoke(genBean(Ut.str.lcfirst(parentClassName)), obs);
        } else {
            throw new RuntimeException("recipes에 Class나 Method가 아닌 값이 있습니다.");
        }

        beans.put(beanName, bean);
        return bean;
    }
}
