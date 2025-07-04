package com.orta.gos.services.sessions;

import java.time.Duration;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.orta.gos.model.Payload;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vavr.Function1;
import io.vavr.control.Option;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class CachedSessionsRepository implements SessionsRepository {

  @ConfigProperty(name = "sync-async-bridge.session.expiration.durarion", defaultValue = "10M")
  private Duration sessionEntryExpiration;

  private Cache<UUID, AsyncResponse> cache;

  @PostConstruct
  public void initCache() {
    cache = Caffeine.newBuilder().expireAfterWrite(sessionDuration()).build();
  }

  @Override
  public void addSession(UUID requestId, AsyncResponse response) {
    cache.put(requestId, response);
  }

  @Override
  public Option<AsyncResponse> byId(UUID requestId) {
    return Option.of(cache.asMap().remove(requestId));
  }

  public Uni<Boolean> resumeResponse(final UUID id, final Payload payload) {
    var result = byId(id).map(response -> response.resume(buildResponse(payload)))
        .onEmpty(() -> logMissingSessions(id));
    return Uni.createFrom().item(result.getOrElse(false));
  }

  private static Response buildResponse(final Payload payload) {
    return Response
        .status(fromPayloadAttributes(payload, "status-code", Integer::valueOf, 200))
        .header("content-type", fromPayloadAttributes(payload, "content-type", v -> v, "text/plain"))
        .entity(body(payload))
        .build();
  }

  private static <T> T fromPayloadAttributes(final Payload payload, String attributeKey, Function1<String, T> convert,
      T defaultValue) {
    return Option.of(payload.getAttributesMap().get(attributeKey)).map(convert).getOrElse(defaultValue);
  }

  private static String body(final Payload payload) {
    return switch (payload.getBodyCase()) {
      case EMPTY_BODY -> "";
      case STRING_BODY -> payload.getStringBody().getBody();
      case BINARY_BODY -> "<binary content>";
      case REST_REQUEST -> "<request content>";
      case BODY_NOT_SET -> "<body not set>";
    };
  }

  private static void logMissingSessions(UUID id) {
    Log.infof("Sync-Async-Service - could not find session by id:[%s]", id);
  }

  @Override
  public Duration sessionDuration() {
    return sessionEntryExpiration;
  }
}
