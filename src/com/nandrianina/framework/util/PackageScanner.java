package com.nandrianina.framework.util;

import com.nandrianina.framework.annotation.Controller;
import com.nandrianina.framework.annotation.Get;
import com.nandrianina.framework.mapping.Mapping;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PackageScanner {

    /**
     * Scanne un package et retourne un Map des URLs vers leurs Mapping
     */
    public static Map<String, Mapping> scanControllers(String packageName) throws Exception {
        Map<String, Mapping> urlMappings = new HashMap<>();
        List<Class<?>> classes = findClasses(packageName);

        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(Controller.class)) {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Get.class)) {
                        Get getAnnotation = method.getAnnotation(Get.class);
                        String url = getAnnotation.value();
                        Mapping mapping = new Mapping(clazz.getName(), method.getName());
                        urlMappings.put(url, mapping);
                    }
                }
            }
        }

        return urlMappings;
    }

    /**
     * Scanne un package et retourne un Map des contrôleurs vers leurs méthodes
     */
    public static Map<String, Map<String, String>> scanControllerMethods(String packageName) throws Exception {
        Map<String, Map<String, String>> controllerMethods = new HashMap<>();
        List<Class<?>> classes = findClasses(packageName);

        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(Controller.class)) {
                Map<String, String> methods = new HashMap<>();
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Get.class)) {
                        Get getAnnotation = method.getAnnotation(Get.class);
                        String url = getAnnotation.value();
                        methods.put(url, method.getName());
                    }
                }
                controllerMethods.put(clazz.getName(), methods);
            }
        }

        return controllerMethods;
    }

    /**
     * Trouve toutes les classes dans un package
     */
    private static List<Class<?>> findClasses(String packageName) throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String path = packageName.replace('.', '/');
        URL resource = classLoader.getResource(path);

        if (resource == null) {
            throw new Exception("Package non trouvé: " + packageName);
        }

        File directory = new File(resource.getFile());
        List<Class<?>> classes = new ArrayList<>();

        if (directory.exists()) {
            findClassesRecursive(directory, packageName, classes);
        }

        return classes;
    }

    /**
     * Recherche récursive des classes
     */
    private static void findClassesRecursive(File directory, String packageName, List<Class<?>> classes) throws ClassNotFoundException {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findClassesRecursive(file, packageName + "." + file.getName(), classes);
                } else if (file.getName().endsWith(".class")) {
                    String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                    classes.add(Class.forName(className));
                }
            }
        }
    }
}
