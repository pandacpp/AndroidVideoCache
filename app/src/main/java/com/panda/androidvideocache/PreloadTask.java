package com.panda.androidvideocache;

import com.panda.library.HttpProxyCacheServer;
import com.panda.library.Logger;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;


/**
 * 原理：主动去请求VideoCache生成的代理地址，触发VideoCache缓存机制
 * 缓存到 PreloadManager.PRELOAD_LENGTH 的数据之后停止请求，完成预加载
 * 播放器去播放VideoCache生成的代理地址的时候，VideoCache会直接返回缓存数据，
 * 从而提升播放速度
 */
public class PreloadTask implements Runnable {

    /**
     * 原始地址
     */
    public String mRawUrl;

    /**
     * 列表中的位置
     */
    public int mPosition;

    /**
     * VideoCache服务器
     */
    public HttpProxyCacheServer mCacheServer;

    /**
     * 是否被取消
     */
    private boolean mIsCanceled;

    /**
     * 是否正在预加载
     */
    private boolean mIsExecuted;

    private final static List<String> blackList = new ArrayList<>();

    @Override
    public void run() {
        if (!mIsCanceled) {
            start();
        }
        mIsExecuted = false;
        mIsCanceled = false;
    }

    /**
     * 开始预加载
     */
    private void start() {
        // 如果在小黑屋里不加载
        if (blackList.contains(mRawUrl)) return;
        Logger.info("预加载开始：" + mPosition);
        HttpURLConnection connection = null;
        try {
            //获取HttpProxyCacheServer的代理地址
            String proxyUrl = mCacheServer.getProxyUrl(mRawUrl);
            URL url = new URL(proxyUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            InputStream in = new BufferedInputStream(connection.getInputStream());
            int length;
            int read = -1;
            byte[] bytes = new byte[8 * 1024];
            while ((length = in.read(bytes)) != -1) {
                read += length;
                //预加载完成或者取消预加载
                if (mIsCanceled || read >= PreloadManager.PRELOAD_LENGTH) {
                    if (mIsCanceled) {
                        Logger.info("预加载取消：" + mPosition + " 读取数据：" + read + " Byte");
                    } else {
                        Logger.info("预加载成功：" + mPosition + " 读取数据：" + read + " Byte");
                    }
                    break;
                }
            }
        } catch (Exception e) {
            Logger.info("预加载异常：" + mPosition + " 异常信息：" + e.getMessage());
            // 关入小黑屋
            blackList.add(mRawUrl);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            Logger.info("预加载结束: " + mPosition);
        }
    }

    /**
     * 将预加载任务提交到线程池，准备执行
     */
    public void executeOn(ExecutorService executorService) {
        if (mIsExecuted) return;
        mIsExecuted = true;
        executorService.submit(this);
    }

    /**
     * 取消预加载任务
     */
    public void cancel() {
        if (mIsExecuted) {
            mIsCanceled = true;
        }
    }
}
