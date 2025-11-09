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

/**
 * Classe utilitaire pour scanner les packages et trouver les contrôleurs annotés
 */
public class PackageScanner {
    
    /**
     * Scanner un package et retourner un Map<URL, Mapping>
     * @param packageName Le nom du package à scanner (ex: "com.example.controllers")
     * @return Map avec les URLs comme clés et les Mappings comme valeurs
     */
    public static Map<String, Mapping> scanControllers(String packageName) throws Exception {
        Map<String, Mapping> urlMappings = new HashMap<>();
        
        // Convertir le package en chemin (com.example -> com/example)
        String path = packageName.replace('.', '/');
        
        // Obtenir le ClassLoader
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(path);
        
        if (resource == null) {
            throw new Exception("Package non trouvé : " + packageName);
        }
        
        // Lister tous les fichiers .class dans ce package
        File directory = new File(resource.getFile());
        List<Class<?>> classes = findClasses(directory, packageName);
        
        // Pour chaque classe, vérifier si elle a l'annotation @Controller
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(Controller.class)) {
                // Scanner les méthodes de cette classe
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    // Vérifier si la méthode a l'annotation @Get
                    if (method.isAnnotationPresent(Get.class)) {
                        Get getAnnotation = method.getAnnotation(Get.class);
                        String url = getAnnotation.value();
                        
                        // Créer le mapping
                        Mapping mapping = new Mapping(clazz.getName(), method.getName());
                        urlMappings.put(url, mapping);
                    }
                }
            }
        }
        
        return urlMappings;
    }
    
    /**
     * Scanner un package et retourner un Map avec les contrôleurs et leurs méthodes
     * @param packageName Le nom du package à scanner
     * @return Map avec les noms de classes comme clés et Map<URL, Méthode> comme valeurs
     */
    public static Map<String, Map<String, String>> scanControllerMethods(String packageName) throws Exception {
        Map<String, Map<String, String>> controllerMethods = new HashMap<>();
        
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(path);
        
        if (resource == null) {
            throw new Exception("Package non trouvé : " + packageName);
        }
        
        File directory = new File(resource.getFile());
        List<Class<?>> classes = findClasses(directory, packageName);
        
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(Controller.class)) {
                Map<String, String> methods = new HashMap<>();
                Method[] allMethods = clazz.getDeclaredMethods();
                
                for (Method method : allMethods) {
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
     * Trouver récursivement toutes les classes dans un répertoire
     */
    private static List<Class<?>> findClasses(File directory, String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        
        if (!directory.exists()) {
            return classes;
        }
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // Récursion dans les sous-packages
                    classes.addAll(findClasses(file, packageName + "." + file.getName()));
                } else if (file.getName().endsWith(".class")) {
                    // Charger la classe
                    String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                    classes.add(Class.forName(className));
                }
            }
        }
        
        return classes;
    }
}
