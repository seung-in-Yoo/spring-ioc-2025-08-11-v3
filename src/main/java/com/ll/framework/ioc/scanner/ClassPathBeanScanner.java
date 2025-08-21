package com.ll.framework.ioc.scanner;

import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Component;
import com.ll.framework.ioc.annotations.Configuration;
import com.ll.framework.ioc.annotations.Repository;
import com.ll.framework.ioc.annotations.Service;
import com.ll.framework.ioc.dto.BeanDefinitionDto;
import com.ll.standard.util.Ut;
import org.reflections.Reflections;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public final class ClassPathBeanScanner {

    // 어노테이션 스캔
    private static final Class<? extends Annotation>[] AnnoScan = new Class[]{
            Component.class, Service.class, Repository.class, Configuration.class
    };

    private final String[] basePackages;

    public ClassPathBeanScanner(String... basePackages) { // 외부에서 생성자 주입
        this.basePackages = (basePackages == null || basePackages.length == 0) ? new String[]{"com.ll"} : basePackages;
    }

    public Map<String, BeanDefinitionDto> scan() {
        Map<String, BeanDefinitionDto> out = new LinkedHashMap<>();

        for (String base : basePackages) {
            Reflections reflections = new Reflections(base);

            // 스캔 대상 어노테이션들 구상클래스만 남기기
            Set<Class<?>> candidateTypes = new LinkedHashSet<>();

            for (Class<? extends Annotation> ann : AnnoScan) {
                Set<Class<?>> found = reflections.getTypesAnnotatedWith(ann, true);
                for (Class<?> type : found) {
                    if (isConcrete(type)) {
                        candidateTypes.add(type);
                    }
                }
            }

            for (Class<?> type : candidateTypes) {
                String name = Ut.str.lcfirst(type.getSimpleName());
                BeanDefinitionDto exist = out.putIfAbsent(name, BeanDefinitionDto.ofClass(name, type)); // 이름 충돌없이 맵 등록
                if (exist != null && !exist.getType().equals(type)) {
                    // 이름 같을시에 예외처리
                    throw new IllegalStateException("빈 이름 충돌: '" + name + "'");
                }
            }

            // Configuration 클래스의 @Bean 메서드도 빈으로 등록 (v2에서 해당 로직만 추가하여 v3 구현)
            for (Class<?> configurationClass : candidateTypes) {
                if (!configurationClass.isAnnotationPresent(Configuration.class)) continue;

                for (Method m : configurationClass.getDeclaredMethods()) {
                    if (!m.isAnnotationPresent(Bean.class)) { continue; }
                    if (m.getReturnType() == void.class) {
                        throw new IllegalStateException("@Bean 메서드는 void를 반환할 수 없습니다: " + configurationClass.getName() + "메서드 명: " + m.getName());
                    }

                    String beanName = m.getName(); // 메서드명 = 빈 이름
                    Class<?> returnType = m.getReturnType();
                    BeanDefinitionDto exist = out.putIfAbsent(beanName,
                            BeanDefinitionDto.ofBeanMethod(beanName, returnType, configurationClass, m));

                    if (exist != null && !exist.getType().equals(returnType)) {
                        throw new IllegalStateException("빈 이름 충돌: '" + beanName + "'");
                    }
                }
            }
        }
        return out;
    }

    // 구상 클래스인지 검사하는 필터
    private static boolean isConcrete(Class<?> c) {
        int m = c.getModifiers();
        // 인터페이스,어노테이션,enum,추상클래스 제외
        return !c.isInterface() && !c.isAnnotation() && !c.isEnum() && !Modifier.isAbstract(m);
    }
}
