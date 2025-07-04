package com.orta.gos.services;

import java.time.Duration;

import com.orta.gos.model.PlatformMessage;
import com.orta.gos.model.WorkflowEnrichmentGrpc.WorkflowEnrichmentBlockingStub;
import com.orta.gos.model.utils.PlatformMessageUtils;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AsyncGeppettoClient {

  @Inject
  GrpcClientRepository clientRepo;

  @GrpcClient("workflow-enrich")
  WorkflowEnrichmentBlockingStub workflowEnrichmentStub;

  public void sendMessage(PlatformMessage message) {

    new PlatformMessageUtils(message).maybeCurrentAddress().forEach(stepAddress -> {
      Log.infof("sending [%s] message to [%s]", message.getId(), stepAddress);
      clientRepo.getStub(stepAddress).handle(message)
          .onItem().invoke(response -> Log.infof("Received response: %s", response))
          .onFailure().invoke(error -> Log.errorf(error, "Failed to send message to `%s`", stepAddress))
          .onCancellation().invoke(() -> Log.warn("Ack service call was cancelled"))
          .subscribe().with(
              response -> Log.info("Completed successfully"),
              failure -> Log.error("Completed with failure", failure));
    });

  }

  public PlatformMessage appendRules(PlatformMessage message, Duration timeout) {
    return workflowEnrichmentStub
        .withDeadlineAfter(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
        .appendRules(message);
  }

}
