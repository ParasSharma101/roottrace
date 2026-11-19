package com.roottrace.ingestion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;

public final class PayloadDigest {
    private PayloadDigest() {}
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public static String canonicalHash(ObjectMapper mapper, Object value) { return hash(sorted(mapper.valueToTree(value)).toString()); }
    private static JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            var fields = new TreeMap<String, JsonNode>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), sorted(entry.getValue())));
            var result = JsonNodeFactory.instance.objectNode();
            fields.forEach(result::set);
            return result;
        }
        if (node.isArray()) {
            var result = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> result.add(sorted(item)));
            return result;
        }
        return node;
    }
}
