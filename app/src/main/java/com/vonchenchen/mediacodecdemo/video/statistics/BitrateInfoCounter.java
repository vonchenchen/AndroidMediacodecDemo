package com.vonchenchen.mediacodecdemo.video.statistics;

import com.vonchenchen.mediacodecdemo.utils.NumUtils;

import java.util.LinkedList;
import java.util.List;

public class BitrateInfoCounter {

    private List<Integer> mBitrateList;

    public BitrateInfoCounter(){

        mBitrateList = new LinkedList<>();
    }

    public void addData(int data){

        mBitrateList.add(data);
    }

    public BitrateInfo count(int target){

        BitrateInfo bitrateInfo = new BitrateInfo();

        int standCntSum = 0;
        int sum = 0;

        if(mBitrateList.size() <= 0){
            return bitrateInfo;
        }

        bitrateInfo.min = mBitrateList.get(0)*8;
        bitrateInfo.max = mBitrateList.get(0)*8;

        for(int i=0; i<mBitrateList.size(); i++){

            int data = mBitrateList.get(i);

            if(data > bitrateInfo.max){
                bitrateInfo.max = data;
            }else if(data < bitrateInfo.min){
                bitrateInfo.min = data;
            }

            sum += data;
        }

        bitrateInfo.average = sum/mBitrateList.size();

        for(int i=0; i<mBitrateList.size(); i++){

            int data = mBitrateList.get(i);
            standCntSum += Math.pow((data - bitrateInfo.average), 2);
        }
        bitrateInfo.variance = ((double) standCntSum)/mBitrateList.size();
        bitrateInfo.standardDevivation = Math.sqrt(bitrateInfo.variance);

        mBitrateList.clear();
        return bitrateInfo;
    }

    public class BitrateInfo{
        public int max;
        public int min;

        /** 平均数 */
        public int average;
        /** 方差 */
        public double variance;
        /** 误差 */
        public double errorDevivation;
        /** 标准差 */
        public double standardDevivation;

        @Override
        public String toString() {
            return  max/1000+"k" +
                    "-" + min/1000+"k" +
                    "-" + NumUtils.keepTwoDecimals(average/1000)+"k" +
                    "-" + NumUtils.keepTwoDecimals(standardDevivation /1000)+"k";
        }
    }
}
