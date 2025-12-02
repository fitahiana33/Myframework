package com.nandrianina.framework.util;

import java.util.regex.Pattern;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class PatternCache {
    private static final Map<String, Pattern> cache = new ConcurrentHashMap<>();

    public static Pattern getPattern(String urlWithParams) {
        return cache.computeIfAbsent(urlWithParams, key -> {
            String regex = key.replaceAll("\\{[^/]+\\}", "([^/]+)");
            return Pattern.compile("^" + regex + "$");
        });
    }
}