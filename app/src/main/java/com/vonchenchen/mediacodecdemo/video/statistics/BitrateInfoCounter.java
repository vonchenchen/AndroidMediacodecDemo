package com.vonchenchen.mediacodecdemo.video.statistics;

import com.vonchenchen.mediacodecdemo.utils.NumUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class BitrateInfoCounter {

    private List<Integer> mBitrateList;
    private int mSum;

    public BitrateInfoCounter(){

        mBitrateList = new LinkedList<>();
    }

    public void addData(int data){

        mBitrateList.add(data);
    }

    public BitrateInfo count(int target){

        BitrateInfo bitrateInfo = new BitrateInfo();

        mSum = 0;

        if(mBitrateList.size() <= 0){
            return bitrateInfo;
        }

        bitrateInfo.min = mBitrateList.get(0);
        bitrateInfo.max = mBitrateList.get(0);

        for(int i=0; i<mBitrateList.size(); i++){

            int data = mBitrateList.get(i);
            if(data > bitrateInfo.max){
                bitrateInfo.max = data;
            }else if(data < bitrateInfo.min){
                bitrateInfo.min = data;
            }

            mSum += Math.pow((data - target), 2);
        }

        bitrateInfo.variance = ((double)mSum)/mBitrateList.size();
        bitrateInfo.standardDevivation = Math.sqrt(bitrateInfo.variance);

        mBitrateList.clear();
        return bitrateInfo;
    }

    public class BitrateInfo{
        public int max;
        public int min;
        /** 方差 */
        public double variance;
        /** 标准差 */
        public double standardDevivation;

        @Override
        public String toString() {
            return "BitrateInfo{" +
                    "max=" + max/1000+"k" +
                    ", min=" + min/1000+"k" +
                    "\n standardDevivation=" + NumUtils.keepTwoDecimals(standardDevivation/1000)+"k" +
                    '}';
        }
    }
}
