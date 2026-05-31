package com.example.aidemo.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void writesNestedObjectAndArray() {
        String json = Json.write(Map.of("a", 1L, "b", List.of("x", "y")));
        // LinkedHashMap 顺序在 Map.of 下不保证，这里只校验关键片段
        assertTrue(json.contains("\"a\":1"));
        assertTrue(json.contains("\"b\":[\"x\",\"y\"]"));
    }

    @Test
    void escapesSpecialCharacters() {
        String json = Json.write(Map.of("k", "line1\n\"q\"\t"));
        assertTrue(json.contains("\\n"));
        assertTrue(json.contains("\\\""));
        assertTrue(json.contains("\\t"));
    }

    @Test
    void parsesObjectWithMixedTypes() {
        Map<String, Object> m = Json.parseObject(
                "{\"n\":42,\"d\":3.5,\"b\":true,\"s\":\"hi\",\"nul\":null,\"arr\":[1,2,3]}");
        assertEquals(42L, m.get("n"));
        assertEquals(3.5, (Double) m.get("d"), 1e-9);
        assertEquals(Boolean.TRUE, m.get("b"));
        assertEquals("hi", m.get("s"));
        assertEquals(null, m.get("nul"));
        assertInstanceOf(List.class, m.get("arr"));
    }

    @Test
    void roundTripsThroughWriteAndParse() {
        String original = "{\"name\":\"weather\",\"params\":{\"city\":\"北京\"},\"ids\":[1,2]}";
        Map<String, Object> parsed = Json.parseObject(original);
        Map<String, Object> reparsed = Json.parseObject(Json.write(parsed));
        assertEquals("weather", reparsed.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) reparsed.get("params");
        assertEquals("北京", params.get("city"));
    }
}
