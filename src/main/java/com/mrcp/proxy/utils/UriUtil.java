package com.mrcp.proxy.utils;

import java.util.HashMap;
import java.util.Map;

public class UriUtil {

    public static Map<String, String> processRequestParameter(String data) {
        Map<String, String> params = new HashMap<String, String>();
        String[] keyValues;
        if (data.indexOf("&") != -1) {
            keyValues = data.split("&");
        } else {
            keyValues = new String[] { data };
        }
        for (String item : keyValues) {
            if (item.indexOf("=") != -1) {
                String[] tmp = item.split("=");
                if (tmp.length != 0) {
                    if (tmp.length == 1) {
                        params.put(tmp[0], "");
                    }
                    if (tmp.length == 2) {
                        params.put(tmp[0], tmp[1]);
                    }
                }
            }
        }
        return params;
    }


}
