package com.aurora.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CandidatePackageHasher {
  private static final String PACKAGE_HASH = "packageHash";
  private static final String INITIATIVE_ID = "studioInitiativeId";

  private CandidatePackageHasher() {}

  public static String hash(JsonNode body, ObjectMapper mapper) {
    ObjectNode packageContent = JsonNodeFactory.instance.objectNode();
    List<String> names = new ArrayList<>();
    body.fieldNames().forEachRemaining(names::add);
    Collections.sort(names);
    for (String name : names) {
      if (!PACKAGE_HASH.equals(name) && !INITIATIVE_ID.equals(name)) {
        packageContent.set(name, canonicalize(body.get(name)));
      }
    }
    try {
      byte[] serialized = mapper.writeValueAsBytes(packageContent);
      return bytesToHex(MessageDigest.getInstance("SHA-256").digest(serialized));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to hash candidate package", exception);
    }
  }

  private static JsonNode canonicalize(JsonNode value) {
    if (value.isObject()) {
      ObjectNode ordered = JsonNodeFactory.instance.objectNode();
      List<String> names = new ArrayList<>();
      value.fieldNames().forEachRemaining(names::add);
      Collections.sort(names);
      for (String name : names) {
        ordered.set(name, canonicalize(value.get(name)));
      }
      return ordered;
    }
    if (value.isArray()) {
      ArrayNode ordered = JsonNodeFactory.instance.arrayNode();
      value.forEach(item -> ordered.add(canonicalize(item)));
      return ordered;
    }
    return value;
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      result.append(String.format("%02x", value));
    }
    return result.toString();
  }
}
