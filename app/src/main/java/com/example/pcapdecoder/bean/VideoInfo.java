package com.example.pcapdecoder.bean;

/**
 * Created by h26376 on 2017/11/27.
 */

public class VideoInfo {
    private int ip_src;
    private int ip_dst;
    private int ssrc;
    private int type_num;
    private int pkt_count;
    private String a_line;

    public int getIp_src() {
        return ip_src;
    }

    public int getIp_dst() {
        return ip_dst;
    }

    public int getSsrc() {
        return ssrc;
    }

    public int getType_num() {
        return type_num;
    }

    public String getA_line() {
        return a_line;
    }

    public int getPkt_count() {
        return pkt_count;
    }

    public void setType_num(int type_num) {
        this.type_num = type_num;
    }

    @Override
    public String toString() {
        return "VideoInfo{" +
                "ip_src=" + ip_src +
                ", ip_dst=" + ip_dst +
                ", ssrc=" + ssrc +
                ", type_num=" + type_num +
                ", pkt_count=" + pkt_count +
                ", a_line='" + a_line + '\'' +
                '}';
    }

    public static byte[] intToByteArray(int a) {
        return new byte[] {
                (byte) ((a >> 24) & 0xFF),
                (byte) ((a >> 16) & 0xFF),
                (byte) ((a >> 8) & 0xFF),
                (byte) (a & 0xFF)
        };
    }

    public static String byteArrayToIP(byte[] b) {
        String ip = "";
        for(int i = 0; i<b.length; i++){
            ip += (b[i]&0x0FF);
            if(i != b.length-1){
                ip += ".";
            }
        }
        return ip;
    }

    public static int shortiToUshort(int s){
        return s&0x0FFFF;
    }

    public static long intToUint(long i){
        return i&0x0FFFFFFFFL;
    }
}