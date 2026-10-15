package com.recsys.web;

import com.recsys.config.AppContext;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.nio.file.Path;
import java.util.EnumSet;

/** Wires servlets and filters into an embedded Jetty servlet container. */
public final class WebServer {
    private WebServer() { }

    public static Server create(AppContext app, int port, Path frontendDir) {
        Server server = new Server(port);
        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        ctx.setContextPath("/");
        ctx.setResourceBase(frontendDir.toAbsolutePath().toString());
        ctx.getSessionHandler().setHttpOnly(true);
        ctx.getSessionHandler().setMaxInactiveInterval(30 * 60);

        EnumSet<DispatcherType> requests = EnumSet.of(DispatcherType.REQUEST);
        ctx.addFilter(new FilterHolder(new SecurityHeadersFilter()), "/*", requests);
        ctx.addFilter(new FilterHolder(new AuthFilter(app.users)), "/api/*", requests);

        ctx.addServlet(new ServletHolder(new AuthServlet(app.users)), "/api/auth/*");
        ctx.addServlet(new ServletHolder(new CatalogServlet(app.items)), "/api/items");
        ctx.addServlet(new ServletHolder(new CategoryServlet(app.items)), "/api/categories");
        ctx.addServlet(new ServletHolder(new ReviewServlet(app.reviews)), "/api/reviews/*");
        ctx.addServlet(new ServletHolder(new RecommendationServlet(app.recommendations)), "/api/recommendations");
        ctx.addServlet(new ServletHolder(new InteractionServlet(app.interactions)), "/api/interactions");
        ctx.addServlet(new ServletHolder(new PreferenceServlet(app.preferences)), "/api/preferences");
        ctx.addServlet(new ServletHolder(new AdminSettingsServlet(app.settings)), "/api/admin/settings");
        ctx.addServlet(new ServletHolder(new AdminAnalyticsServlet(app.analytics)), "/api/admin/analytics");
        ctx.addServlet(new ServletHolder(new AdminUserServlet(app.users)), "/api/admin/users/*");

        ServletHolder staticFiles = new ServletHolder("static", DefaultServlet.class);
        staticFiles.setInitParameter("dirAllowed", "false");
        staticFiles.setInitParameter("cacheControl", "no-cache");
        ctx.addServlet(staticFiles, "/");

        server.setHandler(ctx);
        return server;
    }
}
