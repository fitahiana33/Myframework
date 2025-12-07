package com.nandrianina.framework.modelView;

import java.util.HashMap;

public class ModelView {
    private String view;
    private HashMap<String, Object> data;

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
