package com.orta.gos.services;

import static com.orta.gos.model.utils.Navigation.updateWorkflow;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import com.orta.gos.model.InboundGrpc;
import com.orta.gos.model.PlatformMessage;
import com.orta.gos.model.PlatformResponse;
import com.orta.gos.model.utils.PlatformMessageUtils;
import com.orta.gos.services.sessions.SessionsRepository;

import io.grpc.stub.StreamObserver;
import io.quarkus.grpc.GrpcService;
import io.quarkus.logging.Log;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@GrpcService
@RegisterForReflection
public class ProcessGrpcService extends InboundGrpc.InboundImplBase {

  @Inject
  GrpcClientRepository clientRepository;

  @Inject
  SessionsRepository sessions;

  @Override
  public void handle(PlatformMessage request, StreamObserver<PlatformResponse> responseObserver) {
    responseOriginator(request)
        .onItem().transform(updateSteps())
        .onItem().invoke(callNextStep(clientRepository))
        .subscribe().with(platformMessage -> {
          responseObserver.onNext(generateResponse(platformMessage));
          Log.info("--- message: ");
          Log.info(platformMessage);
          Log.info("---");
          responseObserver.onCompleted();
        }, failure -> {
          Log.error("Failed to add rules to the workflow, " + failure.getMessage(), failure);
          responseObserver.onError(failure);
        });
  }

  public static PlatformResponse generateResponse(PlatformMessage request) {
    return PlatformResponse.newBuilder().setId(request.getId()).setStatus(200).build();
  }

  public static Response responseToOriginator(PlatformMessage request) {
    var payload = request.getPayloadsList().getLast();
    return Response.status(200)
        .entity(payload.getStringBody().getBody())
        .header("request-id", request.getId())
        .build();
  }

  public static Function<PlatformMessage, PlatformMessage> updateSteps() {
    return message -> {
      var workflowBuilder = updateWorkflow(message.getWorkflowLog());
      return PlatformMessage.newBuilder(message).setWorkflowLog(workflowBuilder).build();
    };
  }

  public Uni<PlatformMessage> responseOriginator(final PlatformMessage message) {
    sessions.resumeResponse(UUID.fromString(message.getId()), message.getPayloadsList().getLast());
    return Uni.createFrom().item(message);
  }

  public static final Consumer<PlatformMessage> callNextStep(GrpcClientRepository clientRepo) {
    return message -> {
      new PlatformMessageUtils(message).maybeCurrentAddress()
          .forEach(nextStepAddress -> clientRepo.getStub(nextStepAddress).handle(message).subscribe().with(
              nextResponse -> Log.infof("Message [%s] successfuly arrived to %s", message.getId(), nextStepAddress),
              failure -> Log.errorf("Error processing message [%s] by next step %s: %s%n", message.getId(),
                  nextStepAddress, failure.getMessage())));
    };
  }
}
