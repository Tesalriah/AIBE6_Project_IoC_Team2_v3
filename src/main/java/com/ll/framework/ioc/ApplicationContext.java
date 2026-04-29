package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.*;
import com.ll.standard.util.Ut;

import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;

public class ApplicationContext {

    private final String basePackage;

    // 싱글톤 빈 저장소
    private final Map<String, Object> beanMap = new HashMap<>();

    // 스캔 결과 저장
    private final List<Class<?>> componentClasses = new ArrayList<>();
    private final List<Class<?>> configurationClasses = new ArrayList<>();

    public ApplicationContext(String basePackage) {
        this.basePackage = basePackage;
    }

    public void init() {
        scanPackage(basePackage);

        // 1. 일반 컴포넌트 먼저 생성
        for (Class<?> clazz : componentClasses) {
            createBean(clazz);
        }

        // 2. @Configuration + @Bean 처리
        for (Class<?> configClass : configurationClasses) {
            Object configInstance = createBean(configClass);

            for (Method method : configClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Bean.class)) {
                    try {
                        Object[] args = Arrays.stream(method.getParameterTypes())
                                .map(this::getBeanByType)
                                .toArray();

                        Object bean = method.invoke(configInstance, args);

                        String beanName = method.getName();
                        beanMap.put(beanName, bean);

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    public <T> T genBean(String beanName) {
        return (T) beanMap.get(beanName);
    }

    private void scanPackage(String basePackage) {
        String path = basePackage.replace(".", "/");

        try {
            Enumeration<URL> resources = Thread.currentThread()
                    .getContextClassLoader()
                    .getResources(path);

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                File dir = new File(resource.toURI());

                scanDirectory(basePackage, dir);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void scanDirectory(String packageName, File dir) {
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.isDirectory()) {
                scanDirectory(packageName + "." + file.getName(), file);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." +
                        file.getName().replace(".class", "");

                try {
                    Class<?> clazz = Class.forName(className);

                    if (clazz.isAnnotation()) continue;

                    if (clazz.isAnnotationPresent(Configuration.class)) {
                        configurationClasses.add(clazz);
                    } else if (isComponent(clazz)) {
                        componentClasses.add(clazz);
                    }

                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private boolean isComponent(Class<?> clazz) {
        return clazz.isAnnotationPresent(Component.class)
                || clazz.isAnnotationPresent(Service.class)
                || clazz.isAnnotationPresent(Repository.class);
    }

    private Object createBean(Class<?> clazz) {
        String beanName = Ut.str.lcfirst(clazz.getSimpleName());

        if (beanMap.containsKey(beanName)) {
            return beanMap.get(beanName);
        }

        try {
            Constructor<?>[] constructors = clazz.getDeclaredConstructors();

            Constructor<?> constructor;

            if (constructors.length == 0) {
                constructor = clazz.getDeclaredConstructor();
            } else {
                constructor = constructors[0];
            }

            constructor.setAccessible(true); // ⭐ 중요

            Object[] args = Arrays.stream(constructor.getParameterTypes())
                    .map(this::getBeanByType)
                    .toArray();

            Object instance = constructor.newInstance(args);

            beanMap.put(beanName, instance);

            return instance;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object getBeanByType(Class<?> type) {
        // 이미 생성된 빈에서 찾기
        for (Object bean : beanMap.values()) {
            if (type.isAssignableFrom(bean.getClass())) {
                return bean;
            }
        }

        // 아직 생성 안된 경우 찾아서 생성
        for (Class<?> clazz : componentClasses) {
            if (type.isAssignableFrom(clazz)) {
                return createBean(clazz);
            }
        }

        for (Class<?> clazz : configurationClasses) {
            if (type.isAssignableFrom(clazz)) {
                return createBean(clazz);
            }
        }

        throw new RuntimeException("No bean found for type: " + type);
    }
}