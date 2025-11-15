package com.nandrianina.framework;

import com.nandrianina.framework.mapping.Mapping;
import com.nandrianina.framework.util.PackageScanner;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.Map;

public class FrontServlet extends HttpServlet {

    /**
     * Initialisation du servlet au démarrage
     */
    @Override
    public void init() throws ServletException {
        scan();
    }

    /**
     * Traitement des requêtes GET
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Traitement des requêtes POST
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }

    // ========== TRAITEMENT DES REQUÊTES ==========

    /**
     * Traite la requête HTTP
     */
    private void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String relativePath = getRelativePath(request);
        
        // Vérifier si c'est un fichier statique
        if (isStaticFile(relativePath, request, response)) {
            return;
        }
        
        // Récupérer les mappings
        Map<String, Mapping> urlMappings = getMappings();
        Map<String, Map<String, String>> controllerMethods = getControllerMethods();
        
        // Vérifier si c'est une URL de méthode
        if (urlMappings != null && urlMappings.containsKey(relativePath)) {
            processMethodMapping(relativePath, urlMappings.get(relativePath), request, response);
        } 
        // Vérifier si c'est une URL de contrôleur (afficher toutes ses méthodes)
        else if (controllerMethods != null) {
            boolean found = displayControllerInfo(relativePath, controllerMethods, response);
            if (!found) {
                display404Error(relativePath, response);
            }
        } 
        else {
            display404Error(relativePath, response);
        }
    }

    // ========== MÉTHODES DE SCAN ==========

    /**
     * Scanne les contrôleurs et stocke les informations
     */
    private void scan() throws ServletException {
        try {
            String packageToScan = getPackageToScan();
            Map<String, Mapping> urlMappings = scanControllers(packageToScan);
            Map<String, Map<String, String>> controllerMethods = scanControllerMethods(packageToScan);
            storeMappings(urlMappings);
            storeControllerMethods(controllerMethods);
            displayMappings(urlMappings, packageToScan);
        } catch (Exception e) {
            e.printStackTrace();
            throw new ServletException("Erreur lors du scan des contrôleurs", e);
        }
    }

    /**
     * Récupère le package à scanner depuis web.xml
     */
    private String getPackageToScan() throws ServletException {
        String packageToScan = getInitParameter("controllers-package");
        if (packageToScan == null || packageToScan.isEmpty()) {
            throw new ServletException("Le paramètre 'controllers-package' est requis dans web.xml");
        }
        return packageToScan;
    }

    /**
     * Scanne les contrôleurs du package
     */
    private Map<String, Mapping> scanControllers(String packageName) throws Exception {
        System.out.println("Scanning package: " + packageName);
        return PackageScanner.scanControllers(packageName);
    }

    /**
     * Scanne les méthodes de chaque contrôleur
     */
    private Map<String, Map<String, String>> scanControllerMethods(String packageName) throws Exception {
        return PackageScanner.scanControllerMethods(packageName);
    }

    /**
     * Stocke les mappings dans le ServletContext
     */
    private void storeMappings(Map<String, Mapping> urlMappings) {
        getServletContext().setAttribute("urlMappings", urlMappings);
    }

    /**
     * Stocke les méthodes des contrôleurs dans le ServletContext
     */
    private void storeControllerMethods(Map<String, Map<String, String>> controllerMethods) {
        getServletContext().setAttribute("controllerMethods", controllerMethods);
    }

    /**
     * Affiche les mappings trouvés dans la console
     */
    private void displayMappings(Map<String, Mapping> urlMappings, String packageName) {
        System.out.println("Mappings trouvés pour le package " + packageName + ":");
        for (Map.Entry<String, Mapping> entry : urlMappings.entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }
    }

    // ========== MÉTHODES DE TRAITEMENT DES REQUÊTES ==========

    /**
     * Extrait le chemin relatif de la requête
     */
    private String getRelativePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return uri.substring(contextPath.length());
    }

    /**
     * Récupère les mappings depuis le ServletContext
     */
    @SuppressWarnings("unchecked")
    private Map<String, Mapping> getMappings() {
        return (Map<String, Mapping>) getServletContext().getAttribute("urlMappings");
    }

    /**
     * Récupère les méthodes des contrôleurs depuis le ServletContext
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, String>> getControllerMethods() {
        return (Map<String, Map<String, String>>) getServletContext().getAttribute("controllerMethods");
    }

    /**
     * Vérifie si c'est un fichier statique et le forward si nécessaire
     */
    private boolean isStaticFile(String relativePath, HttpServletRequest request,
                                   HttpServletResponse response) throws ServletException, IOException {
        String realPath = getServletContext().getRealPath(relativePath);
        if (realPath != null && (realPath.endsWith(".html") || realPath.endsWith(".jsp"))) {
            File file = new File(realPath);
            if (file.exists() && file.isFile()) {
                RequestDispatcher dispatcher = request.getRequestDispatcher(relativePath);
                dispatcher.forward(request, response);
                return true;
            }
        }
        return false;
    }

    /**
     * Traite un mapping trouvé : exécute la méthode du contrôleur
     */
    private void processMethodMapping(String url, Mapping mapping,
                                      HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            // Invoquer la méthode du contrôleur
            Object result = invokeMethod(mapping);

            // Vérifier le type de retour et agir en conséquence
            if (result instanceof String) {
                displayStringResult(url, mapping, (String) result, response);
            } else {
                dispatchResult(url, mapping, result, request, response);
            }
        } catch (Exception e) {
            displayError(url, mapping, e, response);
        }
    }

    /**
     * Affiche les informations d'un contrôleur complet
     * @return true si le contrôleur a été trouvé, false sinon
     */
    private boolean displayControllerInfo(String url, Map<String, Map<String, String>> controllerMethods,
                                          HttpServletResponse response) throws IOException {
        // Chercher un contrôleur qui correspond à l'URL
        for (Map.Entry<String, Map<String, String>> entry : controllerMethods.entrySet()) {
            String className = entry.getKey();
            String simpleClassName = className.substring(className.lastIndexOf('.') + 1);

            // Vérifier si l'URL correspond au nom de la classe (ex: /Exemple pour Exemple.java)
            if (url.equalsIgnoreCase("/" + simpleClassName)) {
                response.setContentType("text/plain; charset=UTF-8");
                PrintWriter out = response.getWriter();

                out.println("URL: " + url);
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

    // ========== MÉTHODES D'INVOCATION ==========

    /**
     * Invoque la méthode du contrôleur via réflexion
     */
    private Object invokeMethod(Mapping mapping) throws Exception {
        Class<?> controllerClass = Class.forName(mapping.getClassName());
        Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();
        Method method = controllerClass.getMethod(mapping.getMethodName());
        return method.invoke(controllerInstance);
    }

    // ========== MÉTHODES D'AFFICHAGE ==========

    /**
     * Affiche le résultat String via PrintWriter
     */
    private void displayStringResult(String url, Mapping mapping, String result,
                                    HttpServletResponse response) throws IOException {
        response.setContentType("text/plain; charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.println(result);
    }

    /**
     * Dispatch le résultat vers une vue (JSP)
     */
    private void dispatchResult(String url, Mapping mapping, Object result,
                               HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Si le résultat n'est pas String, on pourrait dispatcher vers une JSP
        // Pour l'instant, on affiche juste l'objet
        response.setContentType("text/plain; charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.println("Type de retour non-String:");
        out.println(result != null ? result.toString() : "null");
    }

    /**
     * Affiche une erreur lors de l'exécution
     */
    private void displayError(String url, Mapping mapping, Exception e,
                             HttpServletResponse response) throws IOException {
        response.setContentType("text/plain; charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.println("Erreur lors de l'exécution:");
        out.println("URL: " + url);
        out.println("Contrôleur: " + mapping.getClassName());
        out.println("Méthode: " + mapping.getMethodName());
        out.println("Erreur: " + e.getMessage());
        e.printStackTrace();
    }

    /**
     * Affiche l'erreur 404
     */
    private void display404Error(String url, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType("text/plain; charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.println("Erreur 404 : Page non trouvée");
        out.println("URL: " + url);
    }
}
