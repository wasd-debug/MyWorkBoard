package com.salarytracker.tools;

import com.salarytracker.platform.UpstreamException;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 抖音链接的白名单与地址校验（纯函数）。
 * <p>
 * 这里做的是「输入侧」防护：只接受抖音域名、只接受视频类路径、拒绝 IP 字面量与
 * 非标准端口。真正建立连接前的 DNS 解析校验在 {@link #assertPublicHost(URI)}。
 * <p>
 * 已知残余风险：JDK 的 {@code HttpClient} 不支持把已校验的 IP 固定到连接上，
 * 校验与连接之间存在 DNS rebinding 的时间窗。缓解手段是「域名白名单 + 每跳校验 +
 * 不读取响应体 + 5 秒总预算 + 不在服务端保存任何响应内容」，不宣称彻底消除。
 */
public final class DouyinUrlGuard {
    public static final Set<String> ALLOWED_HOSTS = Set.of(
            "douyin.com", "www.douyin.com", "v.douyin.com", "iesdouyin.com", "www.iesdouyin.com");

    private static final Pattern SHORT_PATH = Pattern.compile("^/[A-Za-z0-9_-]{4,32}/?$");
    private static final Pattern VIDEO_PATH = Pattern.compile("^/(?:share/)?video/(\\d+)");
    private static final Pattern VIDEO_QUERY = Pattern.compile("(?:^|&)(?:modal_id|aweme_id)=(\\d+)");
    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(?:\\.\\d{1,3}){3}$");
    private static final int MAX_LENGTH = 2048;

    private DouyinUrlGuard() {
    }

    /**
     * 校验并归一化一个抖音链接。任何不符合白名单的输入都抛 {@link IllegalArgumentException}，
     * 由平台异常处理器转成 400。
     */
    public static URI validate(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("请提供抖音分享链接");
        if (text.length() > MAX_LENGTH) throw new IllegalArgumentException("链接长度超出限制");

        URI uri;
        try {
            uri = new URI(text);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("链接格式无效");
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("只支持 http/https 链接");
        }
        if (uri.getUserInfo() != null) throw new IllegalArgumentException("链接不能包含用户信息");
        if (uri.getRawAuthority() != null && uri.getRawAuthority().contains("%")) {
            throw new IllegalArgumentException("链接主机不能包含转义字符");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new IllegalArgumentException("链接缺少主机名");
        host = normalizeHost(host);
        if (IPV4_LITERAL.matcher(host).matches() || host.contains(":")) {
            throw new IllegalArgumentException("链接主机不能是 IP 地址");
        }
        if (!ALLOWED_HOSTS.contains(host)) throw new IllegalArgumentException("只支持抖音域名");

        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) throw new IllegalArgumentException("链接端口不受支持");

        String path = uri.getPath() == null ? "" : uri.getPath();
        if (isShortHost(host)) {
            if (!SHORT_PATH.matcher(path).matches()) throw new IllegalArgumentException("短链格式无效");
        } else if (videoId(uri) == null) {
            throw new IllegalArgumentException("链接中未识别到视频 ID");
        }
        return uri;
    }

    public static boolean isShortLink(URI uri) {
        return uri != null && isShortHost(normalizeHost(uri.getHost()));
    }

    public static String videoId(URI uri) {
        if (uri == null) return null;
        String path = uri.getPath() == null ? "" : uri.getPath();
        Matcher byPath = VIDEO_PATH.matcher(path);
        if (byPath.find()) return byPath.group(1);
        String query = uri.getRawQuery();
        if (query != null) {
            Matcher byQuery = VIDEO_QUERY.matcher(query);
            if (byQuery.find()) return byQuery.group(1);
        }
        return null;
    }

    /**
     * 连接前校验：域名必须解析到公网地址，任一解析结果落在内网/保留段即拒绝。
     * DNS 失败按上游不可用处理（502），而不是 400 —— 那是外部故障不是用户的错。
     */
    public static void assertPublicHost(URI uri) {
        String host = normalizeHost(uri.getHost());
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isPrivateAddress(address)) {
                    throw new IllegalArgumentException("链接主机解析到内网地址");
                }
            }
        } catch (UnknownHostException exception) {
            throw new UpstreamException("无法解析链接主机", exception);
        }
    }

    public static boolean isPrivateAddress(InetAddress address) {
        if (address == null) return true;
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            if (first == 0) return true;                                   // 0.0.0.0/8
            if (first == 10) return true;                                  // 10.0.0.0/8
            if (first == 127) return true;                                 // 回环
            if (first == 100 && second >= 64 && second <= 127) return true; // 100.64.0.0/10 运营商 NAT
            if (first == 169 && second == 254) return true;                // 链路本地
            if (first == 172 && second >= 16 && second <= 31) return true; // 172.16.0.0/12
            if (first == 192 && second == 168) return true;                // 192.168.0.0/16
            return first >= 224;                                           // 组播与保留段
        }
        if (bytes.length == 16) {
            int first = bytes[0] & 0xff;
            if ((first & 0xfe) == 0xfc) return true;                       // fc00::/7
            if (first == 0xfe && (bytes[1] & 0xc0) == 0x80) return true;   // fe80::/10
            boolean mapped = true;
            for (int index = 0; index < 10; index++) {
                if (bytes[index] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped && bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff) {
                try {
                    return isPrivateAddress(InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16)));
                } catch (UnknownHostException exception) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private static boolean isShortHost(String host) {
        return "v.douyin.com".equals(host);
    }

    private static String normalizeHost(String host) {
        if (host == null) return "";
        String normalized = host.toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
