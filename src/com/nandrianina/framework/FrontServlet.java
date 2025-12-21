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
import java.util.*;
import java.util.LinkedHashMap;

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
        if (path.endsWith(".jsp") || path.endsWith(".html")) {
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

            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];

            List<String> pathVariables = extractPathVariables(url, mapping.getOriginalUrl());
            int pathVarIndex = 0;

            Map<String, Object> allParams = buildAllParametersMap(request);

            // Liste ordonnée pour fallback simple
            List<String> orderedValues = new ArrayList<>();
            for (Object v : allParams.values()) {
                if (v instanceof List<?> list && !list.isEmpty()) {
                    orderedValues.add(list.get(0).toString());
                } else if (v != null) {
                    orderedValues.add(v.toString());
                }
            }
            int fallbackIndex = 0;
            boolean fallbackUsedForSimpleObject = false; // Une seule fois pour les objets simples

            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];
                Object value = null;

                // 1. Map globale ou Map<String,Byte[]>
                if (Map.class.isAssignableFrom(param.getType())) {
                    // tenter de détecter Map<String,Byte[]> ou Map<String,byte[]>
                    Type genericType = method.getGenericParameterTypes()[i];
                    boolean producedSpecialMap = false;
                    if (genericType instanceof ParameterizedType pt) {
                        Type[] typeArgs = pt.getActualTypeArguments();
                        if (typeArgs.length == 2) {
                            Type k = typeArgs[0];
                            Type v = typeArgs[1];
                            boolean keyIsString = k instanceof Class && k == String.class;
                            boolean valueIsBoxedByteArray = v instanceof Class && v == Byte[].class;
                            boolean valueIsPrimitiveByteArray = v instanceof Class && v == byte[].class;

                            if (keyIsString && (valueIsBoxedByteArray || valueIsPrimitiveByteArray)) {
                                Map<String, Byte[]> filesMap = new HashMap<>();
                                for (Map.Entry<String, Object> e : allParams.entrySet()) {
                                    Object ov = e.getValue();
                                    if (ov instanceof java.util.Map.Entry) {
                                        Map.Entry<?,?> fe = (Map.Entry<?,?>) ov;
                                        Object fk = fe.getKey(); Object fv = fe.getValue();
                                        if (fv instanceof Byte[]) filesMap.put(String.valueOf(fk), (Byte[]) fv);
                                        else if (fv instanceof byte[]) filesMap.put(String.valueOf(fk), toBoxed((byte[]) fv));
                                    } else if (ov instanceof Byte[]) {
                                        filesMap.put(e.getKey(), (Byte[]) ov);
                                    } else if (ov instanceof byte[]) {
                                        filesMap.put(e.getKey(), toBoxed((byte[]) ov));
                                    } else if (ov instanceof java.util.List) {
                                        for (Object it : (java.util.List) ov) {
                                            if (it instanceof java.util.Map.Entry) {
                                                Map.Entry<?,?> fe = (Map.Entry<?,?>) it;
                                                Object fk = fe.getKey(); Object fv = fe.getValue();
                                                if (fv instanceof Byte[]) filesMap.put(String.valueOf(fk), (Byte[]) fv);
                                                else if (fv instanceof byte[]) filesMap.put(String.valueOf(fk), toBoxed((byte[]) fv));
                                            } else if (it instanceof Byte[]) {
                                                filesMap.put(e.getKey(), (Byte[]) it);
                                            } else if (it instanceof byte[]) {
                                                filesMap.put(e.getKey(), toBoxed((byte[]) it));
                                            }
                                        }
                                    }
                                }
                                value = filesMap;
                                producedSpecialMap = true;
                            }
                        }
                    }
                    if (!producedSpecialMap) {
                        value = allParams;
                    }
                }
                // Support pour Map.Entry<String, Byte[]> : on cherche d'abord des Map.Entry stockées
                else if (java.util.Map.Entry.class.isAssignableFrom(param.getType())) {
                    for (Map.Entry<String, Object> e : allParams.entrySet()) {
                        Object v = e.getValue();
                        if (v instanceof java.util.Map.Entry) {
                            value = v;
                            break;
                        } else if (v instanceof java.util.List) {
                            java.util.List lst = (java.util.List) v;
                            if (!lst.isEmpty()) {
                                Object first = lst.get(0);
                                if (first instanceof java.util.Map.Entry) {
                                    value = first;
                                    break;
                                } else if (first instanceof Byte[]) {
                                    value = new java.util.AbstractMap.SimpleEntry<String, Byte[]>((String) e.getKey(), (Byte[]) first);
                                    break;
                                }
                            }
                        } else if (v instanceof Byte[]) {
                            value = new java.util.AbstractMap.SimpleEntry<String, Byte[]>((String) e.getKey(), (Byte[]) v);
                            break;
                        }
                    }
                }
                // 2. @RequestParam
                else if (param.isAnnotationPresent(RequestParam.class)) {
                    RequestParam rp = param.getAnnotation(RequestParam.class);
                    String name = rp.value();
                    String[] values = request.getParameterValues(name);

                    if (values != null && values.length > 0) {
                        if (values.length == 1) {
                            value = convert(values[0], param.getType());
                        } else {
                            if (param.getType() == String[].class) {
                                value = values;
                            } else if (List.class.isAssignableFrom(param.getType())) {
                                value = Arrays.asList(values);
                            } else {
                                value = convert(values[0], param.getType());
                            }
                        }
                    } else if (fallbackIndex < orderedValues.size()) {
                        value = convert(orderedValues.get(fallbackIndex++), param.getType());
                    }
                }
                // 3. Path variable
                else if (pathVarIndex < pathVariables.size()) {
                    value = convert(pathVariables.get(pathVarIndex++), param.getType());
                }
                // 4. LISTE OU TABLEAU D'OBJETS (List<User>, User[])
                else if (List.class.isAssignableFrom(param.getType()) || param.getType().isArray()) {
                    Class<?> elementType;
                    if (param.getType().isArray()) {
                        elementType = param.getType().getComponentType();
                    } else {
                        Type genericType = method.getGenericParameterTypes()[i];
                        if (genericType instanceof ParameterizedType pt) {
                            elementType = (Class<?>) pt.getActualTypeArguments()[0];
                        } else {
                            elementType = Object.class;
                        }
                    }

                    // Types simples → fallback normal
                    if (elementType.isPrimitive() || elementType.getName().startsWith("java.lang.")) {
                        if (fallbackIndex < orderedValues.size()) {
                            value = convert(orderedValues.get(fallbackIndex++), param.getType());
                        }
                        continue;
                    }

                    // LISTE D'OBJETS PERSONNALISÉS
                    // Support spécial pour List<Map.Entry<String,Byte[]>> ou Map.Entry[]
                    if (java.util.Map.Entry.class.isAssignableFrom(elementType)) {
                        List<Object> flist = new ArrayList<>();
                        for (Map.Entry<String, Object> e : allParams.entrySet()) {
                            Object v = e.getValue();
                            if (v instanceof java.util.Map.Entry) {
                                flist.add(v);
                            } else if (v instanceof Byte[]) {
                                flist.add(new java.util.AbstractMap.SimpleEntry<String, Byte[]>((String) e.getKey(), (Byte[]) v));
                            } else if (v instanceof java.util.List) {
                                for (Object it : (java.util.List) v) {
                                    if (it instanceof java.util.Map.Entry) {
                                        flist.add(it);
                                    } else if (it instanceof Byte[]) {
                                        flist.add(new java.util.AbstractMap.SimpleEntry<String, Byte[]>((String) e.getKey(), (Byte[]) it));
                                    }
                                }
                            }
                        }

                        if (param.getType().isArray()) {
                            Object[] array = (Object[]) java.lang.reflect.Array.newInstance(elementType, flist.size());
                            value = flist.toArray(array);
                        } else {
                            value = flist;
                        }
                        // assign later
                        // continue with normal flow
                    } else {
                        List<Object> list = new ArrayList<>();
                    int index = 0;

                    while (true) {
                        boolean hasData = false;
                        Object obj;
                        try {
                            obj = elementType.getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            break;
                        }

                        Field[] fields = elementType.getDeclaredFields();
                        for (Field field : fields) {
                            String key = field.getName() + "[" + index + "]";
                            if (allParams.containsKey(key)) {
                                Object raw = allParams.get(key);
                                String str = raw instanceof List<?> l && !l.isEmpty() ? l.get(0).toString() : raw.toString();
                                field.setAccessible(true);
                                try {
                                    field.set(obj, convert(str, field.getType()));
                                } catch (Exception ignored) {}
                                hasData = true;
                            }
                        }

                        if (!hasData) break;
                        list.add(obj);
                        index++;
                    }

                    if (param.getType().isArray()) {
                        Object[] array = (Object[]) java.lang.reflect.Array.newInstance(elementType, list.size());
                        value = list.toArray(array);
                    } else {
                        value = list;
                    }
                    }
                }
                // 4.5. PARAMÈTRES PRIMITIFS SIMPLES (int, String, etc.) — pour éviter null
                else if (param.getType().isPrimitive() || param.getType() == String.class) {
                    if (fallbackIndex < orderedValues.size()) {
                        value = convert(orderedValues.get(fallbackIndex++), param.getType());
                    } else {
                        // Valeur par défaut pour éviter null
                        if (param.getType() == int.class) value = 0;
                        else if (param.getType() == boolean.class) value = false;
                        else value = "";
                    }
                }
                // 5. OBJET PERSONNALISÉ SIMPLE (User, Adresse, etc.)
                // 5. OBJET PERSONNALISÉ SIMPLE
                else {
                    try {
                        Object obj = param.getType().getDeclaredConstructor().newInstance();
                        Field[] fields = param.getType().getDeclaredFields();

                        boolean filled = false;

                        for (Field field : fields) {
                            String fieldName = field.getName();
                            if (allParams.containsKey(fieldName)) {
                                Object raw = allParams.get(fieldName);
                                String str = raw instanceof List<?> l && !l.isEmpty() ? l.get(0).toString() : raw.toString();
                                field.setAccessible(true);
                                field.set(obj, convert(str, field.getType()));
                                filled = true;
                            }
                        }

                        if (!filled && !fallbackUsedForSimpleObject) {
                            fallbackUsedForSimpleObject = true;
                            int idx = 0;
                            for (Field field : fields) {
                                if (idx < orderedValues.size()) {
                                    field.setAccessible(true);
                                    field.set(obj, convert(orderedValues.get(idx++), field.getType()));
                                }
                            }
                        }

                        value = obj;
                    } catch (Exception ignored) {}
                }
                args[i] = value;
            }

            // Pour handleResult savoir si c'est @JSON
            request.setAttribute("calledMethodName", method.getName());
            request.setAttribute("calledMethod", method);
            Object result = method.invoke(controllerInstance, args);
            handleResult(result, request, response);

        } catch (Exception e) {
            response.setContentType("text/plain; charset=UTF-8");
            PrintWriter out = response.getWriter();
            out.println("Erreur : " + e.getMessage());
            e.printStackTrace(out);
        }
    }

    // MÉTHODE À AJOUTER EN BAS DE LA CLASSE
    private Map<String, Object> buildAllParametersMap(HttpServletRequest request) {
        Map<String, Object> map = new HashMap<>();
        java.util.Enumeration<String> names = request.getParameterNames();

        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String[] values = request.getParameterValues(name);

            if (values != null && values.length > 0) {
                if (values.length == 1) {
                    map.put(name, values[0]);
                } else {
                    map.put(name, java.util.Arrays.asList(values)); // checkboxes
                }
            }
        }
        // Traiter les fichiers si la requête est multipart
        try {
            String ct = request.getContentType();
            if (ct != null && ct.toLowerCase().startsWith("multipart/")) {
                Collection<Part> parts = request.getParts();
                for (Part part : parts) {
                    String fieldName = part.getName();
                    String submittedFileName = part.getSubmittedFileName();
                    if (submittedFileName != null && !submittedFileName.isEmpty()) {
                        byte[] raw = part.getInputStream().readAllBytes();
                        // Sauvegarder le fichier dans le dossier /upload en conservant le nom d'origine
                        String savedName = submittedFileName;
                        try {
                            String uploadPath = getServletContext().getRealPath("/upload");
                            if (uploadPath == null) {
                                uploadPath = new File("upload").getAbsolutePath();
                            }
                            File dir = new File(uploadPath);
                            if (!dir.exists()) dir.mkdirs();
                            File outFile = new File(dir, submittedFileName);
                            if (outFile.exists()) {
                                // éviter l'écrasement : ajouter timestamp devant
                                savedName = System.currentTimeMillis() + "_" + submittedFileName;
                                outFile = new File(dir, savedName);
                            }
                            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                                fos.write(raw);
                            }
                            System.out.println("FrontServlet: uploaded field='" + fieldName + "' submitted='" + submittedFileName + "' saved='" + savedName + "' size=" + raw.length);
                        } catch (Exception ignored) {}
                        Byte[] boxed = toBoxed(raw);

                        // Construire une entrée (nom d'origine, octets)
                        java.util.Map.Entry<String, Byte[]> fileEntry = new java.util.AbstractMap.SimpleEntry<>(submittedFileName, boxed);

                        // stocker le nom sauvegardé pour information
                        map.put(fieldName + "_savedName", savedName);

                        // Insérer sous la clé du champ (ex: "file"): valeur = Byte[] | Map.Entry | List<...>
                        if (map.containsKey(fieldName)) {
                            Object existing = map.get(fieldName);
                            if (existing instanceof java.util.List) {
                                ((java.util.List) existing).add(fileEntry);
                            } else {
                                java.util.List<Object> lst = new java.util.ArrayList<>();
                                lst.add(existing);
                                lst.add(fileEntry);
                                map.put(fieldName, lst);
                            }
                        } else {
                            map.put(fieldName, fileEntry);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    // utilitaire: convertir byte[] en Byte[] (pour correspondre à la demande)
    private Byte[] toBoxed(byte[] src) {
        if (src == null) return null;
        Byte[] boxed = new Byte[src.length];
        for (int i = 0; i < src.length; i++) boxed[i] = src[i];
        return boxed;
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
    private Object convert(String str, Class<?> type) {
        if (str == null || str.isEmpty()) {
            if (type.isPrimitive()) {
                if (type == int.class) return 0;
                if (type == long.class) return 0L;
                if (type == double.class) return 0.0;
                if (type == boolean.class) return false;
            }
            return null;
        }

        try {
            if (type == String.class) {
                return str;
            } else if (type == int.class || type == Integer.class) {
                return Integer.parseInt(str.trim());
            } else if (type == long.class || type == Long.class) {
                return Long.parseLong(str.trim());
            } else if (type == double.class || type == Double.class) {
                return Double.parseDouble(str.trim());
            } else if (type == boolean.class || type == Boolean.class) {
                return Boolean.parseBoolean(str.trim());
            } else {
                // Pour les autres types, on essaie le constructor
                return type.getConstructor(String.class).newInstance(str);
            }
        } catch (Exception e) {
            // Si la conversion échoue, on retourne une valeur par défaut sûre
            if (type.isPrimitive()) {
                if (type == int.class) return 0;
                if (type == long.class) return 0L;
                if (type == double.class) return 0.0;
                if (type == boolean.class) return false;
            }
            return null;
        }
    }

    private void handleResult(Object result, HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (result == null) {
            response.sendError(404);
            return;
        }

        Method calledMethod = (Method) request.getAttribute("calledMethod");

        if (calledMethod != null && calledMethod.isAnnotationPresent(Json.class)) {
            response.setContentType("application/json; charset=UTF-8");
            PrintWriter out = response.getWriter();

            // On utilise LinkedHashMap pour garder l'ordre des clés
            Map<String, Object> jsonResponse = new LinkedHashMap<>();
            jsonResponse.put("status", "success");
            jsonResponse.put("code", 200);
            jsonResponse.put("resultat", result);

            out.println(objectToJson(jsonResponse));
            out.close();
        } else if (result instanceof ModelView) {
            // MODE MVC
            ModelView mv = (ModelView) result;
            for (Map.Entry<String, Object> entry : mv.getData().entrySet()) {
                request.setAttribute(entry.getKey(), entry.getValue());
            }
            request.getRequestDispatcher("/" + mv.getView()).forward(request, response);
        } else if (result instanceof String) {
            response.setContentType("text/html; charset=UTF-8");
            PrintWriter out = response.getWriter();
            out.println(result);
        } else {
            response.setContentType("text/plain; charset=UTF-8");
            PrintWriter out = response.getWriter();
            out.println(result.toString());
        }
    }

    private String objectToJson(Object obj) {
        return objectToJson(obj, 0);
    }

    private String objectToJson(Object obj, int indentLevel) {
        if (obj == null) {
            return "null";
        }

        String indent = "  ".repeat(indentLevel);

        if (obj instanceof String) {
            return "\"" + ((String) obj).replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{\n");
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                if (!first) sb.append(",\n");
                sb.append(indent + "  \"").append(entry.getKey()).append("\": ");
                sb.append(objectToJson(entry.getValue(), indentLevel + 1));
                first = false;
            }
            sb.append("\n").append(indent).append("}");
            return sb.toString();
        }
        if (obj instanceof List) {
            StringBuilder sb = new StringBuilder("[\n");
            boolean first = true;
            for (Object item : (List<?>) obj) {
                if (!first) sb.append(",\n");
                sb.append(indent + "  ");
                sb.append(objectToJson(item, indentLevel + 1));
                first = false;
            }
            sb.append("\n").append(indent).append("]");
            return sb.toString();
        }
        if (obj.getClass().isArray()) {
            StringBuilder sb = new StringBuilder("[\n");
            int length = Array.getLength(obj);
            for (int i = 0; i < length; i++) {
                if (i > 0) sb.append(",\n");
                sb.append(indent + "  ");
                sb.append(objectToJson(Array.get(obj, i), indentLevel + 1));
            }
            sb.append("\n").append(indent).append("]");
            return sb.toString();
        }

        // Objet personnalisé
        StringBuilder sb = new StringBuilder("{\n");
        boolean first = true;
        for (Field field : obj.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                if (!first) sb.append(",\n");
                sb.append(indent + "  \"").append(field.getName()).append("\": ");
                sb.append(objectToJson(value, indentLevel + 1));
                first = false;
            } catch (Exception ignored) {}
        }
        sb.append("\n").append(indent).append("}");
        return sb.toString();
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
