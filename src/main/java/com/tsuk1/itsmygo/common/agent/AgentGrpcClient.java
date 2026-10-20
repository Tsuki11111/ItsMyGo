package com.tsuk1.itsmygo.common.agent;

import agent.v1.AgentServiceGrpc;
import agent.v1.ConverseRequest;
import agent.v1.Event;
import agent.v1.HistoryMessage;
import agent.v1.HistoryRequest;
import agent.v1.ListSessionsRequest;
import agent.v1.SessionInfo;
import agent.v1.StopRequest;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

/** 买小购 gRPC 客户端：内网明文、metadata 注入身份、不做自动重试。 */
@Slf4j
@Component
public class AgentGrpcClient implements AgentGateway {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> USER_ID =
            Metadata.Key.of("x-user-id", ASCII_STRING_MARSHALLER);

    private static final Duration SHORT_RPC_DEADLINE = Duration.ofSeconds(15);

    private final AgentProperties properties;
    private final ManagedChannel channel;

    public AgentGrpcClient(AgentProperties properties) {
        this.properties = properties;
        this.channel = NettyChannelBuilder.forTarget(properties.target())
                .usePlaintext()
                // Python grpc 默认要求 ping 间隔 ≥300s，别设太小
                .keepAliveTime(300, TimeUnit.SECONDS)
                .keepAliveTimeout(20, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(false)
                .build();
        log.info("买小购 gRPC 客户端目标: {}", properties.target());
    }

    @Override
    public boolean configured() {
        return StringUtils.hasText(properties.key());
    }

    @Override
    public Iterator<Event> converse(long userId, String sessionId, String message, Duration deadline) {
        AgentServiceGrpc.AgentServiceBlockingStub stub = stub(userId)
                .withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS);
        ConverseRequest request = ConverseRequest.newBuilder()
                .setSessionId(sessionId == null ? "" : sessionId)
                .setMessage(message)
                .build();
        return stub.converse(request);
    }

    @Override
    public boolean stop(long userId, String sessionId, String runId) {
        AgentServiceGrpc.AgentServiceBlockingStub stub = stub(userId)
                .withDeadlineAfter(SHORT_RPC_DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
        return stub.stop(StopRequest.newBuilder()
                .setSessionId(sessionId)
                .setRunId(runId)
                .build()).getStopped();
    }

    @Override
    public List<HistoryMessage> history(long userId, String sessionId, int limit) {
        AgentServiceGrpc.AgentServiceBlockingStub stub = stub(userId)
                .withDeadlineAfter(SHORT_RPC_DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
        return stub.getHistory(HistoryRequest.newBuilder()
                .setSessionId(sessionId)
                .setLimit(limit)
                .build()).getMessagesList();
    }

    @Override
    public List<SessionInfo> sessions(long userId, int limit) {
        AgentServiceGrpc.AgentServiceBlockingStub stub = stub(userId)
                .withDeadlineAfter(SHORT_RPC_DEADLINE.toMillis(), TimeUnit.MILLISECONDS);
        return stub.listSessions(ListSessionsRequest.newBuilder()
                .setLimit(limit)
                .build()).getSessionsList();
    }

    private AgentServiceGrpc.AgentServiceBlockingStub stub(long userId) {
        Metadata metadata = new Metadata();
        // 客户端同名 metadata 不会被转发：这是 Java 新构造的 Metadata，由 Java 独占
        metadata.put(AUTHORIZATION, "Bearer " + properties.key());
        metadata.put(USER_ID, String.valueOf(userId));
        return AgentServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    @PreDestroy
    public void shutdown() {
        channel.shutdownNow();
    }
}
