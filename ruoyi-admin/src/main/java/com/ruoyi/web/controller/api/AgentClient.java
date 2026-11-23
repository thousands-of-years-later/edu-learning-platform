package com.ruoyi.web.controller.api;

import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Python Agent 服务的内网 HTTP 客户端。
 *
 * 只由 Java 后端调用，不直接暴露给前端；x-internal-token 与 Agent 服务一致。
 */
@Service
public class AgentClient
{
    private static final Logger log = LoggerFactory.getLogger(AgentClient.class);

    private final RestClient restClient;

    public AgentClient(@Value("${agent.base-url:http://127.0.0.1:8100}") String baseUrl,
                       @Value("${agent.internal-token:}") String internalToken)
    {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(120));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("x-internal-token", internalToken)
                .build();
    }

    public Map<String, Object> post(String path, Object body)
    {
        log.info("调用 Agent 服务: {}", path);
        return restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}
