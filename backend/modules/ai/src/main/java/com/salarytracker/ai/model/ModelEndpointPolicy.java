package com.salarytracker.ai.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;

@Component
public class ModelEndpointPolicy {
    private final boolean allowPrivateNetwork;

    public ModelEndpointPolicy(@Value("${app.ai.allow-private-network:false}") boolean allowPrivateNetwork) {
        this.allowPrivateNetwork = allowPrivateNetwork;
    }

    public URI validate(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl == null ? "" : rawUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("模型端点必须使用 HTTPS");
            if (uri.getUserInfo() != null) throw new IllegalArgumentException("模型端点不能包含用户名或密码");
            if (uri.getHost() == null || uri.getHost().isBlank()) throw new IllegalArgumentException("模型端点缺少主机名");
            if (uri.getPort() != -1 && uri.getPort() != 443) throw new IllegalArgumentException("模型端点只允许 443 端口");
            if (!allowPrivateNetwork) {
                for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                    if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                            || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                        throw new IllegalArgumentException("模型端点不能指向本机或私有网络");
                    }
                    byte[] bytes = address.getAddress();
                    if (bytes.length == 4 && (bytes[0] & 255) == 169 && (bytes[1] & 255) == 254) {
                        throw new IllegalArgumentException("模型端点不能访问链路本地地址");
                    }
                }
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("模型端点无法解析", exception);
        }
    }
}
