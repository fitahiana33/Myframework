package com.nandrianina.framework;

import com.nandrianina.framework.mapping.Mapping;
import com.nandrianina.framework.modelView.ModelView;
import com.nandrianina.framework.util.PackageScanner;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * FrontServlet - Contrôleur frontal du framework MVC
 * Version simplifiée et restructurée
 */
public class FrontServlet extends HttpServlet {

    @Override
    public void init() throws ServletException {
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
        } catch (Exception e) {
            throw new ServletException("Erreur lors de l'initialisation du framework", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
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
        
        @SuppressWarnings("unchecked")
        Map<String, Mapping> urlMappings = (Map<String, Mapping>) getServletContext().getAttribute("urlMappings");
        
        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> controllerMethods = (Map<String, Map<String, String>>) 
            getServletContext().getAttribute("controllerMethods");
        
        // 1. URL mappée à une méthode
        if (urlMappings != null && urlMappings.containsKey(path)) {
            invokeControllerMethod(path, urlMappings.get(path), request, response);
            return;
        }
        
        // 2. URL de contrôleur (afficher les méthodes)
        if (controllerMethods != null && displayControllerInfo(path, controllerMethods, response)) {
            return;
        }
        
        // 3. Erreur 404
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/plain; charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.println("Erreur 404 : Page non trouvée");
        out.println("URL: " + path);
    }

    private void invokeControllerMethod(String url, Mapping mapping,
                                       HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            Class<?> controllerClass = Class.forName(mapping.getClassName());
            Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
            Method method = controllerClass.getMethod(mapping.getMethodName());
            Object result = method.invoke(controllerInstance);
            
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
            // Avec le mapping "/", les JSP ne repassent plus par FrontServlet
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
