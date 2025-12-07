package com.nandrianina.framework;

import com.nandrianina.framework.mapping.Mapping;
import com.nandrianina.framework.modelView.ModelView;
import com.nandrianina.framework.util.*;
import com.nandrianina.framework.annotation.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class FrontServlet extends HttpServlet {

    @Override
    public void init() throws ServletException {
        try {
            scanner();
        } catch (Exception e) {
            throw new ServletException("Erreur lors de l'initialisation du framework", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    private void scanner() throws Exception{
        try {
            // Récupérer le package à scanner
            String packageToScan = getInitParameter("controllers-package");
            if (packageToScan == null || packageToScan.isEmpty()) {
                throw new ServletException("Le paramètre 'controllers-package' est requis dans web.xml");
            }
            
            // Scanner et stocker les mappings
            Map<String, Mapping> urlMappings = PackageScanner.scanControllers(packageToScan);
            Map<String, Map<String, String>> controllerMethods = PackageScanner.scanControllerMethods(packageToScan);
            
            getServletContext().setAttribute("urlMappings", urlMappings);
            getServletContext().setAttribute("controllerMethods", controllerMethods);
            
            System.out.println("Framework initialisé - " + urlMappings.size() + " mappings trouvés");
            // Affichage détaillé de chaque mapping
            for (Map.Entry<String, Mapping> entry : urlMappings.entrySet()) {
                String url = entry.getKey();
                Mapping mapping = entry.getValue();
                
                System.out.println("URL : " + url 
                    + " -> " + mapping.getClassName() 
                    + "." + mapping.getMethodName() 
                    //+ (mapping.getHttpMethod() != null ? " (" + mapping.getHttpMethod() + ")" : "")
                    );
            }
        } catch (Exception e) {
            throw new ServletException("Erreur lors de l'initialisation du framework", e);
        }        
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = uri.substring(contextPath.length());
        String requestMethod = request.getMethod(); // "GET" ou "POST"
        
        // Si c'est un fichier .jsp qui existe, utiliser la chaîne de filtres par défaut
        if (path.endsWith(".jsp")) {
            String realPath = getServletContext().getRealPath(path);
            if (realPath != null && new java.io.File(realPath).exists()) {
                // Utiliser la chaîne par défaut (pas de traitement par FrontServlet)
                request.getRequestDispatcher(path).include(request, response);
                return;
            }
        }
        
        Map<String, Mapping> urlMappings = (Map<String, Mapping>) getServletContext().getAttribute("urlMappings");

        // RECHERCHE D'UN MAPPING (exact ou avec paramètre)
        Mapping mapping = findMapping(path, request.getMethod(), urlMappings);

        if (mapping != null) {
            // Extraire les paramètres et les mettre dans la request
            extractParameters(path, mapping.getOriginalUrl(), request);
            invokeControllerMethod(path, mapping, request, response);
            return;
        }
        
        // 3. Erreur 404
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/plain; charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.println("Erreur 404 : Page non trouvée");
        out.println("URL: " + path);
    }

private Mapping findMapping(String requestPath, String requestMethod, Map<String, Mapping> urlMappings) {
    // 1. Recherche exacte
    String exactKey = requestPath + "|||" + requestMethod;
    Mapping mapping = urlMappings.get(exactKey);
    if (mapping != null && mapping.getHttpMethod().equalsIgnoreCase(requestMethod)) {
        return mapping;
    }

    // 2. Recherche avec paramètres {id}
    for (Map.Entry<String, Mapping> entry : urlMappings.entrySet()) {
        String key = entry.getKey();
        String[] parts = key.split("\\|\\|\\|");
        String mappedUrl = parts[0];
        String mappedMethod = parts[1];

        if (!mappedMethod.equalsIgnoreCase(requestMethod)) {
            continue;
        }

        Pattern pattern = PatternCache.getPattern(mappedUrl);
        Matcher matcher = pattern.matcher(requestPath);

        if (matcher.matches()) {
            mapping = entry.getValue();
            mapping.setOriginalUrl(mappedUrl);
            return mapping;
        }
    }
    return null;
}

    private void extractParameters(String requestPath, String mappedUrl, HttpServletRequest request) {
        java.util.regex.Pattern pattern = PatternCache.getPattern(mappedUrl);
        java.util.regex.Matcher matcher = pattern.matcher(requestPath);

        if (matcher.matches()) {
            String[] pathParts = requestPath.split("/");
            String[] patternParts = mappedUrl.split("/");

            for (int i = 0; i < patternParts.length; i++) {
                if (patternParts[i].startsWith("{") && patternParts[i].endsWith("}")) {
                    String paramName = patternParts[i].substring(1, patternParts[i].length() - 1);
                    String paramValue = i < pathParts.length ? pathParts[i] : "";
                    request.setAttribute(paramName, paramValue);
                }
            }
        }
    }

    private void invokeControllerMethod(String url, Mapping mapping,
                                       HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            Class<?> controllerClass = Class.forName(mapping.getClassName());
            Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
            
            // Trouver la méthode par son nom (marche même si elle a des paramètres)
            Method method = null;
            for (Method m : controllerClass.getDeclaredMethods()) {
                if (m.getName().equals(mapping.getMethodName())) {
                    method = m;
                    break;
                }
            }
            if (method == null) {
                throw new RuntimeException("Méthode non trouvée : " + mapping.getMethodName());
            }

            // Préparer les arguments (support @RequestParam + paramètres d'URL par ordre)
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];

            // Extraire les valeurs des paramètres d'URL (ex: /user/123 → id = "123")
            java.util.List<String> pathVariables = extractPathVariables(url, mapping.getOriginalUrl());

            int pathVarIndex = 0;
            java.util.List<String> queryValuesInOrder = null;
            int queryIndex = 0;

            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];
                Object value = null;

                // 1. Cas @RequestParam → on cherche dans request.getParameter()
                if (param.isAnnotationPresent(RequestParam.class)) {
                    RequestParam rp = param.getAnnotation(RequestParam.class);
                    String expectedName = rp.value();
                    String paramValue = request.getParameter(expectedName);  // 1. essai exact

                    // 2. SI pas trouvé → fallback : on prend par ordre
                    if (paramValue == null) {
                        // On récupère tous les paramètres dans l'ordre d'apparition
                        if (queryValuesInOrder == null) {
                            queryValuesInOrder = new java.util.ArrayList<>();
                            java.util.LinkedHashMap<String, String[]> map = 
                                new java.util.LinkedHashMap<>(request.getParameterMap());
                            for (String[] vals : map.values()) {
                                if (vals != null && vals.length > 0) {
                                    queryValuesInOrder.add(vals[0]);
                                }
                            }
                        }
                        if (queryIndex < queryValuesInOrder.size()) {
                            paramValue = queryValuesInOrder.get(queryIndex++);
                        }
                    }

                    // Conversion finale
                    if (paramValue != null && !paramValue.trim().isEmpty()) {
                        value = convert(paramValue, param.getType());
                    } else if (param.getType().isPrimitive() && rp.required()) {
                        throw new IllegalArgumentException("Paramètre requis manquant : " + expectedName);
                    }
                }
                // 2. Sinon → injection par ordre des {id} dans l'URL
                else if (pathVarIndex < pathVariables.size()) {
                    String pathValue = pathVariables.get(pathVarIndex);
                    value = convert(pathValue, param.getType());
                    pathVarIndex++;
                }
                // 3. Sinon → paramètre sans annotation : on essaye par le nom Java (fallback)
                else {
                    String paramName = param.getName();
                    String paramValue = request.getParameter(paramName);
                    if (paramValue != null && paramValue.trim().length() > 0) {
                        value = convert(paramValue, param.getType());
                    }
                    // sinon value reste null → OK si type non primitif
                }

                args[i] = value;
            }

            Object result = method.invoke(controllerInstance, args);
            
            handleResult(result, request, response);
            
        } catch (Exception e) {
            response.setContentType("text/plain; charset=UTF-8");
            PrintWriter out = response.getWriter();
            out.println("Erreur lors de l'invocation du methode :");
            out.println("URL: " + url);
            out.println("Contrôleur: " + mapping.getClassName());
            out.println("Méthode: " + mapping.getMethodName());
            out.println("Erreur: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Extrait les valeurs des parties variables de l'URL (ex: /Ex/sprint6/123 → ["123"])
    private java.util.List<String> extractPathVariables(String requestPath, String patternUrl) {
        java.util.List<String> values = new java.util.ArrayList<>();
        if (patternUrl == null) return values;

        String[] patternParts = patternUrl.split("/");
        String[] pathParts = requestPath.split("/");

        for (int i = 0; i < patternParts.length; i++) {
            if (i < patternParts.length && patternParts[i].matches("\\{.+\\}")) {
                if (i < pathParts.length) {
                    values.add(pathParts[i]);
                }
            }
        }
        return values;
    }

    // Convertit une String en int, Integer, etc. (ou retourne String si inconnu)
    private Object convert(String value, Class<?> targetType) {
        if (value == null) return null;
    
        try {
            if (targetType == int.class || targetType == Integer.class) {
                return Integer.parseInt(value);
            }
            if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(value);
            }
            // ... autres types
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Paramètre invalide : '" + value + "' n'est pas un " + targetType.getSimpleName());
        }
        return value;
    }

    private void handleResult(Object result, HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        if (result == null) {
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().println("null");
            return;
        }
        
        if (result instanceof String) {
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().println((String) result);
            return;
        }
        
        if (result instanceof ModelView) {
            ModelView mv = (ModelView) result;
            String view = mv.getView();
            if (!view.startsWith("/")) {
                view = "/" + view;
            }
            // Ajouter toutes les données du ModelView dans la request
            for (String key : mv.getData().keySet()) {
                request.setAttribute(key, mv.getData().get(key));
            }
            // Forward vers la JSP
            RequestDispatcher dispatcher = request.getRequestDispatcher(view);
            dispatcher.forward(request, response);
            return;
        }
        
        response.setContentType("text/plain; charset=UTF-8");
        response.getWriter().println("Type de retour: " + result.getClass().getName());
        response.getWriter().println("Valeur: " + result.toString());
    }

    private boolean displayControllerInfo(String path, Map<String, Map<String, String>> controllerMethods,
                                         HttpServletResponse response) throws IOException {
        for (Map.Entry<String, Map<String, String>> entry : controllerMethods.entrySet()) {
            String className = entry.getKey();
            String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
            
            if (path.equalsIgnoreCase("/" + simpleClassName)) {
                response.setContentType("text/plain; charset=UTF-8");
                PrintWriter out = response.getWriter();
                out.println("URL: " + path);
                out.println("Contrôleur: " + className);
                out.println("Méthodes annotées:");
                
                Map<String, String> methods = entry.getValue();
                if (methods.isEmpty()) {
                    out.println("  Aucune méthode annotée avec @Get");
                } else {
                    for (Map.Entry<String, String> methodEntry : methods.entrySet()) {
                        out.println("  " + methodEntry.getKey() + " -> " + methodEntry.getValue() + "()");
                    }
                }
                return true;
            }
        }
        return false;
    }
}
