package io.agentscope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.tool.HttpRequestTool;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class AgentHttpExecutionTest {

    @Test
    void shouldExecuteHttpToolByAgent() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
            String requestUrl = server.url("/api/ping").toString();

            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new HttpRequestTool());

            ReActAgent agent =
                    ReActAgent.builder()
                            .name("http-agent")
                            .sysPrompt("你是一个会调用 HTTP 工具的助手")
                            .model(new FakeModel(requestUrl))
                            .toolkit(toolkit)
                            .memory(new InMemoryMemory())
                            .build();

            Msg userMsg =
                    Msg.builder().name("user").role(MsgRole.USER).textContent("请请求测试接口").build();

            Msg response = agent.call(userMsg).block(Duration.ofSeconds(5));
            assertNotNull(response);
            assertTrue(response.getTextContent().contains("HTTP调用完成"));

            RecordedRequest recorded = server.takeRequest(3, TimeUnit.SECONDS);
            assertNotNull(recorded);
            assertEquals("GET", recorded.getMethod());
            assertEquals("/api/ping", recorded.getPath());

            boolean hasToolResult =
                    agent.getMemory().getMessages().stream()
                            .filter(m -> m.hasContentBlocks(ToolResultBlock.class))
                            .flatMap(m -> m.getContentBlocks(ToolResultBlock.class).stream())
                            .flatMap(r -> r.getOutput().stream())
                            .filter(TextBlock.class::isInstance)
                            .map(TextBlock.class::cast)
                            .map(TextBlock::getText)
                            .anyMatch(t -> t.contains("status=200") && t.contains("{\"ok\":true}"));
            assertTrue(hasToolResult);
        }
    }

    private static class FakeModel implements Model {

        private final AtomicInteger callCount = new AtomicInteger();
        private final String requestUrl;

        private FakeModel(String requestUrl) {
            this.requestUrl = requestUrl;
        }

        @Override
        public Flux<ChatResponse> stream(
                List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
            int current = callCount.getAndIncrement();
            if (current == 0) {
                return Flux.just(
                        ChatResponse.builder()
                                .content(
                                        List.of(
                                                ToolUseBlock.builder()
                                                        .id("tool-call-1")
                                                        .name("http_get")
                                                        .input(Map.of("url", requestUrl))
                                                        .content("{\"url\":\"" + requestUrl + "\"}")
                                                        .build()))
                                .usage(new ChatUsage(1, 1, 2))
                                .build());
            }
            return Flux.just(
                    ChatResponse.builder()
                            .content(List.of(TextBlock.builder().text("HTTP调用完成").build()))
                            .usage(new ChatUsage(1, 1, 2))
                            .build());
        }

        @Override
        public String getModelName() {
            return "demo-fake-model";
        }
    }
}
