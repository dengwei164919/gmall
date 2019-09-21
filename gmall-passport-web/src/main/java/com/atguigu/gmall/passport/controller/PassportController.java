package com.atguigu.gmall.passport.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.UserInfo;
import com.atguigu.gmall.passport.config.JwtUtil;
import com.atguigu.gmall.service.UserInfoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Controller
public class PassportController {

    @Reference
    private UserInfoService userInfoService;

    @Value("${token.key}")
    private String key;

    @RequestMapping("index")
    public String index(HttpServletRequest request){
        String originUrl = request.getParameter("originUrl");

        request.setAttribute("originUrl",originUrl);

        return "index";
    }

    @RequestMapping("login")
    @ResponseBody
    public String login(UserInfo userInfo,HttpServletRequest request){
        UserInfo info = userInfoService.login(userInfo);
        if (info != null){
            String salt = request.getHeader("X-forwarded-for");
            Map<String,Object> map = new HashMap<>();
            map.put("userId",info.getId());
            map.put("nickName",info.getNickName());
            String token = JwtUtil.encode(key, map, salt);
            return token;
        }else {
            return "fail";
        }
    }

    @RequestMapping("verify")
    @ResponseBody
    public String verify(HttpServletRequest request){

        String token = request.getParameter("token");
        String salt = request.getParameter("salt");

        Map<String, Object> map = JwtUtil.decode(token, key, salt);

        if (map != null){
            String userId = (String) map.get("userId");
            UserInfo userInfo = userInfoService.verify(userId);

            if (userInfo != null){
                return "success";
            }
        }
        return "fail";

    }
}
