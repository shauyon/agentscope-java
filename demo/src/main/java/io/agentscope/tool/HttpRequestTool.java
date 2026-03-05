package io.agentscope.tool;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Simple HTTP request tool for AgentScope agents.
 */
public class HttpRequestTool {

    private static final int MAX_RESPONSE_LENGTH = 2000;
    private final HttpClient httpClient;

    public HttpRequestTool() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    HttpRequestTool(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Tool(name = "http_get", description = "Send a simple HTTP GET request and return status/body")
    public ToolResultBlock httpGet(
            @ToolParam(
                            name = "url",
                            description = "HTTP URL to request, e.g. http://localhost:8080/ping")
                    String url) {
        if (url == null || url.isBlank()) {
            return ToolResultBlock.error("url is required");
        }
        try {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .GET()
                            .timeout(Duration.ofSeconds(10))
                            .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            String body = truncate(response.body());
            String result = "status=" + response.statusCode() + ", body=" + body;
            return ToolResultBlock.text(result);
        } catch (IllegalArgumentException e) {
            return ToolResultBlock.error("invalid url: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResultBlock.error("request interrupted");
        } catch (IOException e) {
            return ToolResultBlock.error("request failed: " + e.getMessage());
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= MAX_RESPONSE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_RESPONSE_LENGTH) + "...(truncated)";
    }
}
