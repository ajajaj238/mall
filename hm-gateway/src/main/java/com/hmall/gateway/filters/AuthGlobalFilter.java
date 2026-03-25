package com.hmall.gateway.filters;

import com.hmall.common.exception.UnauthorizedException;
import com.hmall.gateway.config.AuthProperties;
import com.hmall.gateway.utils.JwtTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Autowired
    private AuthProperties authProperties;
    @Autowired
    private JwtTool jwtTool;

    private AntPathMatcher matcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //获取request
        ServerHttpRequest request = exchange.getRequest();
        //判断是否需要拦截
        if (isExclude(request.getPath().toString())) {
            //放行
            return chain.filter(exchange);
        }
        //获取用户token
        String token = null;
        List<String> headers = request.getHeaders().get("Authorization");

        if (headers != null && headers.size() > 0) {
            token = headers.get(0);
        }

        Long parsed = null;
        try {
            parsed = jwtTool.parseToken(token);
        } catch (UnauthorizedException e) {
            //拦截，设置响应状态401
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }

        //传递用户信息
        String userId = parsed.toString();
        ServerWebExchange exchange1 = exchange.mutate()
                .request(builder -> builder.header("user-info", userId))
                .build();

        //放行
        return chain.filter(exchange1);
    }

    private boolean isExclude(String string) {
        List<String> paths = authProperties.getExcludePaths();
        for (String path : paths) {
            if (matcher.match(path, string)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
