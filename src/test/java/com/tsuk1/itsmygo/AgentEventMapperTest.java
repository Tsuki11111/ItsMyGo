package com.tsuk1.itsmygo;

import agent.v1.Delta;
import agent.v1.ErrorEvent;
import agent.v1.Event;
import agent.v1.FinalEvent;
import agent.v1.Image;
import agent.v1.SessionStart;
import agent.v1.Trace;
import agent.v1.Usage;
import com.tsuk1.itsmygo.common.agent.AgentEventMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEventMapperTest {

    @Test
    void mapsStartTraceDeltaAndFinal() {
        AgentEventMapper.Mapped start = AgentEventMapper.map(Event.newBuilder()
                .setStart(SessionStart.newBuilder().setSessionId("s1").setRunId("r1")).build());
        assertThat(start.event()).isEqualTo("start");
        assertThat(start.data()).containsEntry("sessionId", "s1").containsEntry("runId", "r1");

        AgentEventMapper.Mapped trace = AgentEventMapper.map(Event.newBuilder()
                .setTrace(Trace.newBuilder().setKind("delegate").setAgent("product-kb")
                        .setDetail("检索")).build());
        assertThat(trace.event()).isEqualTo("trace");
        assertThat(trace.data()).containsEntry("kind", "delegate")
                .containsEntry("agent", "product-kb").containsEntry("detail", "检索");

        AgentEventMapper.Mapped delta = AgentEventMapper.map(Event.newBuilder()
                .setDelta(Delta.newBuilder().setDelta("片段")).build());
        assertThat(delta.event()).isEqualTo("delta");
        assertThat(delta.data()).containsEntry("delta", "片段");

        AgentEventMapper.Mapped fin = AgentEventMapper.map(Event.newBuilder()
                .setFinal(FinalEvent.newBuilder()
                        .setAnswer("答案")
                        .setWebOnly(true)
                        .addImages(Image.newBuilder().setUrl("u").setCaption("图注"))).build());
        assertThat(fin.event()).isEqualTo("final");
        assertThat(fin.data()).containsEntry("answer", "答案").containsEntry("webOnly", true);
        @SuppressWarnings("unchecked")
        var images = (java.util.List<java.util.Map<String, Object>>) fin.data().get("images");
        assertThat(images).hasSize(1);
        assertThat(images.get(0)).containsEntry("url", "u").containsEntry("caption", "图注");
    }

    @Test
    void mapsUsageAndGenericError() {
        AgentEventMapper.Mapped usage = AgentEventMapper.map(Event.newBuilder()
                .setUsage(Usage.newBuilder().setCalls(2).setFailed(1).setTotalTokens(300)
                        .setCost(0.12).setElapsedMs(1500).setUnpricedCalls(1)).build());
        assertThat(usage.event()).isEqualTo("usage");
        assertThat(usage.data()).containsEntry("calls", 2).containsEntry("failed", 1)
                .containsEntry("totalTokens", 300L).containsEntry("unpricedCalls", 1);

        AgentEventMapper.Mapped error = AgentEventMapper.map(Event.newBuilder()
                .setError(ErrorEvent.newBuilder().setMessage("内部堆栈").build()).build());
        assertThat(error.event()).isEqualTo("error");
        assertThat(error.data()).containsEntry("code", "AGENT_ERROR")
                .containsEntry("message", "客服处理失败，请稍后重试")
                .containsEntry("retryable", true);
    }

    @Test
    void helpersAndEmptyEvent() {
        assertThat(AgentEventMapper.done("stopped")).containsEntry("reason", "stopped");
        assertThat(AgentEventMapper.error("X", "x", false))
                .containsEntry("code", "X").containsEntry("message", "x")
                .containsEntry("retryable", false);
        assertThat(AgentEventMapper.map(Event.getDefaultInstance())).isNull();
    }
}
