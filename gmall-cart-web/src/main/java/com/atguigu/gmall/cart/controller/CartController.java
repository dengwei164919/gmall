package com.atguigu.gmall.cart.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.config.LoginRequire;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.ManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

@Controller
public class CartController {

    @Reference
    private CartService cartService;

    @Autowired
    private CartCookieHandler cartCookieHandler;
    @Reference
    private ManageService manageService;

    @RequestMapping("addToCart")
    @LoginRequire(autoRedirect = false)
    public String addToCart(HttpServletRequest request, HttpServletResponse response){

        String skuNum = request.getParameter("skuNum");
        String skuId = request.getParameter("skuId");

        //请求先走拦截器，先判断reqeust域中是否有userId，如果有，走数据库和缓存，如果没有，走cookie
        String userId = (String) request.getAttribute("userId");

        if (userId != null){
            cartService.addToCart(skuId,userId,Integer.parseInt(skuNum));
        }else {
            cartCookieHandler.addToCart(request,response,skuId,userId,Integer.parseInt(skuNum));
        }

        SkuInfo skuInfo = manageService.getSkuInfo(skuId);

        request.setAttribute("skuNum",skuNum);
        request.setAttribute("skuInfo",skuInfo);

        return "success";
    }

    @RequestMapping("cartList")
    @LoginRequire(autoRedirect = false)
    public String cartList(HttpServletRequest request,HttpServletResponse response){

        String userId = (String) request.getAttribute("userId");
        List<CartInfo> cartInfoList = new ArrayList<>();
        if (userId != null){
            //从cookie中查找购物车
            List<CartInfo> cartListCK = cartCookieHandler.getCartList(request);
            if (cartListCK != null && cartListCK.size() > 0){
                cartInfoList = cartService.mergeToCartList(cartListCK,userId);
                cartCookieHandler.deleteCartCookie(request,response);
            }else {
                cartInfoList = cartService.getCartList(userId);
            }
        }else {
            cartInfoList = cartCookieHandler.getCartList(request);
        }

        request.setAttribute("cartInfoList",cartInfoList);

        return "cartList";

    }

    @RequestMapping("checkCart")
    @ResponseBody
    @LoginRequire(autoRedirect = false)
    public void checkCart(HttpServletRequest request,HttpServletResponse response){

        String userId = (String) request.getAttribute("userId");
        String isChecked = request.getParameter("isChecked");
        String skuId = request.getParameter("skuId");

        if (userId != null){
            cartService.checkCart(skuId,isChecked,userId);
        }else {
            cartCookieHandler.checkCart(request,response,skuId,isChecked);
        }
    }

    @RequestMapping("toTrade")
    @LoginRequire(autoRedirect = true)
    public String toTrade(HttpServletRequest request,HttpServletResponse response){

        String userId = (String) request.getAttribute("userId");

        List<CartInfo> cartList = cartCookieHandler.getCartList(request);
        if (cartList != null && cartList.size() > 0){
            cartService.mergeToCartList(cartList,userId);

            cartCookieHandler.deleteCartCookie(request,response);
        }

        return "redirect://trade.gmall.com/trade";
    }
}
