package com.example.pcapdecoder.utils;

import com.example.pcapdecoder.R;

/**
 * Created by h26376 on 2017/9/12.
 */

public class ConstantUtils {
    public static int getColorByType(String type){
        switch (type){
            case "mp4":
                return R.color.type_color_0;
            case "yuv":
                return R.color.type_color_1;
            case "h264":
                return R.color.type_color_2;
            case "mkv":
                return R.color.type_color_3;
            case "flv":
                return R.color.type_color_4;
            case "avi":
                return R.color.type_color_5;
            case "mpg":
                return R.color.type_color_6;
            case "wmv":
                return R.color.type_color_7;
            case "mov":
                return R.color.type_color_8;
            case "rmvb":
                return R.color.type_color_9;
            case "ts":
                return R.color.type_color_10;
            case "http":
                return R.color.type_color_11;
            case "rtcp":
                return R.color.type_color_12;
            case "rtp":
                return R.color.type_color_13;
            case "file":
                return R.color.type_color_14;
            default:
                return R.color.type_color_15;
        }
    }

    public static int getColorByID(int id){
        switch (id){
            case 0:
                return R.color.type_color_0;
            case 1:
                return R.color.type_color_1;
            case 2:
                return R.color.type_color_2;
            case 3:
                return R.color.type_color_3;
            case 4:
                return R.color.type_color_4;
            case 5:
                return R.color.type_color_5;
            case 6:
                return R.color.type_color_6;
            case 7:
                return R.color.type_color_7;
            case 8:
                return R.color.type_color_8;
            case 9:
                return R.color.type_color_9;
            case 10:
                return R.color.type_color_10;
            case 11:
                return R.color.type_color_11;
            case 12:
                return R.color.type_color_12;
            case 13:
                return R.color.type_color_13;
            case 14:
                return R.color.type_color_14;
            default:
                return R.color.type_color_15;
        }
    }
    public static int SUM_ID = 15;

    public static boolean isStreamMedia(String type){
        switch (type){
            case "http":
                return true;
            case "rtsp":
                return true;
            case "rtp":
                return true;
            case "file":
                return true;
            default:
                return false;
        }
    }
}