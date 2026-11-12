package com.salarytracker.tools;

import com.salarytracker.platform.UpstreamException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 基于 JDK HttpClient 的实现。
 * <p>
 * 刻意设置：
 * <ul>
 *   <li>{@code followRedirects(NEVER)} —— 跳转必须由调用方逐跳校验，否则等于绕过白名单；</li>
 *   <li>{@code BodyHandlers.discarding()} —— 不把响应体读进内存，即使上游误返回视频流；</li>
 *   <li>{@code ProxySelector.of(null)} —— 不走系统代理，避免代理把请求带到未知地址；</li>
 *   <li>不带 Cookie / Authorization，固定 UA。</li>
 * </ul>
 */
@Component
public class JdkDouyinHttpClient implements DouyinHttpClient {
    private static final String USER_AGENT = "salary-sync-tools/1.0";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(ProxySelector.of(null))
            .build();

    @Override
    public Response head(URI uri, Duration timeout) {
        try {
            Response response = send(uri, timeout, "HEAD");
            if (response.status() == 405 || response.status() == 501) {
                // 少数边缘节点不支持 HEAD，退回只取前 1 个字节的 GET；仍然不读业务响应体。
                return send(uri, timeout, "GET");
            }
            return response;
        } catch (IOException exception) {
            throw new UpstreamException("抖音暂时不可达", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UpstreamException("请求被中断", exception);
        }
    }

    private Response send(URI uri, Duration timeout, String method) throws IOException, InterruptedException {
        Duration effective = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(1) : timeout;
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(effective)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*");
        if ("HEAD".equals(method)) {
            builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Range", "bytes=0-0").GET();
        }
        HttpResponse<Void> response = client.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        return new Response(response.statusCode(), response.headers().firstValue("location").orElse(null));
    }
}
