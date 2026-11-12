package com.salarytracker.platform;

/**
 * 依赖的外部服务不可用（连接失败、超时、5xx）。映射为 502，避免把上游故障
 * 伪装成本服务的 500，便于前端区分「重试可能有用」和「服务端缺陷」。
 */
public class UpstreamException extends RuntimeException {
    public UpstreamException(String message) {
        super(message);
    }

    public UpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
