package com.chenliang.controller;

import com.chenliang.service.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author chen
 * @date 2023-06-11 21:45
 */
@RestController
public class StockController {
    @Autowired
    private StockService stockService;

    private int count = 0;
    @GetMapping("/stock/deduct")
    public String deduct (){
        stockService.deduct();
//        System.out.println(count++);
        return "hello stock deduct!!";
    }
}
