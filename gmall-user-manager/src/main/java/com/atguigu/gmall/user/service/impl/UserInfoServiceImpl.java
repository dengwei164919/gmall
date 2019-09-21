package com.atguigu.gmall.user.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.UserAddress;
import com.atguigu.gmall.bean.UserInfo;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.service.UserInfoService;
import com.atguigu.gmall.user.mapper.UserAddressMapper;
import com.atguigu.gmall.user.mapper.UserInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;
import tk.mybatis.mapper.entity.Example;

import java.util.List;

@Service
public class UserInfoServiceImpl implements UserInfoService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private UserAddressMapper userAddressMapper;

    @Autowired
    private RedisUtil redisUtil;

    public String userKey_prefix="user:";
    public String userinfoKey_suffix=":info";
    public int userKey_timeOut=60*60*24;

    @Override
    public List<UserInfo> findAll() {
        return userInfoMapper.selectAll();
    }

    @Override
    public UserInfo getUserInfoByName(String name) {
        return null;
    }

    @Override
    public List<UserInfo> getUserInfoListByName(UserInfo userInfo) {
        return null;
    }

    @Override
    public List<UserInfo> getUserInfoListByNickName(UserInfo userInfo) {
        return null;
    }

    @Override
    public void addUser(UserInfo userInfo) {

    }

    @Override
    public void updUser(UserInfo userInfo) {

    }

    @Override
    public void delUser(UserInfo userInfo) {

    }

    @Override
    public List<UserAddress> getUserAddressByUserId(String userId) {
        Example example = new Example(UserAddress.class);
        example.createCriteria().andEqualTo("userId",userId);

        return userAddressMapper.selectByExample(example);
    }

    @Override
    public List<UserAddress> getUserAddressByUserId(UserAddress userAddress) {
        return userAddressMapper.select(userAddress);
    }

    @Override
    public UserInfo login(UserInfo userInfo) {

        String passwd = userInfo.getPasswd();
        String newPasswd = DigestUtils.md5DigestAsHex(passwd.getBytes());
        userInfo.setPasswd(newPasswd);

        UserInfo info = userInfoMapper.selectOne(userInfo);

        if (info != null){
            Jedis jedis = null;
            try {
                jedis = redisUtil.getJedis();
                String userKey = userKey_prefix + info.getId() + userinfoKey_suffix;
                jedis.setex(userKey,userKey_timeOut, JSON.toJSONString(info));

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (jedis != null ){
                    jedis.close();
                }
            }

            return info;
        }
        return null;

    }

    @Override
    public UserInfo verify(String userId) {

        UserInfo userInfo = null;
        Jedis jedis = null;
        try {
            jedis = redisUtil.getJedis();
            String userKey = userKey_prefix + userId + userinfoKey_suffix;
            String userJson = jedis.get(userKey);

            if (!StringUtils.isEmpty(userJson)){
                //延长用户过期时间
                jedis.expire(userKey,userKey_timeOut);
                //返回对象
                userInfo = JSON.parseObject(userJson,UserInfo.class);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (jedis != null){
                jedis.close();
            }
        }
        return userInfo;
    }
}
