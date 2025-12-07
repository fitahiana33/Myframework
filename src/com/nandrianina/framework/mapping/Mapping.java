package com.nandrianina.framework.mapping;

public class Mapping {
    private String className;
    private String methodName;
    private String originalUrl;
    private String httpMethod;  // GET ou POST

    public Mapping(String className, String methodName, String httpMethod) {
        this.className = className;
        this.methodName = methodName;
        this.httpMethod = httpMethod;
    }

    // getters + setters
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getClassName() { return className; }

    public void setClassName(String className) { this.className = className; }

    public String getMethodName() { return methodName; }

    public void setMethodName(String methodName) { this.methodName = methodName; }


    public String getOriginalUrl() { return originalUrl; }

    public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }

    @Override
    public String toString() { return className + "." + methodName + "()"; }
}
