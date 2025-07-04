package com.orta.gos.services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.orta.gos.model.MutinyInboundGrpc;
import com.orta.gos.model.MutinyInboundGrpc.MutinyInboundStub;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.logging.Log;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterForReflection
public class GrpcClientRepository {

  private Map<String, ManagedChannel> channelMap;
  private Map<String, MutinyInboundStub> stubMap;

  @PostConstruct
  public void init() {
    channelMap = new ConcurrentHashMap<>();
    stubMap = new ConcurrentHashMap<>();
  }

  public MutinyInboundStub getStub(String address) {
    var hostname = address.split(":")[0];
    var port = Integer.parseInt(address.split(":")[1]);

    Log.infof("[%s] -> (%s, %d)", address, hostname, port);
    return stubMap.computeIfAbsent(address, k -> {
      ManagedChannel channel = channelMap.computeIfAbsent(address,
          ch -> ManagedChannelBuilder.forAddress(hostname, port)
              .usePlaintext()
              .build());
      return MutinyInboundGrpc.newMutinyStub(channel);
    });
  }

  public void shutdownAll() {
    channelMap.values().forEach(ManagedChannel::shutdownNow);
    channelMap.clear();
    stubMap.clear();
  }
}
