package com.vonchenchen.mediacodecdemo.utils;

import java.math.BigDecimal;

public class NumUtils {

    /**
     * 保留两位小数
     * @param data
     * @return
     */
    public static String keepTwoDecimals(double data){
        return String.format("%.2f", data);
    }

    /**
     * 将浮点数字符串
     * @param numStr
     * @param reserve
     * @return
     */
    public static String roundFloatString(String numStr, int reserve){
        BigDecimal decimal = new BigDecimal(numStr);
        BigDecimal setScale = decimal.setScale(reserve,BigDecimal.ROUND_HALF_DOWN);
        return setScale.toString();
    }
}
