package com.nandrianina.framework;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;

public class FrontServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		processRequest(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		processRequest(req, resp);
	}

	private void processRequest(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {

		String contextPath = req.getContextPath();
		String requestUri = req.getRequestURI();
		String pathWithinApp = requestUri.substring(contextPath.length());
		if (pathWithinApp.isEmpty()) {
			pathWithinApp = "/";
		}

		// 1) Static-first: if the requested resource exists in the webapp, forward to the default servlet
		if (resourceExists(pathWithinApp)) {
			RequestDispatcher defaultDispatcher = getServletContext().getNamedDispatcher("default");
			if (defaultDispatcher != null) {
				defaultDispatcher.forward(req, resp);
				return;
			}
		}

		// 2) Otherwise, handle with the framework FrontServlet
		resp.setContentType("text/html;charset=UTF-8");
		try (PrintWriter out = resp.getWriter()) {
			out.println("<html><body>");
			out.println("<h2>FrontServlet a intercepté la requête !</h2>");
			out.println("<p>URL demandée : " + pathWithinApp + "</p>");
			out.println("<p>(Ressource statique introuvable, traitement par le framework)</p>");
			out.println("</body></html>");
		}
	}

	private boolean resourceExists(String pathWithinApp) {
		// Normalise chemin et vérifie la présence de la ressource dans le contexte web
		String normalized = pathWithinApp;
		if (!normalized.startsWith("/")) {
			normalized = "/" + normalized;
		}
		try {
			URL resourceUrl = getServletContext().getResource(normalized);
			return resourceUrl != null;
		} catch (MalformedURLException e) {
			return false;
		}
	}
}
