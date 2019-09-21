package com.atguigu.gmall.passport.config;

import io.jsonwebtoken.*;

import java.util.Map;

public class JwtUtil {
    /**
     * 生成token
     * @param key 公共部分
     * @param param 用户信息
     * @param salt 服务器Ip
     * @return
     */
    public static String encode(String key,Map<String,Object> param,String salt){
        if(salt!=null){
            key+=salt;
        }
        JwtBuilder jwtBuilder = Jwts.builder().signWith(SignatureAlgorithm.HS256,key);
        // 把用户信息放入token中！
        jwtBuilder = jwtBuilder.setClaims(param);

        String token = jwtBuilder.compact();
        return token;

    }

    /**
     * 反解密
     * @param token 字符串
     * @param key 公共部分
     * @param salt 私有部分
     * @return
     */
    public  static Map<String,Object> decode(String token , String key, String salt){
        Claims claims=null;
        if (salt!=null){
            key+=salt;
        }
        try {
            claims= Jwts.parser().setSigningKey(key).parseClaimsJws(token).getBody();
        } catch ( JwtException e) {
            return null;
        }
        // 解密出用户信息！
        return  claims;
    }

}
