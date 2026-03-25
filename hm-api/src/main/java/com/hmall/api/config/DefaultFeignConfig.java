package com.hmall.api.config;

import com.hmall.common.utils.UserContext;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class DefaultFeignConfig {


    @Bean
    public RequestInterceptor requestInterceptor() {
       return new RequestInterceptor() {
           @Override
           public void apply(feign.RequestTemplate requestTemplate) {
               Long user = UserContext.getUser();
               if (user != null){
                   requestTemplate.header("user-info", user.toString());
               }
           }
       };
    }
}
