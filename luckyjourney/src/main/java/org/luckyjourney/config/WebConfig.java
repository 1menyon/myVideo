package org.luckyjourney.config;

import org.luckyjourney.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

/**
 * @description:
 * @Author: menyon
 * @CreateTime: 2023-10-24 16:27
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private UserService userService;


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminInterceptor(userService))
                .addPathPatterns("/admin/**","/authorize/**")
                .addPathPatterns("/luckyjourney/**")
                .excludePathPatterns("/luckyjourney/login/**","/luckyjourney/index/**","/luckyjourney/cdn/**", "/luckyjourney/file/**");

    }
    /**
     *      跨域设置主要是为了控制和限制哪些网站可以通过浏览器的 JavaScript 访问您的资源。
     * */
    @Override
    public void addCorsMappings(CorsRegistry registry) {

        String[] url = {"http://101.35.228.84:8882","http://101.35.228.84:5378","http://127.0.0.1:5378","http://localhost:5378"};

        registry.addMapping("/**")
                .allowedOrigins(url)
                .allowCredentials(true)
                .allowedMethods("*")   // 允许跨域的方法，可以单独配置
                .allowedHeaders("*");  // 允许跨域的请求头，可以单独配置;
    }
    /**
     * 通过knife4j生成接口文档
     * @return
     */
    @Bean
    public Docket docket() {
        ApiInfo apiInfo = new ApiInfoBuilder()
                .title("视频项目接口文档")
                .version("4.0")
                .description("视频项目接口文档")
                .build();
        Docket docket = new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(apiInfo)
                .select()
                .apis(RequestHandlerSelectors.basePackage("org.luckyjourney.controller"))
                .paths(PathSelectors.any())
                .build();
        return docket;
    }

    /**
     * 设置静态资源映射
     * @param registry
     */
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }


}
