package com.chenliang.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.chenliang.mapper.StockMapper;
import com.chenliang.pojo.Stock;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
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
     * 防误删（A请求释放了B请求的锁）--谁加锁，谁才能释放锁。通过set key value 的value来验证。
     * 自动续期
     */


    /**
     * 1.实现原理，setnx key value
     *      REDIS 分布式锁 简单实现  --setnx key value 来实现
     * 2.防止死锁：
     *      给锁加过期时间，防止死锁。setnx
     *      SET key value ex 3 nx ,这条redis命令可以保证setnx和设置过期时间两个操作的原子性。
     *3.防误删：
     *  再改造一下：为了防止误删锁发生，我们通过给value设置uuid值的方法来防误删
     *4.代码执行时间超过锁设置的过期时间问题：
     *   通过自动续期来解决。
     *5.释放锁：查询uuid判断uuid是否相等 和 释放锁的操作，需要【原子性】
     *否则会出现问题：
     *      请求A再删除锁之前，会判断uuid是否相同，判断完成后，还没等到删除key（释放锁）就因为锁的过期时间到了，锁被释放了；
     *      此时其他请求就可以获取锁。请求A接着执行代码，就会误删请求B的锁。
     *      请求C就能获取锁。
     */
    public void deduct (){
        String uuid = UUID.randomUUID().toString();

        while (!this.redisTemplate.opsForValue().setIfAbsent("lock", uuid, 3, TimeUnit.SECONDS)){
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
            //防误删
//            if(StringUtils.equals(redisTemplate.opsForValue().get("lock"),uuid)){
//                this.redisTemplate.delete("lock");
//            }
            //防误删----》改进版：lua脚本保证判断和删除原子性
            String luaScript = "if redis.call('get',KEYS[1])==ARGV[1] " +
                    "then " +
                    "return redis.call('del',KEYS[1]) " +
                    "else return 0 " +
                    "end";
            redisTemplate.execute(new DefaultRedisScript<>(luaScript,Boolean.class), Arrays.asList("lock"),uuid);
        }
    }
    /**
     * REDIS 乐观锁 watch来实现 防止超买
     */
    public void redisWatchDeduct(){
        redisTemplate.execute(new SessionCallback<Object>() {
            @Override
            public  Object execute(RedisOperations operations) throws DataAccessException {
                //watch
                operations.watch( "stock");
                //1.查询库存信息
                String stock = operations.opsForValue().get("stock").toString();
                //2.判断库存是否充足
                if (stock != null && stock.length() != 0) {
                    Integer st = Integer.valueOf(stock);
                    if (st > 0) {
                        //multi
                        operations.multi();
                        //3.扣减库存
                        redisTemplate.opsForValue().set("stock", String.valueOf(--st));
                        //exec
                        List<Object> exec = operations.exec();
                        if (exec == null || exec.size() == 0) {
                            deduct();
                        }
                        return exec;
                    }
                }
                return null;
            }
        });
    }

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
