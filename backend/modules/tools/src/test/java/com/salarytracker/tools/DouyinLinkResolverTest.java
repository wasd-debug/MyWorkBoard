package com.salarytracker.tools;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.NotFoundException;
import com.salarytracker.platform.TooManyRequestsException;
import com.salarytracker.platform.UpstreamException;
import com.salarytracker.tools.ToolsModels.DouyinResolveResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DouyinLinkResolverTest {
    private static final String SHORT = "https://v.douyin.com/abcd123/";

    @Test
    void followsRedirectToVideoPage() {
        DouyinHttpClient client = mock(DouyinHttpClient.class);
        when(client.head(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DouyinHttpClient.Response(302, "https://www.douyin.com/video/7400"));

        DouyinResolveResult result = resolver(client).resolve(SHORT);

        assertTrue(result.resolved());
        assertEquals("7400", result.videoId());
        assertEquals(2, result.hops().size());
        assertTrue(result.shortLink());
        assertEquals("www.douyin.com", result.host());
    }

    @Test
    void resolvesRelativeRedirectLocation() {
        DouyinHttpClient client = mock(DouyinHttpClient.class);
        // 第一跳是同host 的相对短码，第二跳才是视频页：验证 current.resolve(location) 生效。
        when(client.head(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DouyinHttpClient.Response(302, "/next2/"),
                        new DouyinHttpClient.Response(302, "https://www.douyin.com/video/7400"));

        DouyinResolveResult result = resolver(client).resolve(SHORT);

        assertTrue(result.resolved());
        assertEquals("7400", result.videoId());
        assertEquals(3, result.hops().size());
    }

    @Test
    void rejectsRelativeLocationThatIsNotAShortCode() {
        DouyinHttpClient client = mock(DouyinHttpClient.class);
        when(client.head(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DouyinHttpClient.Response(302, "/share/video/7400"));

        // 解析后仍是 v.douyin.com，但路径不是短码，按短链规则被拒绝，避免把短链 host 当成长链用。
        assertThrows(IllegalArgumentException.class, () -> resolver(client).resolve(SHORT));
    }

    @Test
    void answersLongLinksWithoutCallingUpstream() {
        DouyinHttpClient client = mock(DouyinHttpClient.class);

        DouyinResolveResult result = resolver(client).resolve("https://www.douyin.com/video/7400");

        assertTrue(result.resolved());
        assertEquals("7400", result.videoId());
        assertFalse(result.shortLink());
        verifyNoInteractions(client);
    }

    @Test
    void rejectsRedirectChainsThatAreTooLong() {
        DouyinHttpClient client = mock(DouyinHttpClient.class);
        when(client.head(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DouyinHttpClient.Response(302, "https://v.douyin.com/next1/"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> resolver(client).resolve(SHORT));
        assertTrue(error.getMessage().contains("跳转次数过多"));
    }

    @Test
    void rejectsRedirectToPrivateAddress() {
        DouyinHttpClient client = mock(DouyinHttpClient.class);
        when(client.head(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DouyinHttpClient.Response(302, "https://127.0.0.1/video/7400"));

        assertThrows(IllegalArgumentException.class, () -> resolver(client).resolve(SHORT));
    }

    @Test
    void mapsUpstreamFailures() {
        DouyinHttpClient notFound = mock(DouyinHttpClient.class);
        when(notFound.head(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DouyinHttpClient.Response(404, null));
        assertThrows(NotFoundException.class, () -> resolver(notFound).resolve(SHORT));

        DouyinHttpClient broken = mock(DouyinHttpClient.class);
        when(broken.head(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DouyinHttpClient.Response(503, null));
        assertThrows(UpstreamException.class, () -> resolver(broken).resolve(SHORT));
    }

    @Test
    void reportsUnresolvedWhenShortLinkReturnsHtml() {
        DouyinHttpClient client = mock(DouyinHttpClient.class);
        when(client.head(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DouyinHttpClient.Response(200, null));

        DouyinResolveResult result = resolver(client).resolve(SHORT);

        assertFalse(result.resolved());
        assertEquals("", result.videoId());
        assertTrue(result.reason().contains("风控"));
    }

    @Test
    void rateLimitsPerUser() {
        DouyinHttpClient client = mock(DouyinHttpClient.class);
        DouyinLinkResolver resolver = resolver(client);
        for (int i = 0; i < 10; i++) {
            resolver.resolve("https://www.douyin.com/video/7400");
        }
        assertThrows(TooManyRequestsException.class,
                () -> resolver.resolve("https://www.douyin.com/video/7400"));
        verify(client, never()).head(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private DouyinLinkResolver resolver(DouyinHttpClient client) {
        CurrentUserResolver current = mock(CurrentUserResolver.class);
        when(current.id()).thenReturn(42L);
        return new DouyinLinkResolver(client, uri -> { }, new ToolsRateLimiter(), current);
    }
}
