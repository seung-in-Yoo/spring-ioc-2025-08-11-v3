package com.ll.global.testJackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Configuration;
import com.ll.framework.ioc.annotations.Primary;

@Configuration
public class TestJacksonConfig {
    @Bean
    public JavaTimeModule testBaseJavaTimeModule() {
        return new JavaTimeModule();
    }

    @Bean
    public ObjectMapper testBaseObjectMapper(JavaTimeModule testBaseJavaTimeModule) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(testBaseJavaTimeModule);
        return objectMapper;
    }

    @Bean
    @Primary
    public JavaTimeModule testBaseJavaTimeModule2() {
        return new JavaTimeModule();
    }

    @Bean
    public JavaTimeModule testParamJavaTimeModule(JavaTimeModule testBaseJavaTimeModule) {
        return testBaseJavaTimeModule;
    }
}