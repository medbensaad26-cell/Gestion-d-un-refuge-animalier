package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.router.Router;
import com.enicarthage.refugeanimalier.security.PasswordUtils;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Manual/CLI Postman testing against disposable MySQL, never the development database. */
public final class PostmanSandbox extends MysqlTestSupport {
    private PostmanSandbox() {}

    public static void main(String[] args) throws Exception {
        Path backend = Path.of("").toAbsolutePath();
        if (Files.isRegularFile(backend.resolve("Backend/pom.xml"))) backend = backend.resolve("Backend");
        Path postman = backend.getParent().resolve("postman");
        Path target = Files.createDirectories(backend.resolve("target"));
        Path environmentFile = target.resolve("postman-sandbox.environment.json");
        Path stopFile = target.resolve("postman-sandbox.stop");
        Path rawReport = target.resolve("newman-private-result.json");
        Path runnerLog = target.resolve("newman-private.log");
        HttpServer server = null;
        try {
            startIsolatedMysql();
            PostmanSandbox sandbox = new PostmanSandbox();
            sandbox.resetIsolatedData();
            sandbox.useSingleAdministrator();
            String password = UUID.randomUUID().toString();
            String email = "postman-sandbox@example.test";
            try (var connection = sandbox.connection(); var statement = connection.prepareStatement(
                    "UPDATE admin SET email = ?, mot_de_passe = ? WHERE id = 1")) {
                statement.setString(1, email);
                statement.setString(2, PasswordUtils.hacher(password));
                statement.executeUpdate();
            }
            JsonObject environment = JsonParser.parseString(Files.readString(
                    postman.resolve("isolated.postman_environment.json"))).getAsJsonObject();
            setEnvironment(environment, "adminEmail", email);
            setEnvironment(environment, "adminPassword", password);
            setEnvironment(environment, "requestAdminId", "1");
            setEnvironment(environment, "allowTestWrites", "true");
            Files.writeString(environmentFile, new GsonBuilder().setPrettyPrinting().create().toJson(environment));
            Files.deleteIfExists(stopFile);

            server = Router.creerServeur(new InetSocketAddress("127.0.0.1", 18080));
            server.removeContext("/api/health");
            server.createContext("/api/health", exchange -> {
                int status = !exchange.getRequestURI().getPath().equals("/api/health") ? 404
                        : exchange.getRequestMethod().equals("GET") ? 200 : 405;
                if (status == 405) exchange.getResponseHeaders().set("Allow", "GET");
                exchange.getResponseHeaders().set("X-Refuge-Test-Sandbox", "true");
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                byte[] body = (status == 200 ? "{\"status\":\"ok\"}" : "{\"erreur\":\"Route ou methode invalide\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start();
            System.out.println("Postman sandbox ready on http://127.0.0.1:18080 (isolated MySQL).");
            if (Arrays.asList(args).contains("--newman")) {
                runNewman(postman, environmentFile, target, rawReport, runnerLog);
            } else {
                System.out.println("Import the private, temporary environment file: " + environmentFile);
                System.out.println("Stop by creating this empty file: " + stopFile);
                while (!Files.exists(stopFile)) Thread.sleep(500);
            }
        } finally {
            if (server != null) server.stop(0);
            try {
                stopIsolatedMysql();
            } finally {
                com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
                Files.deleteIfExists(environmentFile);
                Files.deleteIfExists(rawReport);
                Files.deleteIfExists(runnerLog);
                Files.deleteIfExists(stopFile);
            }
        }
    }

    private static void setEnvironment(JsonObject environment, String key, String value) {
        for (var element : environment.getAsJsonArray("values")) {
            JsonObject entry = element.getAsJsonObject();
            if (entry.get("key").getAsString().equals(key)) {
                entry.addProperty("value", value);
                entry.addProperty("enabled", true);
                return;
            }
        }
        throw new IllegalArgumentException("Missing Postman environment field: " + key);
    }

    private static void runNewman(Path postman, Path environment, Path target,
                                  Path rawReport, Path runnerLog) throws Exception {
        Path collection = postman.resolve("RefugeAnimalier.postman_collection.json");
        JsonObject variables = JsonParser.parseString(Files.readString(environment)).getAsJsonObject();
        setEnvironment(variables, "allowTestWrites", "false");
        Files.writeString(environment, variables.toString());
        NewmanRun guard = executeNewman(collection, environment, rawReport, runnerLog);
        int guardRequests = guard.run().getAsJsonObject("stats").getAsJsonObject("requests").get("total").getAsInt();
        if (guardRequests != 0) {
            throw new IllegalStateException("Postman safety guard sent requests while writes were disabled");
        }
        setEnvironment(variables, "allowTestWrites", "true");
        Files.writeString(environment, variables.toString());
        NewmanRun result = executeNewman(collection, environment, rawReport, runnerLog);
        JsonObject run = result.run();
        JsonObject summary = new JsonObject();
        summary.addProperty("runtime", "Newman 6.2.1; isolated MySQL");
        summary.addProperty("completedAt", Instant.now().toString());
        summary.addProperty("guardRequestsSentWithWritesDisabled", guardRequests);
        summary.add("stats", run.getAsJsonObject("stats").deepCopy());
        JsonArray failures = new JsonArray();
        for (var element : run.getAsJsonArray("failures")) {
            var failure = element.getAsJsonObject();
            JsonObject safe = new JsonObject();
            if (failure.has("source") && failure.getAsJsonObject("source").has("name")) {
                safe.add("request", failure.getAsJsonObject("source").get("name"));
            }
            safe.add("type", failure.getAsJsonObject("error").get("name"));
            failures.add(safe);
        }
        summary.add("failures", failures);
        Files.writeString(target.resolve("newman-summary.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(summary));
        System.out.println("Postman requests: " + run.getAsJsonObject("stats").getAsJsonObject("requests").get("total")
                + "; assertions: " + run.getAsJsonObject("stats").getAsJsonObject("assertions").get("total")
                + "; failures: " + failures.size() + "; disabled-write guard requests: " + guardRequests);
        if (result.exitCode() != 0 || !failures.isEmpty()) {
            throw new IllegalStateException("Postman verification failed; see target/newman-summary.json (credentials omitted)");
        }
    }

    private static NewmanRun executeNewman(Path collection, Path environment,
                                           Path rawReport, Path runnerLog) throws Exception {
        List<String> arguments = List.of("--yes", "newman@6.2.1", "run",
                collection.toString(),
                "--environment", environment.toString(), "--reporters", "json",
                "--reporter-json-export", rawReport.toString(), "--timeout-request", "10000");
        var command = new java.util.ArrayList<String>();
        if (System.getProperty("os.name").startsWith("Windows")) {
            command.addAll(List.of("cmd.exe", "/d", "/c", "npx.cmd"));
        } else {
            command.add("npx");
        }
        command.addAll(arguments);
        Process runner = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(runnerLog.toFile()).start();
        if (!runner.waitFor(240, TimeUnit.SECONDS)) {
            runner.descendants().forEach(ProcessHandle::destroy);
            runner.destroyForcibly().waitFor();
            throw new IllegalStateException("Newman timed out; no private runner output is published");
        }
        if (!Files.isRegularFile(rawReport)) {
            throw new IllegalStateException("Newman could not generate a report; check Node/npm availability");
        }
        JsonObject run = JsonParser.parseString(Files.readString(rawReport)).getAsJsonObject().getAsJsonObject("run");
        return new NewmanRun(run, runner.exitValue());
    }

    private record NewmanRun(JsonObject run, int exitCode) {}
}
