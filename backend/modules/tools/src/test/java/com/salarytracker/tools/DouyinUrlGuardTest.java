package com.salarytracker.tools;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DouyinUrlGuardTest {
    @Test
    void acceptsVideoLinksOnWhitelistedHosts() {
        assertEquals("7400000000000000001", DouyinUrlGuard.videoId(
                DouyinUrlGuard.validate("https://www.douyin.com/video/7400000000000000001")));
        assertEquals("7400", DouyinUrlGuard.videoId(
                DouyinUrlGuard.validate("https://www.douyin.com/share/video/7400?region=CN")));
        assertEquals("7400", DouyinUrlGuard.videoId(
                DouyinUrlGuard.validate("https://www.douyin.com/discover?modal_id=7400")));
        assertEquals("7400", DouyinUrlGuard.videoId(
                DouyinUrlGuard.validate("https://www.iesdouyin.com/share/video/7400")));
        assertTrue(DouyinUrlGuard.isShortLink(
                DouyinUrlGuard.validate("https://v.douyin.com/abcd123/")));
    }

    @Test
    void normalizesUppercaseHostAndTrailingDot() {
        URI uri = DouyinUrlGuard.validate("https://WWW.DOUYIN.COM./video/7400");
        assertEquals("7400", DouyinUrlGuard.videoId(uri));
    }

    @Test
    void rejectsHostsOutsideTheWhitelist() {
        assertRejected("https://evil.com/video/7400");
        assertRejected("https://douyin.com.evil.com/video/7400");
        assertRejected("https://notdouyin.com/video/7400");
        assertRejected("https://v.douyin.com.evil.com/abcd123/");
    }

    @Test
    void rejectsUserInfoAndNonStandardPorts() {
        assertRejected("https://user@v.douyin.com/abcd123/");
        assertRejected("https://v.douyin.com:8080/abcd123/");
        assertRejected("ftp://v.douyin.com/abcd123/");
    }

    @Test
    void rejectsIpLiteralsAndBareText() {
        assertRejected("https://1.2.3.4/video/7400");
        assertRejected("网页链接 1234567890 复制此链接");
        assertRejected("");
    }

    @Test
    void rejectsUnsupportedPaths() {
        assertRejected("https://v.douyin.com/");
        assertRejected("https://v.douyin.com/ab/");
        assertRejected("https://www.douyin.com/user/MS4wLjABAAAA");
        assertRejected("https://www.douyin.com/");
    }

    @Test
    void classifiesPrivateAddresses() throws Exception {
        assertTrue(DouyinUrlGuard.isPrivateAddress(InetAddress.getByName("10.0.0.1")));
        assertTrue(DouyinUrlGuard.isPrivateAddress(InetAddress.getByName("172.16.0.1")));
        assertTrue(DouyinUrlGuard.isPrivateAddress(InetAddress.getByName("192.168.1.1")));
        assertTrue(DouyinUrlGuard.isPrivateAddress(InetAddress.getByName("127.0.0.1")));
        assertTrue(DouyinUrlGuard.isPrivateAddress(InetAddress.getByName("169.254.1.1")));
        assertTrue(DouyinUrlGuard.isPrivateAddress(InetAddress.getByName("100.64.0.1")));
        assertTrue(DouyinUrlGuard.isPrivateAddress(InetAddress.getByName("0.0.0.0")));
        assertTrue(DouyinUrlGuard.isPrivateAddress(InetAddress.getByName("224.0.0.1")));
        assertTrue(DouyinUrlGuard.isPrivateAddress(InetAddress.getByName("::1")));
        assertTrue(DouyinUrlGuard.isPrivateAddress(InetAddress.getByName("fd00::1")));
        assertTrue(DouyinUrlGuard.isPrivateAddress(InetAddress.getByName("fe80::1")));

        assertFalse(DouyinUrlGuard.isPrivateAddress(InetAddress.getByName("8.8.8.8")));
        assertFalse(DouyinUrlGuard.isPrivateAddress(InetAddress.getByName("1.1.1.1")));
    }

    private void assertRejected(String url) {
        assertThrows(IllegalArgumentException.class, () -> DouyinUrlGuard.validate(url), url);
    }
}
