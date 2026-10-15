package com.recsys;

import com.recsys.config.AppContext;
import com.recsys.config.DataSeeder;
import com.recsys.web.WebServer;
import org.eclipse.jetty.server.Server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Entry point. Options (all optional):
 * <pre>
 *   -Drecsys.port=8080   -Drecsys.db=jdbc:h2:file:./data/recsys   -Drecsys.frontend=path/to/frontend
 * </pre>
 */
public final class Main {
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private Main() { }

    public static void main(String[] args) throws Exception {
        int port = Integer.getInteger("recsys.port", 8080);
        Path frontend = locateFrontend();
        String dbUrl = System.getProperty("recsys.db",
                "jdbc:h2:file:" + frontend.getParent().resolve("data").resolve("recsys").toAbsolutePath());

        AppContext app = new AppContext(dbUrl, true);
        Server server = WebServer.create(app, port, frontend);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (Exception ignored) {
                // shutting down anyway
            }
            app.close();
        }));
        server.start();

        LOG.info("==========================================================");
        LOG.info(" AI Recommendation System running at http://localhost:" + port);
        LOG.info(" Admin demo : " + DataSeeder.ADMIN_EMAIL + " / " + DataSeeder.ADMIN_PASSWORD);
        LOG.info(" User demo  : " + DataSeeder.DEMO_EMAIL + " / " + DataSeeder.DEMO_PASSWORD);
        LOG.info("==========================================================");
        server.join();
    }

    /** Finds the static frontend folder whether started from the project root or /backend. */
    private static Path locateFrontend() {
        String configured = System.getProperty("recsys.frontend");
        if (configured != null) return Path.of(configured).toAbsolutePath();
        for (String candidate : new String[]{"frontend", "../frontend"}) {
            Path p = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isDirectory(p)) return p;
        }
        throw new IllegalStateException("Cannot find the frontend folder. Run from the project root or pass -Drecsys.frontend=...");
    }
}
