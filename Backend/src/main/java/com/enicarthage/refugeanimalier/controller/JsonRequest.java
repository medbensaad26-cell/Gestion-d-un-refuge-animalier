package com.enicarthage.refugeanimalier.controller;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class JsonRequest {
    private JsonRequest() {}

    static JsonObject lireObjet(HttpExchange exchange) throws IOException {
        try (JsonReader reader = new JsonReader(new InputStreamReader(
                exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Un objet JSON est attendu");
            }
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonParseException("Un seul objet JSON est attendu");
            }
            return element.getAsJsonObject();
        } catch (com.google.gson.stream.MalformedJsonException e) {
            throw new JsonParseException("JSON invalide", e);
        }
    }
}
