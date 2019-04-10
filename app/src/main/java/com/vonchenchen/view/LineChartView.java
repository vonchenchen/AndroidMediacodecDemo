package com.vonchenchen.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import com.vonchenchen.mediacodecdemo.R;

public class LineChartView extends RelativeLayout{

    private Context mContext;

    public LineChartView(Context context) {
        super(context, null);
    }

    public LineChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(mContext, R.layout.view_line_chart, this);
    }


}
