package org.luckyjourney.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.luckyjourney.entity.user.User;
import org.luckyjourney.holder.UserHolder;
import org.luckyjourney.service.user.UserService;
import org.luckyjourney.util.JwtUtils;
import org.luckyjourney.util.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @description:
 * @Author: menyon
 * @CreateTime: 2023-10-25 20:17
 */
// TODO:拦截器是一个非常轻量级的组件，只有在需要时才会被调用，并且不需要像控制器或服务一样在整个应用程序中可用。
//  因此，将拦截器声明为一个Spring Bean可能会引导致性能下降。因此，建议通过构造器注入的方式获取外部容器中的bean
@Component
public class AdminInterceptor implements HandlerInterceptor {

    private ObjectMapper objectMapper = new ObjectMapper();

    private UserService userService;

   public AdminInterceptor(UserService userService){
       this.userService = userService;
   }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if (request.getMethod().equals("OPTIONS")){
            return true;
        }

        if (!JwtUtils.checkToken(request)) {
            response(R.error().message("请登录后再操作"),response);
            return false;
        }

        final Long userId = JwtUtils.getUserId(request);
        final User user = userService.getById(userId);
        if (ObjectUtils.isEmpty(user)){
            response(R.error().message("用户不存在"),response);
            return false;
        }
        UserHolder.set(userId);
        return true;
    }

    private boolean response(R r, HttpServletResponse response) throws IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Cache-Control", "no-cache");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().println(objectMapper.writeValueAsString(r));
        response.getWriter().flush();
        return false;
    }
    // 这里有必要重写一个HandlerIntercepter接口的afterCompletion方法 后端业务完成后，最后执行这个方法，清除threadlocal内存，避免内存泄漏
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.clear();
    }
}
