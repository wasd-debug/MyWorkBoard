package com.salarytracker.tools;

import java.net.URI;
import java.time.Duration;

/**
 * 只做「取响应头」的最小 HTTP 抽象：解析短链只需要状态码与 {@code Location}，
 * 不需要也不允许读取响应体。单独抽出来是为了让 {@link DouyinLinkResolver} 可以在
 * 不联网的情况下做单测。
 */
public interface DouyinHttpClient {
    Response head(URI uri, Duration timeout);

    record Response(int status, String location) {
    }
}
