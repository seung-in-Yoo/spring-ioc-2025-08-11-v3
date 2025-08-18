package com.ll.framework.ioc;

import com.ll.standard.util.Ut;

public class ApplicationContext {
    private final BeanScanner beanScanner;

    public ApplicationContext(String basePackage) {
        beanScanner =  new BeanScanner(basePackage);
    }

    public void init() {
        beanScanner.init();
    }

    public <T> T genBean(String beanName) {
        try {
            return (T) beanScanner.genBean(Ut.str.lcfirst(beanName));
        } catch (Exception e) {
            System.out.println("빈 생성/불러오기에 실패하였습니다. : "+e.getMessage());
            return null;
        }
    }
}
