package com.salarytracker.tools;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.NotFoundException;
import com.salarytracker.platform.UpstreamException;
import com.salarytracker.tools.ToolsModels.DouyinResolveResult;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 把抖音分享短链还原成带视频 ID 的真实地址。
 * <p>
 * 范围刻意收窄：只跟随 HTTP 跳转并读 {@code Location}，不解析页面、不抓标题/封面、
 * 不下载视频、不生成去水印地址。短链被风控拦截返回 200 HTML 时，结果是
 * {@code resolved=false} 加一个可解释的原因，而不是伪造成功。
 */
@Service
public class DouyinLinkResolver {
    /** 最多 4 次请求 = 初始短链 + 3 次跳转。 */
    private static final int MAX_REQUESTS = 4;
    private static final Duration TOTAL_BUDGET = Duration.ofSeconds(5);

    private final DouyinHttpClient client;
    private final DouyinHostChecker hostChecker;
    private final ToolsRateLimiter rateLimiter;
    private final CurrentUserResolver currentUser;

    public DouyinLinkResolver(DouyinHttpClient client, DouyinHostChecker hostChecker,
                              ToolsRateLimiter rateLimiter, CurrentUserResolver currentUser) {
        this.client = client;
        this.hostChecker = hostChecker;
        this.rateLimiter = rateLimiter;
        this.currentUser = currentUser;
    }

    public DouyinResolveResult resolve(String rawUrl) {
        rateLimiter.check(currentUser.id());

        URI start = DouyinUrlGuard.validate(rawUrl);
        List<String> hops = new ArrayList<>();
        hops.add(start.toString());
        URI current = start;
        long deadline = System.nanoTime() + TOTAL_BUDGET.toNanos();

        for (int attempt = 0; attempt < MAX_REQUESTS; attempt++) {
            String videoId = DouyinUrlGuard.videoId(current);
            if (videoId != null) {
                return result(rawUrl, current, videoId, hops, start, true, "");
            }
            if (!DouyinUrlGuard.isShortLink(current)) {
                return result(rawUrl, current, "", hops, start, false, "链接中未识别到视频 ID");
            }

            hostChecker.check(current);
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) throw new UpstreamException("解析超时，请稍后再试");

            DouyinHttpClient.Response response = client.head(current, Duration.ofNanos(remainingNanos));
            int status = response.status();
            if (status >= 300 && status < 400) {
                String location = response.location();
                if (location == null || location.isBlank()) {
                    throw new UpstreamException("抖音返回了无效跳转");
                }
                // 每一跳都重新跑完整校验（协议、host 白名单、路径、端口），避免开放重定向绕过。
                URI next = DouyinUrlGuard.validate(current.resolve(location).toString());
                hops.add(next.toString());
                current = next;
                continue;
            }
            if (status == 404 || status == 410) throw new NotFoundException("分享链接已失效");
            if (status == 200) {
                return result(rawUrl, current, "", hops, start, false, "短链未返回跳转地址（可能被风控拦截）");
            }
            throw new UpstreamException("抖音暂时不可达（HTTP " + status + "）");
        }
        throw new IllegalArgumentException("短链跳转次数过多");
    }

    private DouyinResolveResult result(String inputUrl, URI current, String videoId, List<String> hops,
                                       URI start, boolean resolved, String reason) {
        return new DouyinResolveResult(inputUrl, current.toString(), videoId == null ? "" : videoId,
                List.copyOf(hops), current.getHost(), DouyinUrlGuard.isShortLink(start), resolved, reason);
    }
}
