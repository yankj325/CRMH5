package com.sail.simonli.server.weixinapi.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认存储与内存中
 */
public class DefaultAccessTokenCache implements IAccessTokenCache {

    private Map<String, String> map = new ConcurrentHashMap<String, String>();

    public String get(String key) {
        return map.get(key);
    }

    public void set(String key, String jsonValue) {
        map.put(key, jsonValue);
    }

    public void remove(String key) {
        map.remove(key);
    }

}