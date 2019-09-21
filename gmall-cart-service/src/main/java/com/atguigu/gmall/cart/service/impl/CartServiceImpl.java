package com.atguigu.gmall.cart.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.cart.constant.CartConst;
import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.ManageService;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Reference
    private ManageService manageService;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public void addToCart(String skuId, String userId, int skuNum) {
        CartInfo cartInfo = new CartInfo();
        cartInfo.setUserId(userId);
        cartInfo.setSkuId(skuId);
        CartInfo cartInfoExist = cartInfoMapper.selectOne(cartInfo);

        if (cartInfoExist != null){
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum() + skuNum);
            cartInfoExist.setSkuPrice(cartInfoExist.getCartPrice());
            cartInfoMapper.updateByPrimaryKeySelective(cartInfoExist);
        }else {
            SkuInfo skuInfo = manageService.getSkuInfo(skuId);
            CartInfo cartInfo1 = new CartInfo();

            cartInfo1.setSkuId(skuId);
            cartInfo1.setCartPrice(skuInfo.getPrice());
            cartInfo1.setSkuPrice(skuInfo.getPrice());
            cartInfo1.setSkuName(skuInfo.getSkuName());
            cartInfo1.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo1.setUserId(userId);
            cartInfo1.setSkuNum(skuNum);

            cartInfoMapper.insertSelective(cartInfo1);
            cartInfoExist = cartInfo1;
        }

        String userCartKey = CartConst.USER_KEY_PREFIX+userId+CartConst.USER_CART_KEY_SUFFIX;

        Jedis jedis = null;

        try {
            jedis = redisUtil.getJedis();
            jedis.hset(userCartKey,skuId, JSON.toJSONString(cartInfoExist));
            //更新过期时间
            String userInfoKey = CartConst.USER_KEY_PREFIX + userId + CartConst.USERINFOKEY_SUFFIX;
            Long ttl = jedis.ttl(userInfoKey);
            jedis.expire(userCartKey,ttl.intValue());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (jedis != null){
                jedis.close();
            }
        }
    }

    @Override
    public List<CartInfo> getCartList(String userId) {
        Jedis jedis = null;

        List<CartInfo> cartInfoList = new ArrayList<>();

        try {
            jedis = redisUtil.getJedis();

            String cartKey = CartConst.USER_KEY_PREFIX + userId + CartConst.USER_CART_KEY_SUFFIX;

            List<String> cartList = jedis.hvals(cartKey);

            if (cartList != null && cartList.size() > 0){
                for (String cartJson : cartList) {
                    CartInfo cartInfo = JSON.parseObject(cartJson,CartInfo.class);
                    cartInfoList.add(cartInfo);
                }
                //按照时间倒叙
                cartInfoList.sort(new Comparator<CartInfo>() {
                    @Override
                    public int compare(CartInfo o1, CartInfo o2) {
                        return o1.getId().compareTo(o2.getId());
                    }
                });

            }else {
                //从数据库查找
                cartInfoList = loadCartCache(userId);
            }
            return cartInfoList;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (jedis != null){
                jedis.close();
            }
        }


        return null;
    }

    @Override
    public List<CartInfo> mergeToCartList(List<CartInfo> cartListCK, String userId) {

        List<CartInfo> cartInfoListDB = cartInfoMapper.selectCartListWithCurPrice(userId);
        for (CartInfo cartInfoCK : cartListCK) {
            boolean isMatch = false;

            for (CartInfo cartInfoDB : cartInfoListDB) {
                if (cartInfoCK.getSkuId().equals(cartInfoDB.getSkuId())){
                    cartInfoDB.setSkuNum(cartInfoDB.getSkuNum() + cartInfoCK.getSkuNum());
                    cartInfoMapper.updateByPrimaryKeySelective(cartInfoDB);
                    isMatch = true;
                }
            }

            if (!isMatch){
                cartInfoCK.setUserId(userId);
                cartInfoMapper.insertSelective(cartInfoCK);
            }
        }

        List<CartInfo> cartInfoList = loadCartCache(userId);

        //合并勾选的商品,根据cookie中的商品的选中状态勾选
        for (CartInfo cartInfoDB : cartInfoListDB) {
            for (CartInfo cartInfoCK : cartListCK) {
                if (cartInfoCK.getSkuId().equals(cartInfoDB.getSkuId())){
                    if ("1".equals(cartInfoCK.getIsChecked())){
                        cartInfoDB.setIsChecked("1");
                        checkCart(cartInfoDB.getSkuId(),cartInfoCK.getIsChecked(),userId);
                    }
                }
            }
        }

        return cartInfoList;
    }

    @Override
    public void checkCart(String skuId, String isChecked, String userId) {

        Jedis jedis = null;
        try {
            //将页面的商品id和redis中的比较，
            jedis = redisUtil.getJedis();
            String cartKey = CartConst.USER_KEY_PREFIX+userId+ CartConst.USER_CART_KEY_SUFFIX;
            String cartInfoJson = jedis.hget(cartKey, skuId);

            CartInfo cartInfo = JSON.parseObject(cartInfoJson, CartInfo.class);
            //修改选中状态
            cartInfo.setIsChecked(isChecked);
            //将对象写回redis中
            jedis.hset(cartKey,skuId,JSON.toJSONString(cartInfo));
            //创建新key存储选中的商品
            String cartCheckedKey = CartConst.USER_KEY_PREFIX + userId + CartConst.USER_CHECKED_KEY_SUFFIX;

            if ("1".equals(isChecked)){
                jedis.hset(cartCheckedKey,skuId,JSON.toJSONString(cartInfo));
            }else {
                jedis.hdel(cartCheckedKey,skuId);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (jedis != null){
                jedis.close();
            }
        }
    }

    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        List<CartInfo> cartInfoList = new ArrayList<>();
        Jedis jedis = null;
        try {
            jedis = redisUtil.getJedis();
            String cartCheckedKey = CartConst.USER_KEY_PREFIX + userId + CartConst.USER_CHECKED_KEY_SUFFIX;
            List<String> stringList = jedis.hvals(cartCheckedKey);

            for (String cartInfoJson : stringList) {
                CartInfo cartInfo = JSON.parseObject(cartInfoJson, CartInfo.class);
                cartInfoList.add(cartInfo);
            }

            return cartInfoList;

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (jedis != null){
                jedis.close();
            }
        }
        return null;
    }

    private List<CartInfo> loadCartCache(String userId) {
        List<CartInfo> cartInfoList = cartInfoMapper.selectCartListWithCurPrice(userId);

        if (cartInfoList == null || cartInfoList.size() == 0){
            return null;
        }

        Jedis jedis = null;

        try {
            jedis = redisUtil.getJedis();

            String cartKey = CartConst.USER_KEY_PREFIX + userId + CartConst.USER_CART_KEY_SUFFIX;

            HashMap<String, String> map = new HashMap<>();
            for (CartInfo cartInfo : cartInfoList) {

                map.put(cartInfo.getSkuId(),JSON.toJSONString(cartInfo));
            }

            jedis.hmset(cartKey,map);

            return cartInfoList;

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (jedis != null){
                jedis.close();
            }
        }
        return cartInfoList;
    }
}
