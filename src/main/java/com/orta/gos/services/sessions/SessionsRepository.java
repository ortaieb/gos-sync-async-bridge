package com.orta.gos.services.sessions;

import java.time.Duration;
import java.util.UUID;

import com.orta.gos.model.Payload;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vavr.Function1;
import io.vavr.control.Option;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.core.Response;

public interface SessionsRepository {

  Duration sessionDuration();

  void addSession(UUID requestId, AsyncResponse response);

  Option<AsyncResponse> byId(UUID requestId);

  default Uni<Boolean> resumeResponse(final UUID id, final Payload payload) {
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
}
