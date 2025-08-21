package com.ll.framework.ioc;

import com.ll.framework.ioc.dto.BeanDefinitionDto;
import com.ll.framework.ioc.scanner.ClassPathBeanScanner;
import lombok.ToString;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
// import static com.ll.standard.util.Ut.str.lcfirst; => 원래 import 해도 되지만 밑에 메서드로 만들었기에 삭제

@ToString(onlyExplicitlyIncluded = true)
public class ApplicationContext {

    private final String[] basePackages;
    private final Map<String, BeanDefinitionDto> definitions = new LinkedHashMap<>(); // 스캔할 결과를 담을 테이블
    private final Map<String, Object> singletons = new ConcurrentHashMap<>(); // 싱클톤 캐시

    public ApplicationContext(String... basePackages) {
        this.basePackages = (basePackages == null || basePackages.length == 0) ? new String[]{"com.ll"} : basePackages;
    }

    @ToString.Include(name = "packages")
    String packagesView() { return Arrays.toString(basePackages); }

    @ToString.Include(name = "definitions")
    Set<String> definitionsView() { return definitions.keySet(); }

    @ToString.Include(name = "singletons")
    Set<String> singletonsView() { return singletons.keySet(); }

    // 스캔 및 구성 (초기화)
    public void init() {
        ClassPathBeanScanner scanner = new ClassPathBeanScanner(basePackages);
        Map<String, BeanDefinitionDto> scanned = scanner.scan();

        if (scanned.isEmpty()) {
            throw new IllegalStateException("스캔 결과가 비어 있습니다. basePackages=" + Arrays.toString(basePackages));
        }
        definitions.clear();
        definitions.putAll(scanned);
    }

    // 이름으로 빈 조회 (Lazy 생성 + 싱글톤 보장)
    @SuppressWarnings("unchecked")
    public <T> T genBean(String beanName) {
        BeanDefinitionDto def = definitions.get(beanName); // 이름 기반으로 조회 (빈 정의 확인)
        if (def == null) {
            throw new IllegalArgumentException("빈 이름이 존재하지 않습니다: " + beanName);
        }
        return (T) singletons.computeIfAbsent(beanName, n -> create(def)); // 최초 1회는 생성하고 그 뒤로는 캐시로
    }

    // 생성, 생성자 주입 관련
    private Object create(BeanDefinitionDto def) {
        return def.isFactoryMethod() ? createByFactoryMethod(def) : createByConstructor(def);
    }

    // @Bean 팩토리 메서드 기반 생성 => (v3에서 추가된 로직)
    private Object createByFactoryMethod(BeanDefinitionDto def) {
        Class<?> configurationClass = def.getFactoryClass();
        Method method = def.getFactoryMethod();

        // 팩토리 인스턴스
        String configurationBeanName = beanNameOf(configurationClass); // lcfirst 호출 관련 메서드로 사용
        Object configurationInstance = genBean(configurationBeanName ); // @Configuration 빈으로 관리

        // 메서드 파라미터 타입 기반 의존성
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            args[i] = resolveByType(paramTypes[i]);
        }

        // 팩토리 메서드 호출
        try {
            method.setAccessible(true);
            return method.invoke(configurationInstance, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("@Bean 메서드로 빈 생성에 실패했습니다: " + def.getName(), e);
        }
    }

    // 클래스 생성자 기반 생성
    private Object createByConstructor(BeanDefinitionDto def) {
        Class<?> type = def.getType();
        Constructor<?> constructor = selectConstructor(type);

        Class<?>[] paramTypes = constructor.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            args[i] = resolveByType(paramTypes[i]);
        }

        try {
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("빈 생성에 실패하였습니다. " + def.getName());
        }
    }

    // 후보 생성자 중에서 선택할 생성자 고르는 과정 (우선순위 기반)
    private Constructor<?> selectConstructor(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount)) // 파라미터 개수 가장 많은 것 선택
                .orElseThrow(() -> new IllegalStateException("생성자를 찾을 수 없습니다: " + type.getName()));
    }

    // 컨테이너에 등록된 빈들 중 주입할 단 하나를 고르는 과정
    private Object resolveByType(Class<?> requiredType) {
        List<BeanDefinitionDto> candidates = definitions.values().stream()
                .filter(d -> requiredType.isAssignableFrom(d.getType())) // 할당 가능한 타입은 후보로
                .collect(Collectors.toList());

        // 후보 없으면 예외처리 (주입 불가)
        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    String.format("타입 '%s'에 해당하는 빈이 존재하지 않습니다.", requiredType.getName())
            );
        }

        List<BeanDefinitionDto> exact = candidates.stream()
                .filter(d -> d.getType().equals(requiredType)) // 타입이 정확하게 같은걸로 필터
                .collect(Collectors.toList());

        if (exact.size() == 1) { return genBean(exact.get(0).getName()); }

        if (candidates.size() == 1) { return genBean(candidates.get(0).getName()); }

        // 후보가 2개 이상일때 => 실패
        String names = candidates.stream()
                .map(BeanDefinitionDto::getName)
                .collect(Collectors.joining(", "));
        throw new IllegalStateException(
                String.format("타입 '%s'에 해당하는 빈이 둘 이상입니다: %s", requiredType.getName(), names)
        );
    }

    // 유지보수를 위해 lcfirst 호출 관련 메서드 추가
    private static String beanNameOf(Class<?> type) {
        return com.ll.standard.util.Ut.str.lcfirst(type.getSimpleName());
    }
}