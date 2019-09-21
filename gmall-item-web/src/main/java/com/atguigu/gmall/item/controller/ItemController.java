package com.atguigu.gmall.item.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.bean.SkuSaleAttrValue;
import com.atguigu.gmall.bean.SpuSaleAttr;
import com.atguigu.gmall.config.LoginRequire;
import com.atguigu.gmall.service.ListService;
import com.atguigu.gmall.service.ManageService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ItemController {

    @Reference
    private ManageService manageService;

    @Reference
    private ListService listService;

    @RequestMapping("{skuId}.html")
    @LoginRequire(autoRedirect = false)
    public String getItem(@PathVariable String skuId, HttpServletRequest request){

        //查询skuInfo信息,并且查询图片列表
        SkuInfo skuInfo = manageService.getSkuInfo(skuId);

        //查询销售属性和小鼠属性值集合，并锁定当前sku
        List<SpuSaleAttr> spuSaleAttrList = manageService.getSpuSaleAttrListCheckBySku(skuInfo);

        //获取所有销售属性值的组成的skuId集合
        List<SkuSaleAttrValue> skuSaleAttrValueList = manageService.getSkuSaleAttrValueListBySpu(skuInfo.getSpuId());
        String key = "";
        Map<String,String> map = new HashMap<>();
        for (int i = 0; i < skuSaleAttrValueList.size(); i++) {

            SkuSaleAttrValue skuSaleAttrValue = skuSaleAttrValueList.get(i);

            if (key.length() > 0){
                key+="|";
            }

            key+=skuSaleAttrValue.getSaleAttrValueId();

            if ((i+1) == skuSaleAttrValueList.size() || !skuSaleAttrValue.getSkuId().equals(skuSaleAttrValueList.get(i+1).getSkuId())){
                map.put(key,skuSaleAttrValue.getSkuId());
                key = "";
            }
        }

        String valuesSkuJson = JSON.toJSONString(map);

        request.setAttribute("valuesSkuJson",valuesSkuJson);

        request.setAttribute("spuSaleAttrList",spuSaleAttrList);

        request.setAttribute("skuInfo",skuInfo);

        //增加评分
        listService.incrHotScore(skuId);
        return "item";
    }
}
