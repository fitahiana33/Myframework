package com.nandrianina.framework.modelView;

import java.util.HashMap;

/**
 * Classe représentant une vue avec son nom de fichier et les données à passer
 */
public class ModelView {
    private String view;
    private HashMap<String, Object> data;

    public ModelView() {
        this.data = new HashMap<>();
    }

    public ModelView(String view) {
        this.view = view;
        this.data = new HashMap<>();
    }

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }

    public HashMap<String, Object> getData() {
        return data;
    }

    public void addItem(String key, Object value) {
        this.data.put(key, value);
    }
}
