package com.orta.gos.resources;

import static com.orta.gos.resources.UnifiedSyncResource.parseHeaders;
import static com.orta.gos.resources.UnifiedSyncResource.route;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UnifiedResource")
public class UnifiedResourceTest {

  @Nested
  @DisplayName("route")
  class RouteTest {

    @Test
    @DisplayName("string with no special characters returns the same")
    void testNoChangeCase() {
      assertThat(route("test-me")).isEqualTo("test-me");
    }

    @Test
    @DisplayName("string with underscore")
    void testUnderscoreReplace() {
      assertThat(route("test_me")).isEqualTo("test-me");
    }

    @Test
    @DisplayName("string with multiple underscore")
    void testMultiUnderscoreReplace() {
      assertThat(route("test_me_again")).isEqualTo("test-me-again");
    }

    @Test
    @DisplayName("string with fwdSlash")
    void testfwdSlashReplace() {
      assertThat(route("test/me")).isEqualTo("test-me");
    }

    @Test
    @DisplayName("string with multiple fwdSlash")
    void testMultifwdSlashReplace() {
      assertThat(route("test/me/again")).isEqualTo("test-me-again");
    }

    @Test
    @DisplayName("string with mixed replacement")
    void testMixedReplace() {
      assertThat(route("test/me_again")).isEqualTo("test-me-again");
    }

  }

  @Nested
  @DisplayName("parseHeaders")
  class ParseHeadersTest {

    @Test
    @DisplayName("empty headers will return empty map")
    void testEmptyHeadersMap() {
      Map<String, List<String>> headers = Map.of();
      assertThat(parseHeaders(headers)).isEmpty();
    }

    @Test
    @DisplayName("headers with a single entry holding single value")
    void testSingleHeaderWithSingleValue() {
      Map<String, List<String>> headers = Map.of("key", List.of("a"));
      assertThat(parseHeaders(headers)).contains(Map.entry("key", "a"));
    }

    @Test
    @DisplayName("headers with a single entry holding many values")
    void testSingleHeaderWithMultiValue() {
      Map<String, List<String>> headers = Map.of("key", List.of("a", "b", "c"));
      assertThat(parseHeaders(headers)).contains(Map.entry("key", "a, b, c"));
    }

    @Test
    @DisplayName("headers with a many entries")
    void testCommonCase() {
      var headers = Map.of(
          "key1", List.of("a1"),
          "key2", List.of("a2", "b2", "c2"),
          "key3", List.of("a3"));
      assertThat(parseHeaders(headers)).contains(Map.entry("key1", "a1"), Map.entry("key2", "a2, b2, c2"),
          Map.entry("key3", "a3"));
    }
  }
}
