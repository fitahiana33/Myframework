package com.nandrianina.framework;

import com.nandrianina.framework.mapping.Mapping;
import com.nandrianina.framework.modelView.ModelView;
import com.nandrianina.framework.util.*;
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
        Mapping mapping = findMapping(path, urlMappings);

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

    private Mapping findMapping(String requestPath, Map<String, Mapping> urlMappings) {
        // 1. Recherche exacte d'abord
        if (urlMappings.containsKey(requestPath)) {
            Mapping m = urlMappings.get(requestPath);
            m.setOriginalUrl(requestPath); // on garde l'URL exacte
            return m;
        }

        // 2. Recherche avec paramètres {var}
        for (Map.Entry<String, Mapping> entry : urlMappings.entrySet()) {
            String mappedUrl = entry.getKey();
            java.util.regex.Pattern pattern = PatternCache.getPattern(mappedUrl);
            java.util.regex.Matcher matcher = pattern.matcher(requestPath);

            if (matcher.matches()) {
                Mapping m = entry.getValue();
                m.setOriginalUrl(mappedUrl); // on garde le pattern original
                return m;
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

            // Préparer les arguments à partir des paramètres d'URL (par ordre)
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];

            if (parameters.length > 0 && mapping.getOriginalUrl() != null) {
                String patternUrl = mapping.getOriginalUrl();
                Pattern pattern = PatternCache.getPattern(patternUrl);
                Matcher matcher = pattern.matcher(url);

                if (matcher.matches()) {
                    String[] patternParts = patternUrl.split("/");
                    String[] pathParts = url.split("/");

                    int paramIndex = 0; // indice dans les paramètres de la méthode

                    for (int i = 0; i < patternParts.length; i++) {
                        if (patternParts[i].matches("\\{.+\\}")) {
                            if (paramIndex < parameters.length) {
                                String paramValue = (i < pathParts.length) ? pathParts[i] : "";
                                args[paramIndex] = paramValue; // on remplit dans l'ordre
                                paramIndex++;
                            }
                        }
                    }
                }
            }

            Object result = method.invoke(controllerInstance, args);
            
            handleResult(result, request, response);
            
        } catch (Exception e) {
            response.setContentType("text/plain; charset=UTF-8");
            PrintWriter out = response.getWriter();
            out.println("Erreur lors de l'exécution:");
            out.println("URL: " + url);
            out.println("Contrôleur: " + mapping.getClassName());
            out.println("Méthode: " + mapping.getMethodName());
            out.println("Erreur: " + e.getMessage());
            e.printStackTrace();
        }
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
