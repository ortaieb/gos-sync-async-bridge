package com.orta.gos.resources;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.orta.gos.model.EmptyBody;
import com.orta.gos.model.Payload;
import com.orta.gos.model.PlatformMessage;
import com.orta.gos.model.PlatformWorkflow;
import com.orta.gos.model.StringBody;
import com.orta.gos.model.rules.BlockEdge;
import com.orta.gos.model.rules.BlockIndicator;
import com.orta.gos.model.rules.BlockRange;
import com.orta.gos.model.rules.BlockType;
import com.orta.gos.model.rules.Step;
import com.orta.gos.model.rules.Tracker;
import com.orta.gos.services.AsyncGeppettoClient;
import com.orta.gos.services.sessions.SessionsRepository;

import io.quarkus.logging.Log;
import io.vavr.Function1;
import io.vavr.control.Option;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;

@Path("/")
public class UnifiedSyncResource {

  @Inject
  SessionsRepository sessions;

  @Inject
  AsyncGeppettoClient asyncClient;

  @ConfigProperty(name = "sync-async-bridge.valid.prefixes")
  List<String> validPrefixes;

  @GET
  @Path("{prefix}/{action:[a-zA-Z0-9/_-]+}")
  public void serveGeTEndpoit(
      @PathParam("prefix") String prefix,
      @PathParam("action") String action,
      @Suspended AsyncResponse asyncResponse,
      @Context HttpHeaders headers) {
    unifiedEndpoint(prefix, action, asyncResponse, headers, null);
  }

  @PUT
  @Path("{prefix}/{action:[a-zA-Z0-9/_-]+}")
  public void servePutEndpoit(
      @PathParam("prefix") String prefix,
      @PathParam("action") String action,
      @Suspended AsyncResponse asyncResponse,
      @Context HttpHeaders headers,
      String body) {
    unifiedEndpoint(prefix, action, asyncResponse, headers, body);
  }

  @POST
  @Path("{prefix}/{action:[a-zA-Z0-9/_-]+}")
  public void servePostEndpoit(
      @PathParam("prefix") String prefix,
      @PathParam("action") String action,
      @Suspended AsyncResponse asyncResponse,
      @Context HttpHeaders headers,
      String body) {
    unifiedEndpoint(prefix, action, asyncResponse, headers, body);
  }

  @DELETE
  @Path("{prefix}/{action:[a-zA-Z0-9/_-]+}")
  public void serveDeleteEndpoit(
      @PathParam("prefix") String prefix,
      @PathParam("action") String action,
      @Suspended AsyncResponse asyncResponse,
      @Context HttpHeaders headers) {
    unifiedEndpoint(prefix, action, asyncResponse, headers, null);
  }

  private void unifiedEndpoint(
      String prefix,
      String action,
      @Suspended AsyncResponse asyncResponse,
      @Context HttpHeaders headers,
      String body) {

    printRequest(prefix, action, headers, Option.of(body).getOrElse("n/a"));

    var requestId = UUID.randomUUID();
    sessions.addSession(requestId, asyncResponse);
    Log.infof("parked request [%s]", requestId);

    var initialPayload = buildInitialPaylaod(body);

    var initialTracker = Tracker.newBuilder()
        .setCurrentBlock(BlockType.MAIN)
        .setCurrentStep(0)
        .setErrorRaised(false)
        .setTermination(1)
        .putRanges(BlockType.MAIN_VALUE,
            BlockRange.newBuilder().setType(BlockType.MAIN).setStartIdx(0).setEndIdx(0).build())
        .build();

    var initialStep = Step.newBuilder()
        .setName("endpoint")
        .setAddress("n/a")
        .addIndicators(BlockIndicator.newBuilder().setType(BlockType.MAIN).setEdge(BlockEdge.START))
        .addIndicators(BlockIndicator.newBuilder().setType(BlockType.MAIN).setEdge(BlockEdge.END));

    var initialWorkflow = PlatformWorkflow.newBuilder()
        .addSteps(initialStep)
        .setTracker(initialTracker);

    var message = PlatformMessage.newBuilder()
        .setId(requestId.toString())
        .putAllHeaders(parseHeaders(headers.getRequestHeaders()))
        .putHeaders("route", route(action))
        .addPayloads(initialPayload)
        .setWorkflowLog(initialWorkflow)
        .build();

    var updatedMessage = asyncClient.appendRules(message, Duration.ofMillis(500));
    Log.infof("After adding rule: %s", updatedMessage);
    asyncClient.sendMessage(updatedMessage);

    Log.infof("-- gRPC call [%s] completed", requestId);
  }

  public static void printRequest(String prefix, String action, HttpHeaders headers, String body) {
    Log.info("----");
    Log.infof("accepted [%s] %s", prefix, route(action));
    Log.info("----");
    parseHeaders(headers.getRequestHeaders()).forEach((k, v) -> Log.infof("\t%s: %s", k, v));
    Log.info("----");
    Log.info(body);
    Log.info("----");
  }

  public static String route(final String action) {
    return action.replaceAll("[_\\/]", "-");
  }

  public static <K, V extends CharSequence> Map<K, String> parseHeaders(Map<K, List<V>> headers) {
    return headers.entrySet().stream().collect(
        Collectors.toMap(k -> k.getKey(), v -> String.join(", ", (Iterable<? extends CharSequence>) v.getValue())));
  }

  private static Function1<String, Payload.Builder> stringBody = body -> Payload.newBuilder()
      .setStringBody(StringBody.newBuilder().setBody(body));
  private static Supplier<Payload.Builder> emptyBody = () -> Payload.newBuilder().setEmptyBody(EmptyBody.newBuilder());

  private static Payload.Builder buildInitialPaylaod(final String originalBody) {
    return Option.of(originalBody).fold(emptyBody, stringBody);
  }
}
