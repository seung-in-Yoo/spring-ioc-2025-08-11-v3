package com.ll.framework.ioc.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.lang.reflect.Method;
import java.util.Objects;


@Getter
@EqualsAndHashCode(of = {"name", "type"}) // 값 동등성은 이름+타입
@ToString(onlyExplicitlyIncluded = true)
public final class BeanDefinitionDto {
    private final String name;
    private final Class<?> type; // 모든 클래스 가능하도록 설정
    private final Class<?> factoryClass; // @Configuration 클래스 타입
    private final Method factoryMethod;  // @Bean이 붙은 메서드

    private BeanDefinitionDto(String name, Class<?> type, Class<?> factoryClass, Method factoryMethod) {
        this.name = name;
        this.type = type;
        this.factoryClass = factoryClass;
        this.factoryMethod = factoryMethod;
    }

    // 컴포넌트 기반 정의
    public static BeanDefinitionDto ofClass(String name, Class<?> type) {
        String trimmed = Objects.requireNonNull(name, "name").trim(); // 공백 제거

        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("DTO 이름은 빈칸일 수 없습니다.");
        }

        Objects.requireNonNull(type, "type");
        return new BeanDefinitionDto(trimmed, type, null, null);
    }

    // @Bean 팩토리 메서드 정의
    public static BeanDefinitionDto ofBeanMethod(String name, Class<?> returnType, Class<?> factoryClass, Method factoryMethod) {
        String trimmed = Objects.requireNonNull(name, "name").trim();

        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("DTO 이름은 빈칸일 수 없습니다.");
        }

        Objects.requireNonNull(returnType, "returnType");
        Objects.requireNonNull(factoryClass, "factoryClass");
        Objects.requireNonNull(factoryMethod, "factoryMethod");
        return new BeanDefinitionDto(trimmed, returnType, factoryClass, factoryMethod);
    }

    // @Bean 메서드 기반인지 여부를 파악하는 boolean 메서드
    public boolean isFactoryMethod() {
        return factoryClass != null && factoryMethod != null;
    }

    @ToString.Include(name = "이름")
    String includeName() { return name; }

    @ToString.Include(name = "타입")
    String includeType() { return type.getName(); }
}
