package com.nandrianina.framework.modelView;

/**
 * Classe représentant une vue avec son nom de fichier
 */
public class ModelView {
    private String view;

    public ModelView() {
    }

    public ModelView(String view) {
        this.view = view;
    }

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }
}
