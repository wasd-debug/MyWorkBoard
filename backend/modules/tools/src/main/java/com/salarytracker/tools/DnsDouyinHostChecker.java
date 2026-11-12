package com.salarytracker.tools;

import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * 生产环境的域名校验：要求域名解析到公网地址。见 {@link DouyinUrlGuard#assertPublicHost(URI)}。
 */
@Component
public class DnsDouyinHostChecker implements DouyinHostChecker {
    @Override
    public void check(URI uri) {
        DouyinUrlGuard.assertPublicHost(uri);
    }
}
