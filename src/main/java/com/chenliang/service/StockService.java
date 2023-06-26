package com.chenliang.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.chenliang.mapper.StockMapper;
import com.chenliang.pojo.Stock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author chen
 * @date 2023-06-11 21:43
 * 默认单例的，只有一个service
 */
@Service
//@Scope(value = "prototype",proxyMode = ScopedProxyMode.TARGET_CLASS)
public class StockService {
//    private Stock stock = new Stock();

    @Autowired
    private StockMapper stockMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    //redis为共享资源，高并发会出现超买
//    public void deduct(){
//        //1.查询库存
//        String stock = this.redisTemplate.opsForValue().get("stock");
//        //2.判断库存是否充足
//        if(stock != null && stock.length() != 0){
//            Integer st = Integer.valueOf(stock);
//            if(st > 0){
//                //3.扣减库存
//                this.redisTemplate.opsForValue().set("stock",String.valueOf(--st));
//            }
//        }
//    }

    /**
     * REDIS 分布式锁 简单实现  --setnx key value 来实现
     */
    public void deduct (){
        while (!this.redisTemplate.opsForValue().setIfAbsent("lock", "1111", 3, TimeUnit.SECONDS)){
            //重试，循环
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        try{
            //1.查询库存
            String stock = this.redisTemplate.opsForValue().get("stock").toString();
            //2.判断库存是否足够
            if(stock != null && stock.length() != 0){
                Integer st = Integer.valueOf(stock);
                if(st>0){
                    //3.扣减库存
                    this.redisTemplate.opsForValue().set("stock",String.valueOf(--st));
                }
            }
        }finally {
            this.redisTemplate.delete("lock");
        }
    }
    /**
     * REDIS 乐观锁 watch来实现 防止超买
     */
//    public void deduct(){
//        redisTemplate.execute(new SessionCallback<Object>() {
//            @Override
//            public  Object execute(RedisOperations operations) throws DataAccessException {
//                //watch
//                operations.watch( "stock");
//                //1.查询库存信息
//                String stock = operations.opsForValue().get("stock").toString();
//                //2.判断库存是否充足
//                if (stock != null && stock.length() != 0) {
//                    Integer st = Integer.valueOf(stock);
//                    if (st > 0) {
//                        //multi
//                        operations.multi();
//                        //3.扣减库存
//                        redisTemplate.opsForValue().set("stock", String.valueOf(--st));
//                        //exec
//                        List<Object> exec = operations.exec();
//                        if (exec == null || exec.size() == 0) {
//                            deduct();
//                        }
//                        return exec;
//                    }
//                }
//                return null;
//            }
//        });
//    }

    //乐观锁，加version字段；update的时候判断version是否一样，如果不一样则减库存失败，再次调用方法，直到版本号没被改变。
//    public void deduct(){
//        //select ... for update 查询的时候就能加行锁了
//        List<Stock> stocks = stockMapper.selectListByProductCode("1001");
//        Stock stock = stocks.get(0);
//        Integer version = stock.getVersion();
//        Long id = stock.getId();
//        if(stock.getCount() == 0){
//            return;
//        }
//        Integer integer = stockMapper.updateByConfirmVersion(id, version);
//        if(integer==0){
//            this.deduct();
//        }
//    }

    private ReentrantLock lock = new ReentrantLock();
//    @Transactional
    /*public  void deduct(){
        try{
//            lock.lock();
            Stock stock = stockMapper.selectOne(new QueryWrapper<Stock>().eq("product_code",1001));
            if(stock != null && stock.getCount() > 0){
                stock.setCount(stock.getCount()-1);
                stockMapper.updateById(stock);
                System.out.println("库存："+stock.getCount());
            }
        }finally {
//            lock.unlock();
        }
    }*/

    /**
     * //1.一条update...where解决问题，JVM本地锁失效的问题；
     * 会造成其他问题：无法获取数据更新前后的状态，可能造成锁整张表，（索引失效）
     * mysql通过update语句来加锁，update、insert、delte都可以加锁
     */
//    public  void deduct(){
//    //            lock.lock();
//            stockMapper.deduct("1001",1);
//    }

    /**
     * 2.select ... for update -----mysql悲观锁
     * 造成问题：1.死锁2.查询都要用select ... for upodate3.性能问题
     */
    private int count = 0;
 /*   @Transactional
    public synchronized void deduct(){
        //select ... for update 查询的时候就能加行锁了
        Stock stock = stockMapper.decuctBySelectFroUpdate("1001");
        count++;
        stock.setCount(stock.getCount()-1);
        stockMapper.updateById(stock);
    }*/


}
