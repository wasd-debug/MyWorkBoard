package com.salarytracker.tools;

import java.net.URI;

/**
 * 连接前的域名解析校验。抽成接口是为了让短链解析的单测可以跳过真实 DNS。
 */
@FunctionalInterface
public interface DouyinHostChecker {
    void check(URI uri);
}
