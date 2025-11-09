package com.nandrianina.framework.mapping;

/**
 * Classe qui stocke le mapping entre une URL et une méthode d'un contrôleur
 */
public class Mapping {
    private String className;      // Nom complet de la classe (ex: com.example.controllers.UserController)
    private String methodName;     // Nom de la méthode (ex: listUsers)
    
    public Mapping() {
    }
    
    public Mapping(String className, String methodName) {
        this.className = className;
        this.methodName = methodName;
    }
    
    // Getters et Setters
    public String getClassName() {
        return className;
    }
    
    public void setClassName(String className) {
        this.className = className;
    }
    
    public String getMethodName() {
        return methodName;
    }
    
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
    
    @Override
    public String toString() {
        return "Mapping{" +
                "className='" + className + '\'' +
                ", methodName='" + methodName + '\'' +
                '}';
    }
}
