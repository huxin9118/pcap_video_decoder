/*
 *  COPYRIGHT NOTICE
 *  Copyright (C) 2017, huxin9118 <huxin9118@163.com>
 *  https://github.com/huxin9118/pcap-video-decoder
 *
 *  @license under the Apache License, Version 2.0
 *
 *  @file    native_video.c
 *
 *  @author  huxin
 *  @date    2017/10/10
 */
#include <stdio.h>
#include <jni.h>
#include <android/log.h>
#include <pthread.h>
#include <unistd.h>

#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libswresample/swresample.h"
#include "libavutil/log.h"
#include "ff_tcpip.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO , "libpcap", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR , "libpcap", __VA_ARGS__)

#define PCAP_BUFFER_SIZE  1024*128
#define PCAP_BOTTOM_ALERT  1024*32

#define MAX_VIDEO_INFO_SIZE 100
#define MAX_FRAMES_PER_PKT 100

#define SAMPLERATE 8000
#define CHANNELS 1
#define PERIOD_TIME 20 //ms
#define FRAME_SIZE SAMPLERATE*PERIOD_TIME/1000

#define MAX_AUDIO_PACKET_SIZE 2048
#define MAX_AUDIO_FRAME_SIZE 192000 // 1 second of 48khz 32bit audio

#define FRAME_BUFFER_SIZE 2048
#define VOC_DECODE_BUFFER_SIZE 3
#define FEC_DECODE_BUFFER_SIZE 72
#define FEC_FRAME_SIZE 9
#define NO_FEC_FRAME_SIZE 6
//#define FRAMES_PER_PKT 3

u_short switchUshort(u_short s){//16bit大小端转换
	return ((s & 0x00FF) << 8) | ((s & 0xFF00) >> 8);
}

u_int32 switchUint32(u_int32 i){//32bit大小端转换
	return ((i & 0x000000FF) << 24) | ((i & 0x0000FF00) << 8) | ((i & 0x00FF0000) >> 8) | ((i & 0xFF000000) >> 24);
}

int checkoutAllZero(char* payload, int payload_len){
	for(int i = 0; i < payload_len; i++){
		if(payload[i] != 0x00) {
			return 0; 
		}
	}
	return 1;
}

int pollVideoBuffer(VIDEO_BUFFERS* buffers, VIDEO_BUFFER** buffer){
	if(buffers->size > 0){
		*buffer = (VIDEO_BUFFER*)malloc(sizeof(VIDEO_BUFFER));
		memmove(*buffer, &buffers->buffer[0], sizeof(VIDEO_BUFFER));
		//LOGI("====seq:%d\t video_buffers.size:%d",buffers->buffer[0].seq,buffers->size);
		for(int i = 1; i <= buffers->size - 1; i++){
			buffers->buffer[i-1].buffer_data = buffers->buffer[i].buffer_data;
			buffers->buffer[i-1].buffer_size = buffers->buffer[i].buffer_size;
			buffers->buffer[i-1].seq = buffers->buffer[i].seq;
			buffers->buffer[i-1].mode = buffers->buffer[i].mode;
			buffers->buffer[i-1].nal_unit_type = buffers->buffer[i].nal_unit_type;
			buffers->buffer[i-1].S = buffers->buffer[i].S;
			buffers->buffer[i-1].E = buffers->buffer[i].E;
		}
		buffers->size--;
		buffers->buffer[buffers->size].buffer_data = 0;
		buffers->buffer[buffers->size].buffer_size = 0;
		buffers->buffer[buffers->size].seq = 0;
		buffers->buffer[buffers->size].mode = 0;
		buffers->buffer[buffers->size].nal_unit_type = 0;
		buffers->buffer[buffers->size].S = 0;
		buffers->buffer[buffers->size].E = 0;
		return 0;
	}
	return -1;
}

int setVideoBuffer(VIDEO_BUFFERS* buffers, u_char* buffer_data, int buffer_size, u_short seq, char mode, char nal_unit_type, char S, char E){
	if(buffers->size < MAX_VIDEO_BUFFERS_SIZE){
		if(buffers->size == 0){
			buffers->buffer[0].buffer_data = buffer_data;
			buffers->buffer[0].buffer_size = buffer_size;
			buffers->buffer[0].seq = seq;
			buffers->buffer[0].mode = mode;
			buffers->buffer[0].nal_unit_type = nal_unit_type;
			buffers->buffer[0].S = S;
			buffers->buffer[0].E = E;
			buffers->size = 1;
		}
		else{
			int first = 0;
			for(int i = 0; i < buffers->size; i++){
				if(buffers->buffer[i].seq != 0){
					first = i;
					break;
				}
			}
			int index = seq - buffers->buffer[first].seq + first;
			//LOGI("###seq: %5d, index:%5d,",seq,index);
			//LOGI("###first: %5d, findex:%5d,",buffers->seq[first],first);
			if(index < 0){
				return -1;
			}
			buffers->buffer[index].buffer_data = buffer_data;
			buffers->buffer[index].buffer_size = buffer_size;
			buffers->buffer[index].seq = seq;
			buffers->buffer[index].mode = mode;
			buffers->buffer[index].nal_unit_type = nal_unit_type;
			buffers->buffer[index].S = S;
			buffers->buffer[index].E = E;
			if(index+1 >= buffers->size){
				buffers->size = index + 1;
			}
		}
		return 0;
	}
	return -1;
}

void clearYUVBuffer(YUV_BUFFERS* buffers){
	if(buffers->size > 0){
		for(int i = 0; i <= buffers->size - 1; i++){
			free(buffers->buffer[i].buffer_data);
		}
		buffers->size = 0;
	}
}

int pollYUVBuffer(YUV_BUFFERS* buffers, YUV_BUFFER** buffer){
	if(buffers->size > 0){
		*buffer = (YUV_BUFFER*)malloc(sizeof(YUV_BUFFER));
		memmove(*buffer, &buffers->buffer[0], sizeof(YUV_BUFFER));
		for(int i = 1; i <= buffers->size - 1; i++){
			buffers->buffer[i-1].buffer_data = buffers->buffer[i].buffer_data;
			buffers->buffer[i-1].buffer_size = buffers->buffer[i].buffer_size;
		}
		buffers->buffer[buffers->size].buffer_data = 0;
		buffers->buffer[buffers->size].buffer_size = 0;
		buffers->size--;
		return 0;
	}
	return -1;
}

int putYUVBuffer(YUV_BUFFERS* buffers, u_char* buffer_data,int buffer_size){
	if(buffers->size < MAX_YUV_BUFFERS_SIZE){
		buffers->buffer[buffers->size].buffer_data = buffer_data;
		buffers->buffer[buffers->size].buffer_size = buffer_size;
		buffers->size++;
		return 0;
	}
	return -1;
}

// int fill_iobuffer(void* opaque,uint8_t *buf, int bufsize){  
	// VIDEO_BUFFERS* buffers = (VIDEO_BUFFERS*)opaque;
	// LOGI("seq:%d\t video_buffers.size:%d",buffers->seq[0],buffers->size);
	// if(buffers->hdr_size > 0){
		// int hdr_size = buffers->hdr_size;
		// //buf = (uint8_t *)buffers->hdr;
		// memcpy(buf, buffers->hdr, hdr_size);
		// buffers->hdr_size = -1;
		// //LOGE("========%d,%d,%s",bufsize,hdr_size,buffers->hdr);
		// return hdr_size;
	// }	
	// else if(buffers->size > 0){
		// char* audio_buffer = NULL;
		// pollAudioBuffer(buffers,&audio_buffer);
		// //buf = (uint8_t *)audio_buffer;
		// memcpy(buf, audio_buffer, buffers->buffer_size);
		// if(audio_buffer != NULL)
			// free(audio_buffer);
		// //LOGE("##########%d",buffers->buffer_size);
		// return buffers->buffer_size;
	// } 
	// else{
		// //LOGE("+++++++++++");
		// return -1;
	// }
	// // if(!feof(fp_open)){  
		// // int true_size=fread(buf,1,bufsize,fp_open);  
		// // LOGE("==========%d,%d",bufsize,true_size);
		// // return true_size;  
	// // }else{  
		// // LOGE("+++++++++++");
		// // return -1;  
	// // }  
// }  

int strfind(char *str,char *sub, int str_len, int sub_len){
	//判断字符串长度，然后从第一个开始匹配
	for (int i=0; i<str_len; i++) {
		for (int j=0; j<sub_len; j++) {
			if (*(str+i+j)!=*(sub+j)) {
				break;
			}//如果不相等则跳出这个for循环
			if (j==sub_len - 1) {
				//如果j等于子串的长度则证明已经匹配，则返回子串开始匹配的位置
				return i;
			}
			//若字符相等则开始比较下个字符
		}
	}
	return -1;
}

int getVideoInfosIndex(VIDEO_INFO* videoInfos,int infoSize,u_int32 ip_src,u_int32 ip_dst, u_int32 ssrc){
	for(int i = 0; i<infoSize; i++){
		if(videoInfos[i].ip_src == ip_src && videoInfos[i].ip_dst == ip_dst && videoInfos[i].ssrc == ssrc){
			return i;
		}
	}
	return -1;
}

JNIEXPORT jint JNICALL Java_com_example_pcapdecoder_activity_MainActivity_parsePktInfo(JNIEnv *env, jobject thiz, jstring pcap_jstr, jobject object_list, jobject object_videoInfo){
	LOGI("parsePktInfo start");
	jclass class_list = (*env)->FindClass(env,"java/util/ArrayList");
	jmethodID methodID_list_add = (*env)->GetMethodID(env,class_list,"add","(Ljava/lang/Object;)Z");
	
	jclass class_pkt = (*env)->FindClass(env,"com/example/pcapdecoder/bean/PktInfo");
	jmethodID methodID_pktInfo_constructor = (*env)->GetMethodID(env,class_pkt,"<init>","()V");
	jobject object_pktInfo;
	jfieldID fieldID_frame = (*env)->GetFieldID(env, class_pkt, "frame", "I");
	jfieldID fieldID_seq = (*env)->GetFieldID(env, class_pkt, "seq", "S");
	jfieldID fieldID_timestamp = (*env)->GetFieldID(env, class_pkt, "timestamp", "I");
	jfieldID fieldID_mode = (*env)->GetFieldID(env, class_pkt, "mode", "I");
	jfieldID fieldID_S = (*env)->GetFieldID(env, class_pkt, "S", "I");
	jfieldID fieldID_E = (*env)->GetFieldID(env, class_pkt, "E", "I");
	jfieldID fieldID_status = (*env)->GetFieldID(env, class_pkt, "status", "I");
	
	jclass class_video = (*env)->FindClass(env,"com/example/pcapdecoder/bean/VideoInfo");
	jfieldID fieldID_ip_src = (*env)->GetFieldID(env, class_video, "ip_src", "I");
	jfieldID fieldID_ip_dst = (*env)->GetFieldID(env, class_video, "ip_dst", "I");
	jfieldID fieldID_ssrc = (*env)->GetFieldID(env, class_video, "ssrc", "I");
	jfieldID fieldID_type_num = (*env)->GetFieldID(env, class_video, "type_num", "I");
	jfieldID fieldID_a_line = (*env)->GetFieldID(env, class_video, "a_line", "Ljava/lang/String;");
	jfieldID fieldID_pkt_count = (*env)->GetFieldID(env, class_video, "pkt_count", "I");
	char a_line[10]={0};
	u_int32 ip_src = (*env)->GetIntField(env, object_videoInfo, fieldID_ip_src);
    u_int32 ip_dst = (*env)->GetIntField(env, object_videoInfo, fieldID_ip_dst);
    u_int32 ssrc = (*env)->GetIntField(env, object_videoInfo, fieldID_ssrc);
    int type_num = (*env)->GetIntField(env, object_videoInfo, fieldID_type_num);
	sprintf(a_line,"%s",(*env)->GetStringUTFChars(env,(*env)->GetObjectField(env, object_videoInfo, fieldID_a_line), NULL));
	int pkt_count = (*env)->GetIntField(env, object_videoInfo, fieldID_pkt_count);
	
		
	char *pcap_buffer = (char*)malloc(PCAP_BUFFER_SIZE);
	char *pos;
	u_int32 pcap_file_linktype; //pcap帧类型
	u_int32 len_pkt_left,  //包的剩余长度
			len_read ,
			len_read_left, //剩余长度
			read_to_end=0 , //是否读取到文件尾
			len_alert = PCAP_BOTTOM_ALERT; //警戒长度，当操作指针距离缓冲区末尾小于这个值的时候，就将剩余部分移动到缓冲区头
		
	PCAP_FILE_HDR *pcap_file_header;
	PCAP_PKT_HDR *pcap_pkt_hdr;
	MAC_FRAME_HDR *frame_hdr;
	LINUX_COOKED_CAPTURE_HDR *linux_cooked_capture_hdr;
	IP_HDR *ip_hdr;
	UDP_HDR *udp_hdr;
	RTP_HDR *rtp_hdr;

	char sip_request_line[10] ={0x49, 0x4e, 0x56, 0x49, 0x54, 0x45, 0x20, 0x73, 0x69, 0x70};
	char sip_status_line[7] = {0x53, 0x49, 0x50, 0x2f, 0x32, 0x2e, 0x30};
	int sip_request_line_len = 10;
	int sip_status_line_len = 7;

	char a_line_h264[5] = {0x20, 0x48, 0x32, 0x36, 0x34}; 
	int a_line_h264_len = 5; 
	char h264_type_num[10] = {0};

	int sdp_index = 0;
	
	char rtp_packer_first = 0x80;
	char rtp_extension_packer_first = 0x90;
	int rtp_extension_hdr_size = 0;
	
	int frame_index = 0;
	int nal_unit_type = 0;
	int mode = 0;
	int S = 0;
	int E = 0;
	
	char pcap_str[500]={0};
	sprintf(pcap_str,"%s",(*env)->GetStringUTFChars(env,pcap_jstr, NULL));
	LOGI("Cpp input pcap_str:%s",pcap_str);
	LOGI("Cpp ip_src:%d",ip_src);
	LOGI("Cpp ip_dst:%d",ip_dst);
	LOGI("Cpp ssrc:%d",ssrc);
	LOGI("Cpp type_num:%d",type_num);
	LOGI("Cpp a_line:%s",a_line);
	LOGI("Cpp pkt_count:%d",pkt_count);
	
	FILE *filePCAP = fopen(pcap_str,"rb");
	if(filePCAP == NULL)
	{
		LOGE("open err!!!!");
		return -1;
	}
	
	int process = 0;
	pcap_file_header = (PCAP_FILE_HDR*)malloc(sizeof(PCAP_FILE_HDR));
	fread(pcap_file_header, 1, sizeof(PCAP_FILE_HDR), filePCAP);
	//偏移PCAP文件头
	process += sizeof(PCAP_FILE_HDR);
	
	LOGI("pcap_file_header->magic=%u",pcap_file_header->magic);
	int is_pcap_file_header_endian_switch = 0;
	if(pcap_file_header->magic == 0xA1B2C3D4){//pcap文件头标识
		is_pcap_file_header_endian_switch = 0;
	}
	else if(pcap_file_header->magic == 0xD4C3B2A1){
		is_pcap_file_header_endian_switch = 1;
	}
	else{
		LOGE("not pcap file!!!!");
		fclose(filePCAP);
		free(pcap_file_header);
		free(pcap_buffer);
		LOGI("parsePktInfo end");
		return -2;
	}	
	LOGI("is_pcap_file_header_endian_switch=%d",is_pcap_file_header_endian_switch);
	
	if(!is_pcap_file_header_endian_switch){
		pcap_file_linktype = pcap_file_header->linktype;
	}
	else{
		pcap_file_linktype = switchUint32(pcap_file_header->linktype);
	}
	
	pos = pcap_buffer;
	len_read = fread(pcap_buffer, sizeof(char), PCAP_BUFFER_SIZE ,filePCAP);
	while(1){		//如果文件读到底了，就全解析完。 否则缓冲区剩余字节小于PCAP_BOTTOM_ALERT的时候退出循环
		while( pos - pcap_buffer <  len_read - len_alert )
		{	
			pcap_pkt_hdr = (PCAP_PKT_HDR *)pos;
			if(!is_pcap_file_header_endian_switch){
				len_pkt_left = pcap_pkt_hdr->caplen;
			}
			else{
				len_pkt_left = switchUint32(pcap_pkt_hdr->caplen);
			}
			if(len_pkt_left > len_read -(pos- pcap_buffer)){
				LOGI("break: frame_index=%5d len_pkt_left=%d len_read=%5d pos- pcap_buffer=%5d",frame_index,len_pkt_left,len_read,(pos- pcap_buffer));
				break;
			}
			pos += sizeof(PCAP_PKT_HDR);

			if(pcap_file_linktype == 1){
				//LOGI("MAC_FRAME_HDR: pcap_file_linktype=%d",pcap_file_linktype);
				frame_hdr = (MAC_FRAME_HDR *)pos;
				len_pkt_left -= sizeof(MAC_FRAME_HDR);
				pos += sizeof(MAC_FRAME_HDR);
				frame_index++;
			}else if(pcap_file_linktype == 113){
				//LOGI("LINUX_COOKED_CAPTURE_HDR: pcap_file_linktype=%d",pcap_file_linktype);
				linux_cooked_capture_hdr = (LINUX_COOKED_CAPTURE_HDR *)pos;
				len_pkt_left -= sizeof(LINUX_COOKED_CAPTURE_HDR);
				pos += sizeof(LINUX_COOKED_CAPTURE_HDR);
				frame_index++;
			}
			else{
				LOGE("unknown: pcap_file_linktype=%6d",pcap_file_linktype);
			}
			
			if((pcap_file_linktype == 1 && switchUshort(frame_hdr->m_cType) == 0x0800) ||
				(pcap_file_linktype == 113 && switchUshort(linux_cooked_capture_hdr->protocol) == 0x0800)){//数据链路帧的网络层为IPv4(0x0800)
				ip_hdr = (IP_HDR *)pos;
				len_pkt_left -= sizeof(IP_HDR);
				pos += sizeof(IP_HDR);

				if(ip_hdr->ip_protocol == 17){//ip的传输层为UPD
					udp_hdr = (UDP_HDR *)pos;
					len_pkt_left -= sizeof(UDP_HDR);
					pos += sizeof(UDP_HDR);
				
					if(*(pos) == rtp_packer_first || *(pos) == rtp_extension_packer_first){//UDP负载为RTP
						rtp_hdr = (RTP_HDR*)pos;
						len_pkt_left -= sizeof(RTP_HDR);
						pos += sizeof(RTP_HDR);
						
						//pos 指向payload
						len_pkt_left -= (rtp_hdr->csrc_count) * sizeof(u_int32);
						pos += (rtp_hdr->csrc_count) * sizeof(int);
						if(rtp_hdr->extension == 0x01){
							rtp_extension_hdr_size = sizeof(RTP_EXTENSION_HDR)+ *(pos+3)*sizeof(u_int32);
							len_pkt_left -= rtp_extension_hdr_size;
							pos += rtp_extension_hdr_size;
						}

						if(rtp_hdr->payload_type == type_num && !strcmp(a_line,"H264")){//RTP负载为AMR-NB
							if(switchUint32(rtp_hdr->ssrc) == ssrc && switchUint32(ip_hdr->ip_source) == ip_src 
							&& switchUint32(ip_hdr->ip_dest) == ip_dst){//该AMR-NB包来自同一同步信源
								object_pktInfo = (*env)->NewObject(env,class_pkt,methodID_pktInfo_constructor);
								(*env)->SetIntField(env, object_pktInfo, fieldID_frame, frame_index);
								(*env)->SetShortField(env, object_pktInfo, fieldID_seq, switchUshort(rtp_hdr->seq));
								(*env)->SetIntField(env, object_pktInfo, fieldID_timestamp, switchUint32(rtp_hdr->timestamp));
								nal_unit_type = *pos & 0x1F;
								if (nal_unit_type == 28){
									mode = 1;
									S = (*(pos+1) & 0x80) >> 7;
									E = (*(pos+1) & 0x40) >> 6;
									if(S==1 && E==0){
										nal_unit_type = *(pos+1) & 0x1F;
									}
								}
								else{
									mode = 0;
								}
								(*env)->SetIntField(env, object_pktInfo, fieldID_mode, mode);
								(*env)->SetIntField(env, object_pktInfo, fieldID_S, S);
								(*env)->SetIntField(env, object_pktInfo, fieldID_E, E);
								(*env)->CallBooleanMethod(env,object_list,methodID_list_add,object_pktInfo);
								(*env)->DeleteLocalRef(env,object_pktInfo);
							}
						}
					}
				}
			}
			//pos 指向下一个pcap 包头的地址
			pos += len_pkt_left;
		}
		if(read_to_end){
			break;
		}
		len_read_left = len_read -(pos- pcap_buffer);
		process += pos- pcap_buffer;
		LOGI("len_read_left [%d]\n", len_read_left);
		//将剩余部分移动到缓冲区头 然后继续从文件中读取 PCAP_BUFFER_SIZE - len_read_left 这么长
		memmove(pcap_buffer,pos,len_read_left);
		pos = pcap_buffer + len_read_left;
		len_read = fread(pos, sizeof(char), (PCAP_BUFFER_SIZE - len_read_left),filePCAP);
		if(len_read < PCAP_BUFFER_SIZE - len_read_left )  //如果读到文件尾，就把警戒值置0，让下一个循环读完
		{
			read_to_end = 1;
			len_alert = 0;
		}
		//待处理的长度为  剩余部分 + 新读取部分
		len_read += len_read_left;
		pos = pcap_buffer;
	}
	fclose(filePCAP);
	free(pcap_file_header);
	free(pcap_buffer);
	LOGI("parsePktInfo end");
	return 0;
}

JNIEXPORT jint JNICALL Java_com_example_pcapdecoder_activity_MainActivity_parseVideoInfo(JNIEnv *env, jobject thiz, jstring pcap_jstr, jint jparse_paramter_type, jobject object_list){
	LOGI("parseVideoInfo start");
	jclass class_list = (*env)->FindClass(env,"java/util/ArrayList");
	//jmethodID methodID_list_constructor = (*env)->GetMethodID(env,class_list,"<init>","()V");
	jmethodID methodID_list_add = (*env)->GetMethodID(env,class_list,"add","(Ljava/lang/Object;)Z");
	jmethodID methodID_list_remove = (*env)->GetMethodID(env,class_list,"remove","(I)Ljava/lang/Object;");
	//jobject object_list= (*env)->NewObject(env,class_list,methodID_list_constructor);
	
	jclass class_video = (*env)->FindClass(env,"com/example/pcapdecoder/bean/VideoInfo");
	jmethodID methodID_videoInfo_constructor = (*env)->GetMethodID(env,class_video,"<init>","()V");
	jobject object_videoInfo;
	
	jfieldID fieldID_ip_src = (*env)->GetFieldID(env, class_video, "ip_src", "I");
	jfieldID fieldID_ip_dst = (*env)->GetFieldID(env, class_video, "ip_dst", "I");
	jfieldID fieldID_ssrc = (*env)->GetFieldID(env, class_video, "ssrc", "I");
	jfieldID fieldID_type_num = (*env)->GetFieldID(env, class_video, "type_num", "I");
	jfieldID fieldID_pkt_count = (*env)->GetFieldID(env, class_video, "pkt_count", "I");
	jfieldID fieldID_a_line = (*env)->GetFieldID(env, class_video, "a_line", "Ljava/lang/String;");
		
	char *pcap_buffer = (char*)malloc(PCAP_BUFFER_SIZE);
	char *pos;
	u_int32 pcap_file_linktype; //pcap帧类型
	u_int32 len_pkt_left,  //包的剩余长度
			len_read ,
			len_read_left, //剩余长度
			read_to_end=0 , //是否读取到文件尾
			len_alert = PCAP_BOTTOM_ALERT; //警戒长度，当操作指针距离缓冲区末尾小于这个值的时候，就将剩余部分移动到缓冲区头
		
	PCAP_FILE_HDR *pcap_file_header;
	PCAP_PKT_HDR *pcap_pkt_hdr;
	MAC_FRAME_HDR *frame_hdr;
	LINUX_COOKED_CAPTURE_HDR *linux_cooked_capture_hdr;
	IP_HDR *ip_hdr;
	UDP_HDR *udp_hdr;
	RTP_HDR *rtp_hdr;

	char sip_request_line[10] ={0x49, 0x4e, 0x56, 0x49, 0x54, 0x45, 0x20, 0x73, 0x69, 0x70};
	char sip_status_line[7] = {0x53, 0x49, 0x50, 0x2f, 0x32, 0x2e, 0x30};
	int sip_request_line_len = 10;
	int sip_status_line_len = 7;

	char a_line_h264[5] = {0x20, 0x48, 0x32, 0x36, 0x34}; 
	int a_line_h264_len = 5; 
	char h264_type_num[10] = {0};
	LOGI("parse_paramter_type: %d",jparse_paramter_type);
	if(jparse_paramter_type == 1){
		object_videoInfo = (*env)->CallObjectMethod(env,object_list,methodID_list_remove,0);
		jint custom_h264_type_num = (*env)->GetIntField(env, object_videoInfo, fieldID_type_num);
		object_videoInfo = NULL;
		sprintf(h264_type_num,"%d", custom_h264_type_num);
		LOGI("custom_h264_type_num: %d",custom_h264_type_num);
	}

	int sdp_index = 0;
	
	char rtp_packer_first = 0x80;
	char rtp_extension_packer_first = 0x90;
	int rtp_extension_hdr_size = 0;
	VIDEO_INFO videoInfos[MAX_VIDEO_INFO_SIZE];
	int infoSize = 0;
	int infoIndex = 0;
	
	int frame_index = 0;
	char pcap_str[500]={0};
	sprintf(pcap_str,"%s",(*env)->GetStringUTFChars(env,pcap_jstr, NULL));
	LOGI("Cpp input pcap_str:%s",pcap_str);
	
	FILE *filePCAP = fopen(pcap_str,"rb");
	if(filePCAP == NULL)
	{
		LOGE("open err!!!!");
		return -1;
	}
	
	int process = 0;
	pcap_file_header = (PCAP_FILE_HDR*)malloc(sizeof(PCAP_FILE_HDR));
	fread(pcap_file_header, 1, sizeof(PCAP_FILE_HDR), filePCAP);
	//偏移PCAP文件头
	process += sizeof(PCAP_FILE_HDR);
	
	LOGI("pcap_file_header->magic=%u",pcap_file_header->magic);
	int is_pcap_file_header_endian_switch = 0;
	if(pcap_file_header->magic == 0xA1B2C3D4){//pcap文件头标识
		is_pcap_file_header_endian_switch = 0;
	}
	else if(pcap_file_header->magic == 0xD4C3B2A1){
		is_pcap_file_header_endian_switch = 1;
	}
	else{
		LOGE("not pcap file!!!!");
		return -2;
	}	
	LOGI("is_pcap_file_header_endian_switch=%d",is_pcap_file_header_endian_switch);
	
	pos = pcap_buffer;
	len_read = fread(pcap_buffer, sizeof(char), PCAP_BUFFER_SIZE ,filePCAP);
	while(1){		//如果文件读到底了，就全解析完。 否则缓冲区剩余字节小于PCAP_BOTTOM_ALERT的时候退出循环
		while( pos - pcap_buffer <  len_read - len_alert )
		{
			pcap_pkt_hdr = (PCAP_PKT_HDR *)pos;
			if(!is_pcap_file_header_endian_switch){
				len_pkt_left = pcap_pkt_hdr->caplen;
			}
			else{
				len_pkt_left = switchUint32(pcap_pkt_hdr->caplen);
			}
			if(len_pkt_left > len_read -(pos- pcap_buffer)){
				LOGI("break: frame_index=%5d len_pkt_left=%d len_read=%5d pos- pcap_buffer=%5d",frame_index,len_pkt_left,len_read,(pos- pcap_buffer));
				break;
			}
			pos += sizeof(PCAP_PKT_HDR);

			//pos += (sizeof(MAC_FRAME_HDR) + sizeof(IP_HDR) +  sizeof(UDP_HDR));
			//len_pkt_left -=  (sizeof(MAC_FRAME_HDR) + sizeof(IP_HDR) +  sizeof(UDP_HDR));
			
			if(!is_pcap_file_header_endian_switch){
				pcap_file_linktype = pcap_file_header->linktype;
			}
			else{
				pcap_file_linktype = switchUint32(pcap_file_header->linktype);
			}
			if(pcap_file_linktype == 1){
				//LOGI("MAC_FRAME_HDR: pcap_file_linktype=%d",pcap_file_linktype);
				frame_hdr = (MAC_FRAME_HDR *)pos;
				len_pkt_left -= sizeof(MAC_FRAME_HDR);
				pos += sizeof(MAC_FRAME_HDR);
				frame_index++;
				LOGI("MAC_FRAME_HDR: pcap_file_linktype=%6d",pcap_file_linktype);
			}else if(pcap_file_linktype == 113){
				//LOGI("LINUX_COOKED_CAPTURE_HDR: pcap_file_linktype=%d",pcap_file_linktype);
				linux_cooked_capture_hdr = (LINUX_COOKED_CAPTURE_HDR *)pos;
				len_pkt_left -= sizeof(LINUX_COOKED_CAPTURE_HDR);
				pos += sizeof(LINUX_COOKED_CAPTURE_HDR);
				frame_index++;
				LOGI("LINUX_COOKED_CAPTURE_HDR: pcap_file_linktype=%6d",pcap_file_linktype);
			}
			else{
				LOGE("unknown: pcap_file_linktype=%6d",pcap_file_linktype);
			}
			
			if((pcap_file_linktype == 1 && switchUshort(frame_hdr->m_cType) == 0x0800) ||
				(pcap_file_linktype == 113 && switchUshort(linux_cooked_capture_hdr->protocol) == 0x0800)){//数据链路帧的网络层为IPv4(0x0800)
				ip_hdr = (IP_HDR *)pos;
				len_pkt_left -= sizeof(IP_HDR);
				pos += sizeof(IP_HDR);

				if(ip_hdr->ip_protocol == 17){//ip的传输层为UPD
					udp_hdr = (UDP_HDR *)pos;
					len_pkt_left -= sizeof(UDP_HDR);
					pos += sizeof(UDP_HDR);
					
					if(!strlen(h264_type_num)){
						// if(strfind(pos, sip_request_line, sip_request_line_len, sip_request_line_len)!=-1){
							// sdp_index = strfind(pos, a_line_h264, switchUshort(udp_hdr->length)-8, a_line_h264_len);
							// if(sdp_index != -1){
								// for(int i = sdp_index - 1; i>sdp_index - 10; i--){
									// if(*(pos+i) < 0x30 || *(pos+i) > 0x39){
										// if(i < sdp_index - 1){
											// memmove(h264_type_num,pos+i+1,sdp_index - 1 - i);
											// break;
										// }
									// }
								// }
							// }			
							// if(strlen(h264_type_num)){
								// sdp_index = strfind(pos, a_line_ptime, switchUshort(udp_hdr->length)-8, a_line_ptime_len);
								// if(sdp_index != -1){
									// for(int i = sdp_index + a_line_ptime_len; i < sdp_index + 10; i++){
										// if(*(pos+i) < 0x30 || *(pos+i) > 0x39){
											// break;
										// }else{
											// memmove(amr_ptime,pos+i,1);	
										// }
									// }
								// }
							// }
						// }
						if(strfind(pos, sip_status_line, sip_status_line_len, sip_status_line_len)!=-1){
							sdp_index = strfind(pos, a_line_h264, switchUshort(udp_hdr->length)-8, a_line_h264_len);
							if(sdp_index != -1){
								for(int i = sdp_index - 1; i>sdp_index - 10; i--){
									if(*(pos+i) < 0x30 || *(pos+i) > 0x39){
										if(i < sdp_index - 1){
											memmove(h264_type_num,pos+i+1,sdp_index - 1 - i);
											break;
										}
									}
								}
							}
							// if(strlen(h264_type_num)){
								// sdp_index = strfind(pos, a_line_ptime, switchUshort(udp_hdr->length)-8, a_line_ptime_len);
								// if(sdp_index != -1){
									// for(int i = sdp_index + a_line_ptime_len; i < sdp_index + 10; i++){
										// if(*(pos+i) < 0x30 || *(pos+i) > 0x39){
											// break;
										// }else{
											// memmove(amr_ptime+strlen(amr_ptime),pos+i,1);	
										// }
									// }
								// }
							// }
						}
					}
					
					if(*(pos) == rtp_packer_first || *(pos) == rtp_extension_packer_first){//UDP负载为RTP
						rtp_hdr = (RTP_HDR*)pos;
						len_pkt_left -= sizeof(RTP_HDR);
						pos += sizeof(RTP_HDR);

						//pos 指向payload
						len_pkt_left -= (rtp_hdr->csrc_count) * sizeof(u_int32);
						pos += (rtp_hdr->csrc_count) * sizeof(int);
						if(rtp_hdr->extension == 0x01){
							rtp_extension_hdr_size = sizeof(RTP_EXTENSION_HDR)+ *(pos+3)*sizeof(u_int32);
							len_pkt_left -= rtp_extension_hdr_size;
							pos += rtp_extension_hdr_size;
						}
						
						if(rtp_hdr->payload_type == atoi(h264_type_num)){
							infoIndex = getVideoInfosIndex(videoInfos,infoSize,switchUint32(ip_hdr->ip_source),switchUint32(ip_hdr->ip_dest),switchUint32(rtp_hdr->ssrc));
							if(infoIndex == -1){
								videoInfos[infoSize].ip_src = switchUint32(ip_hdr->ip_source);
								videoInfos[infoSize].ip_dst = switchUint32(ip_hdr->ip_dest);
								videoInfos[infoSize].ssrc = switchUint32(rtp_hdr->ssrc);
								if(rtp_hdr->payload_type == atoi(h264_type_num)){
									videoInfos[infoSize].type_num = atoi(h264_type_num);
									memmove(videoInfos[infoSize].a_line,a_line_h264+1,a_line_h264_len-1);
									videoInfos[infoSize].a_line[a_line_h264_len-1] = 0;
								}
								videoInfos[infoSize].pkt_count = 1;
								infoSize++;
							}
							else{
								videoInfos[infoIndex].pkt_count++;
							}
						}
					}
				}
			}
			//pos 指向下一个pcap 包头的地址
			pos += len_pkt_left;
		}
		if(read_to_end){
			break;
		}
		len_read_left = len_read -(pos- pcap_buffer);
		process += pos - pcap_buffer;
		LOGI("len_read_left [%d]\n", len_read_left);
		//将剩余部分移动到缓冲区头 然后继续从文件中读取 PCAP_BUFFER_SIZE - len_read_left 这么长
		memmove(pcap_buffer,pos,len_read_left);
		pos = pcap_buffer + len_read_left;
		len_read = fread(pos, sizeof(char), (PCAP_BUFFER_SIZE - len_read_left),filePCAP);
		if(len_read < PCAP_BUFFER_SIZE - len_read_left )  //如果读到文件尾，就把警戒值置0，让下一个循环读完
		{
			read_to_end = 1;
			len_alert = 0;
		}
		//待处理的长度为  剩余部分 + 新读取部分
		len_read += len_read_left;
		pos = pcap_buffer;
	}
	fclose(filePCAP);
	free(pcap_file_header);
	free(pcap_buffer);
	for(int i = 0; i<infoSize; i++){
		object_videoInfo = (*env)->NewObject(env,class_video,methodID_videoInfo_constructor);
		(*env)->SetIntField(env, object_videoInfo, fieldID_ip_src, videoInfos[i].ip_src);
		(*env)->SetIntField(env, object_videoInfo, fieldID_ip_dst, videoInfos[i].ip_dst);
		(*env)->SetIntField(env, object_videoInfo, fieldID_ssrc, videoInfos[i].ssrc);
		(*env)->SetIntField(env, object_videoInfo, fieldID_type_num, videoInfos[i].type_num);
		(*env)->SetIntField(env, object_videoInfo, fieldID_pkt_count, videoInfos[i].pkt_count);
		(*env)->SetObjectField(env, object_videoInfo, fieldID_a_line, (*env)->NewStringUTF(env,videoInfos[i].a_line));
		
		(*env)->CallBooleanMethod(env,object_list,methodID_list_add,object_videoInfo);
		(*env)->DeleteLocalRef(env,object_videoInfo);
	}
	LOGI("parseVideoInfo end");
	return 0;
}

typedef struct info_str{
	char * pcap_str;
	char * video_str;
	char * yuv_str;
	u_int32 ip_src;
	u_int32 ip_dst;
	u_int32 ssrc;
	char * a_line;
	int type_num;
	int pkt_count;
	JNIEnv* env;
} INFO_STR;

static volatile int isPlay = 0;
static volatile int parse_exit = 0;
static int returnValue = 1;
static YUV_BUFFERS yuv_buffers;
static JavaVM* gs_jvm;
static jclass gs_class;
static pthread_mutex_t mutex;

void *decode(void *argv)
{
	LOGI("decode start");
	returnValue = 1;
	char *pcap_buffer = (char*)malloc(PCAP_BUFFER_SIZE);
	char *pos;
	u_int32 pcap_file_linktype; //pcap帧类型
	u_int32 len_pkt_left,  //包的剩余长度
			len_read ,
			len_read_left, //剩余长度
			read_to_end=0 , //是否读取到文件尾
			len_alert = PCAP_BOTTOM_ALERT; //警戒长度，当操作指针距离缓冲区末尾小于这个值的时候，就将剩余部分移动到缓冲区头
		
	PCAP_FILE_HDR *pcap_file_header;
	PCAP_PKT_HDR *pcap_pkt_hdr;
	MAC_FRAME_HDR *frame_hdr;
	LINUX_COOKED_CAPTURE_HDR *linux_cooked_capture_hdr;
	IP_HDR *ip_hdr;
	UDP_HDR *udp_hdr;
	RTP_HDR *rtp_hdr;

	char rtp_packer_first = 0x80;
	char rtp_extension_packer_first = 0x90;
	int rtp_extension_hdr_size = 0;
	u_short rtp_seq = 0;
	
	u_char start_code[4] = {0x00, 0x00, 0x00, 0x01};
	u_char start_code_len = 4;

	VIDEO_BUFFERS video_buffers;
	memset(&video_buffers, 0, sizeof(VIDEO_BUFFERS));
	memset(&yuv_buffers, 0, sizeof(YUV_BUFFERS));
	u_char* video_buffer_data;
	int video_buffer_size;
	char mode; //0--single 1--FU-A
	char nal_unit_type;
	char S;
	char E;
	
	int video_index = 0;
	VIDEO_BUFFER *video_buffer;
	u_char nalu_header;

	INFO_STR* info_str = (INFO_STR *)argv;
	int frame_index = 0;
	FILE *filePCAP = fopen(info_str->pcap_str,"rb");
	FILE *fileVideo = fopen(info_str->video_str,"wb");
	// FILE *fileYUV = fopen(info_str->yuv_str, "wb");
	
	if(filePCAP == NULL || fileVideo==NULL)
	{
		LOGE("open err!!!!");
		returnValue = -1;
		return &returnValue;
	}

	long process = 0;

	JNIEnv *env;
	if(isPlay){
		LOGI("AttachCurrentThread");
		(*gs_jvm)->AttachCurrentThread(gs_jvm,&env,NULL);
	}
	else{
		env = info_str->env;
	}

	jmethodID methodID_setProgressRate = (*env)->GetStaticMethodID(env, gs_class, "setProgressRate", "(I)V");
	pcap_file_header = (PCAP_FILE_HDR*)malloc(sizeof(PCAP_FILE_HDR));
	fread(pcap_file_header, 1, sizeof(PCAP_FILE_HDR), filePCAP);
	//偏移PCAP文件头
	process += sizeof(PCAP_FILE_HDR);
	
	LOGI("pcap_file_header->magic=%u",pcap_file_header->magic);
	int is_pcap_file_header_endian_switch = 0;
	if(pcap_file_header->magic == 0xA1B2C3D4){//pcap文件头标识
		is_pcap_file_header_endian_switch = 0;
	}
	else if(pcap_file_header->magic == 0xD4C3B2A1){
		is_pcap_file_header_endian_switch = 1;
	}
	else{
		LOGE("not pcap file!!!!");
		returnValue = -2;
		return &returnValue;
	}	
	LOGI("is_pcap_file_header_endian_switch=%d",is_pcap_file_header_endian_switch);
	
	pos = pcap_buffer;
	len_read = fread(pcap_buffer, sizeof(char), PCAP_BUFFER_SIZE ,filePCAP);
	while(!parse_exit){		//如果文件读到底了，就全解析完。 否则缓冲区剩余字节小于PCAP_BOTTOM_ALERT的时候退出循环
		while( pos - pcap_buffer <  len_read - len_alert ){
			if(yuv_buffers.size < MAX_YUV_BUFFERS_SIZE / 2 || !isPlay){
				pcap_pkt_hdr = (PCAP_PKT_HDR *)pos;
				if(!is_pcap_file_header_endian_switch){
					len_pkt_left = pcap_pkt_hdr->caplen;
				}
				else{
					len_pkt_left = switchUint32(pcap_pkt_hdr->caplen);
				}
				if(len_pkt_left > len_read -(pos- pcap_buffer)){
					LOGI("break: frame_index=%5d len_pkt_left=%d len_read=%5d pos- pcap_buffer=%5d",frame_index,len_pkt_left,len_read,(pos- pcap_buffer));
					break;
				}
				pos += sizeof(PCAP_PKT_HDR);

				//pos += (sizeof(MAC_FRAME_HDR) + sizeof(IP_HDR) +  sizeof(UDP_HDR));
				//len_pkt_left -=  (sizeof(MAC_FRAME_HDR) + sizeof(IP_HDR) +  sizeof(UDP_HDR));

				if(!is_pcap_file_header_endian_switch){
					pcap_file_linktype = pcap_file_header->linktype;
				}
				else{
					pcap_file_linktype = switchUint32(pcap_file_header->linktype);
				}
				if(pcap_file_linktype == 1){
					//LOGI("MAC_FRAME_HDR: pcap_file_linktype=%d",pcap_file_linktype);
					frame_hdr = (MAC_FRAME_HDR *)pos;
					len_pkt_left -= sizeof(MAC_FRAME_HDR);
					pos += sizeof(MAC_FRAME_HDR);
					frame_index++;
				}else if(pcap_file_linktype == 113){
					//LOGI("LINUX_COOKED_CAPTURE_HDR: pcap_file_linktype=%d",pcap_file_linktype);
					linux_cooked_capture_hdr = (LINUX_COOKED_CAPTURE_HDR *)pos;
					len_pkt_left -= sizeof(LINUX_COOKED_CAPTURE_HDR);
					pos += sizeof(LINUX_COOKED_CAPTURE_HDR);
					frame_index++;
				}
				else{
					//LOGE("unknown: pcap_file_linktype=%6d",pcap_file_linktype);
				}
			
				if((pcap_file_linktype == 1 && switchUshort(frame_hdr->m_cType) == 0x0800) ||
					(pcap_file_linktype == 113 && switchUshort(linux_cooked_capture_hdr->protocol) == 0x0800)){//数据链路帧的网络层为IPv4(0x0800)
					ip_hdr = (IP_HDR *)pos;
					len_pkt_left -= sizeof(IP_HDR);
					pos += sizeof(IP_HDR);

					if(ip_hdr->ip_protocol == 17){//ip的传输层为UPD
						udp_hdr = (UDP_HDR *)pos;
						len_pkt_left -= sizeof(UDP_HDR);
						pos += sizeof(UDP_HDR);
						
						if(*(pos) == rtp_packer_first || *(pos) == rtp_extension_packer_first){//UDP负载为RTP
							rtp_hdr = (RTP_HDR*)pos;
							len_pkt_left -= sizeof(RTP_HDR);
							pos += sizeof(RTP_HDR);
							
							//pos 指向payload
							len_pkt_left -= (rtp_hdr->csrc_count) * sizeof(u_int32);
							pos += (rtp_hdr->csrc_count) * sizeof(int);
							if(rtp_hdr->extension == 0x01){
								rtp_extension_hdr_size = sizeof(RTP_EXTENSION_HDR)+ *(pos+3)*sizeof(u_int32);
								len_pkt_left -= rtp_extension_hdr_size;
								pos += rtp_extension_hdr_size;
							}

							if(rtp_hdr->payload_type == info_str->type_num && !strcmp(info_str->a_line,"H264")){//RTP负载为H264
								if(switchUint32(rtp_hdr->ssrc) == info_str->ssrc && switchUint32(ip_hdr->ip_source) == info_str->ip_src 
								&& switchUint32(ip_hdr->ip_dest) == info_str->ip_dst){//该H264包来自同一同步信源
									if(rtp_seq == 0){
										rtp_seq = switchUshort(rtp_hdr->seq);

										// avio =avio_alloc_context(iobuffer, MAX_AUDIO_PACKET_SIZE,0,&video_buffers,fill_iobuffer,NULL,NULL);  
										// pFormatCtx->pb=avio; 

										// //char* input = "/storage/emulated/0/0testAudio/000.amr";
										// if(avformat_open_input(&pFormatCtx,"",NULL,NULL)!=0){
											// LOGE("Couldn't open input stream.");
											// returnValue = -2;
											// return &returnValue;
										// }
										// // Retrieve stream information
										// if(avformat_find_stream_info(pFormatCtx,NULL)<0){
											// LOGE("Couldn't find stream information.");
											// returnValue = -3;
											// return &returnValue;
										// }

										// // Find the first audio stream
										// audioStream=-1;
										// for(int i=0; i < pFormatCtx->nb_streams; i++){
											// if(pFormatCtx->streams[i].codec->codec_type==AVMEDIA_TYPE_AUDIO){
												// audioStream=i;
												// break;
											// }
										// }

										// if(audioStream==-1){
											// LOGE("Didn't find a audio stream.");
											// returnValue = -4;
											// return &returnValue;
										// }

										// // Get a pointer to the codec context for the audio stream
										// pCodecCtx=pFormatCtx->streams[audioStream].codec;

										// // Find the decoder for the audio stream
										// pCodec=avcodec_find_decoder(pCodecCtx->codec_id);
										// if(pCodec==NULL){
											// LOGE("Codec not found.");
											// returnValue = -5;
											// return &returnValue;
										// }

										// // Open codec
										// if(avcodec_open2(pCodecCtx, pCodec,NULL)<0){
											// LOGE("Could not open codec.");
											// returnValue = -6;
											// return &returnValue;
										// }

										// packet=(AVPacket *)av_malloc(sizeof(AVPacket));
										// av_init_packet(packet);

										// //Out Audio Param
										// out_channel_layout = pCodecCtx->channel_layout;
										// //nb_samples: AAC-1024 MP3-1152 AMR_NB-160:8000*0.02
										// out_nb_samples=160;
										// out_sample_fmt = AV_SAMPLE_FMT_S16;//AMR:16bit
										// out_sample_rate = pCodecCtx->sample_rate;//AMR-nb:8k, AMR-wb:16k
										// out_channels = pCodecCtx->channels;//单通道：1
										// //out_channels=av_get_channel_layout_nb_channels(out_channel_layout);
										// pFrame=av_frame_alloc();

										// //FIX:Some Codec's Context Information is missing
										// //in_channel_layout=av_get_default_channel_layout(pCodecCtx->channels);

										// //Swr
										// au_convert_ctx = swr_alloc();
										// au_convert_ctx = swr_alloc_set_opts(au_convert_ctx,out_channel_layout, AV_SAMPLE_FMT_S16, out_sample_rate,
											// pCodecCtx->channel_layout, pCodecCtx->sample_fmt , pCodecCtx->sample_rate,0, NULL);
										// swr_init(au_convert_ctx);
										//--------------------------------------------------------------------------------------------------------------
									}else{
										rtp_seq++;
									}

									video_buffer_size = (switchUshort(udp_hdr->length)-8) - ((void*)pos-(void*)rtp_hdr);
									LOGI("=============frame=%5d seq=%5d video_buffer_size=%4d video_buffer.size=%5d video_index=%d",frame_index,switchUshort(rtp_hdr->seq),video_buffer_size,video_buffers.size,video_index);
									// if(frame_index == 28818){
										// LOGI("=========frame=%5d seq=%5d video_buffer_size=%4d",frame_index,switchUshort(rtp_hdr->seq),video_buffer_size);
										// returnValue = -1;
										// return &returnValue;
									// }
									if(!checkoutAllZero(pos,video_buffer_size)){
										nal_unit_type = *pos & 0x1F;
										if (nal_unit_type == 28){
											mode = 1;
											S = (*(pos+1) & 0x80) >> 7;
											E = (*(pos+1) & 0x40) >> 6;
											if(S==1 && E==0){
												nal_unit_type = *(pos+1) & 0x1F;
											}
										}
										else{
											mode = 0;
										}
										video_buffer_data = (u_char *)malloc(video_buffer_size);
										memmove(video_buffer_data,pos,video_buffer_size);
										setVideoBuffer(&video_buffers,video_buffer_data,video_buffer_size,switchUshort(rtp_hdr->seq),mode,nal_unit_type,S,E);
										video_index++;
										
										if(video_buffers.size >= 700 || video_index >= info_str->pkt_count){
										//--------------------------------------------------------------------------------------------------------------	
											while(video_buffers.size > 400 || (video_buffers.size > 0 && video_index >= info_str->pkt_count)){
												if(video_buffers.buffer[0].seq != 0){ //正常
													if(video_buffers.buffer[0].mode == 0){ //单包
														pollVideoBuffer(&video_buffers,&video_buffer);
														LOGI("single seq=%5d video_buffer_size=%4d video_buffer.size=%5d",video_buffer->seq,video_buffer->buffer_size,video_buffers.size);
														fwrite(start_code, 1, start_code_len, fileVideo);
														fwrite(video_buffer->buffer_data, 1, video_buffer->buffer_size, fileVideo);
														free(video_buffer->buffer_data);
														free(video_buffer);
													}
													else if(video_buffers.buffer[0].mode == 1){ //FU-A
														LOGI("FU-A  seq=%5d video_buffer_size=%4d S=%d E=%d video_buffer.size=%5d",video_buffers.buffer[0].seq,video_buffers.buffer[0].buffer_size,video_buffers.buffer[0].S,video_buffers.buffer[0].E,video_buffers.size);
														if(video_buffers.buffer[0].S == 1 && video_buffers.buffer[0].E == 0){ //FU-A起始包
															if(video_index >= info_str->pkt_count && video_buffers.size == 1){
																LOGE("error!!! video_buffers.size=%d FU-A等待结束时已没有更多包",video_buffers.size);
																pollVideoBuffer(&video_buffers,&video_buffer);
																free(video_buffer->buffer_data);
																free(video_buffer);
															}
															else{
																for(int index = 1; index < video_buffers.size; index++){
																	LOGI("index seq=%5d video_buffer_size=%4d S=%d E=%d index=%d",video_buffers.buffer[index].seq,video_buffers.buffer[index].buffer_size,video_buffers.buffer[index].S,video_buffers.buffer[index].E,index);
																	if(video_buffers.buffer[index].seq != 0){ //正常
																		if(video_buffers.buffer[index].mode == 1){ 
																			if(video_buffers.buffer[index].S == 0 && video_buffers.buffer[index].E == 0){ //FU-A中间包
																				if(video_index >= info_str->pkt_count && index >= video_buffers.size-1){
																					LOGE("error!!! FU-A_index=%d FU-A等待结束时已没有更多包",index);
																					for(int num = 0; num < index; num++){
																						pollVideoBuffer(&video_buffers,&video_buffer);
																						free(video_buffer->buffer_data);
																						free(video_buffer);
																					}
																					break;
																				}
																				continue;
																			}
																			else if(video_buffers.buffer[index].S == 0 && video_buffers.buffer[index].E == 1){ //FU-A结束包
																				pollVideoBuffer(&video_buffers,&video_buffer);
																				LOGI("FU-A seq=%5d video_buffer_size=%4d",video_buffer->seq,video_buffer->buffer_size);
																				fwrite(start_code, 1, start_code_len, fileVideo);
																				nalu_header = (video_buffer->buffer_data[0] & 0xE0) | (video_buffer->buffer_data[1] & 0x1F);
																				fwrite(&nalu_header, 1, 1, fileVideo);
																				fwrite(video_buffer->buffer_data+2, 1, video_buffer->buffer_size-2, fileVideo);
																				free(video_buffer->buffer_data);
																				free(video_buffer);
																				for(int num = 1; num <= index; num++){
																					pollVideoBuffer(&video_buffers,&video_buffer);
																					if(video_buffer->seq != 0){
																						fwrite(video_buffer->buffer_data+2, 1, video_buffer->buffer_size-2, fileVideo);
																						free(video_buffer->buffer_data);
																						free(video_buffer);
																					}
																					else{
																						LOGE("error!!! FU-A_index=%d FU-A丢包写入跳过",num);
																					}
																				}
																				break;
																			}
																			else if(video_buffers.buffer[index].S == 1 && video_buffers.buffer[index].E == 0){ //FU-A等待结束时出现起始包
																				LOGE("error!!! seq=%5d FU-A_index=%d FU-A等待结束时出现起始包",video_buffers.buffer[index].seq,index);
																				for(int num = 0; num < index; num++){
																					pollVideoBuffer(&video_buffers,&video_buffer);
																					free(video_buffer->buffer_data);
																					free(video_buffer);
																				}
																				break;
																			}
																			else{
																				LOGE("error!!! seq=%5d E=%d S=%d SE异常",video_buffers.buffer[index].seq,video_buffers.buffer[index].S,video_buffers.buffer[index].E);
																			}
																		}
																		else if(video_buffers.buffer[index].mode == 0){ //FU-A等待结束时出现单包
																			LOGE("error!!! seq=%5d FU-A_index=%d FU-A等待结束时出现单包",video_buffers.buffer[index].seq,index);
																			for(int num = 0; num < index; num++){
																				pollVideoBuffer(&video_buffers,&video_buffer);
																				free(video_buffer->buffer_data);
																				free(video_buffer);
																			}
																			break;
																		}
																		else{
																			LOGE("error!!! seq=%5d mode=%d mode异常",video_buffers.buffer[index].seq,video_buffers.buffer[index].mode);
																		}
																	}
																	else { //丢包
																		LOGE("error!!! seq=%5d FU-A_index=%d FU-A丢包",video_buffers.buffer[index].seq,index);
																		// for(int num = 0; num <= index; num++){
																			// pollVideoBuffer(&video_buffers,&video_buffer);
																			// free(video_buffer->buffer_data);
																			// free(video_buffer);
																		// }
																		// break;
																		if(video_index >= info_str->pkt_count && index >= video_buffers.size-1){
																			LOGE("error!!! FU-A_index=%d FU-A等待结束时已没有更多包",index);
																			for(int num = 0; num < index; num++){
																				pollVideoBuffer(&video_buffers,&video_buffer);
																				free(video_buffer->buffer_data);
																				free(video_buffer);
																			}
																			break;
																		}
																		continue;
																	}
																}
															}
														}
														else{
															LOGE("error!!! seq=%5d E=%d S=%d FA-U丢失起始包",video_buffers.buffer[0].seq,video_buffers.buffer[0].S,video_buffers.buffer[0].E);
															pollVideoBuffer(&video_buffers,&video_buffer);
															free(video_buffer->buffer_data);
															free(video_buffer);
														}
													}
													else{
														LOGE("error!!! seq=%5d mode=%d mode异常",video_buffers.buffer[0].seq,video_buffers.buffer[0].mode);
													}
												}
												else{ //丢包
													LOGE("seq+1=%5d 丢包",video_buffers.buffer[1].seq);
													pollVideoBuffer(&video_buffers,&video_buffer);
													free(video_buffer->buffer_data);
													free(video_buffer);
												}
													
												// if(video_buffers.buffer[0].seq != 0){
													// if(av_read_frame(pFormatCtx, packet)>=0){
														// if(packet->stream_index==audioStream){
															// ret = avcodec_decode_audio4( pCodecCtx, pFrame,&got_picture, packet);
															// if ( ret < 0 ) {
																// LOGE("Error in decoding audio frame.");
																// returnValue = -8;
																// return &returnValue;
															// }
															// if ( got_picture > 0 ){
																// // LOGI("%5d %5d %5d %5d %5d %5d %5d %5d %5d %5d %5d %5d %5d %5d %5d %5d %5d %5d %5d %5d",video_buffers.seq[0],video_buffers.seq[1],video_buffers.seq[2],video_buffers.seq[3],video_buffers.seq[4]
																// // ,video_buffers.seq[5],video_buffers.seq[6],video_buffers.seq[7],video_buffers.seq[8],video_buffers.seq[9],video_buffers.seq[10],video_buffers.seq[11],video_buffers.seq[12]
																// // ,video_buffers.seq[13],video_buffers.seq[14],video_buffers.seq[15],video_buffers.seq[16],video_buffers.seq[17],video_buffers.seq[18],video_buffers.seq[19]);
																// swr_size = swr_convert(au_convert_ctx,&swr_buffer, MAX_AUDIO_FRAME_SIZE,(const uint8_t **)pFrame->data , out_nb_samples);
																// //pcm_buffer_size=av_samples_get_buffer_size(NULL,out_channels ,pFrame->nb_samples, out_sample_fmt, 1);
																// if(out_sample_fmt == AV_SAMPLE_FMT_U8){
																	// yuv_buffers.buffer_size = swr_size * out_channels  * 1;
																// }
																// else if(out_sample_fmt == AV_SAMPLE_FMT_S16){
																	// yuv_buffers.buffer_size = swr_size * out_channels  * 2;
																// }
																// else if(out_sample_fmt == AV_SAMPLE_FMT_FLT){
																	// yuv_buffers.buffer_size = swr_size * out_channels  * 4;
																// }
																// fwrite(swr_buffer, 1, yuv_buffers.buffer_size, fileYUV);//Write PCM	
																
																// pcm_buffer=(u_char *)malloc(1*yuv_buffers.buffer_size);
																// memmove(pcm_buffer,swr_buffer,yuv_buffers.buffer_size);
																// pcm_index++;
																// LOGI("pcm_index:%5d\t pts:%lld\t packet size:%d\t yuv_buffers.size:%d\t video_buffers.size:%d",pcm_index,packet->pts,packet->size,yuv_buffers.size,video_buffers.size);
																// //################获取mutex#################
																// if(isPlay == 1 && 0 != pthread_mutex_lock(&mutex)){
																	// returnValue = -9;
																	// return &returnValue;
																// }	
																// //##########################################
																// putPCMBuffer(&yuv_buffers, pcm_buffer);
																// //################释放mutex#################
																// if(isPlay == 1 && 0 != pthread_mutex_unlock(&mutex)){
																	// returnValue = -10;
																	// return &returnValue;
																// }
																// //##########################################
															// }
														// }
													// }
													// av_free_packet(packet);
												// }
												// else{
													// LOGI("pkt loss: %d", video_buffers.seq[1]-1);
													// pollAudioBuffer(&video_buffers,&audio_buffer);
													// pcm_buffer=(u_char *)malloc(1*yuv_buffers.buffer_size);
													// memset(pcm_buffer, 0, 1*yuv_buffers.buffer_size);
													// fwrite(pcm_buffer, 1, yuv_buffers.buffer_size, fileYUV);//Write PCM	
													// pcm_index++;
													// LOGI("loss pcm_index:%5d\t packet size:%d  %d",pcm_index,yuv_buffers.buffer_size,yuv_buffers.size);
													// //################获取mutex#################
													// if(isPlay == 1 && 0 != pthread_mutex_lock(&mutex)){
														// returnValue = -9;
														// return &returnValue;
													// }	
													// //##########################################
													// putPCMBuffer(&yuv_buffers, pcm_buffer);
													// //################释放mutex#################
													// if(isPlay == 1 && 0 != pthread_mutex_unlock(&mutex)){
														// returnValue = -10;
														// return &returnValue;
													// }
													// //##########################################
												// }
											}
											//--------------------------------------------------------------------------------------------------------------
										}
									}
									else{
										LOGE("frame=%5d,H264 is all zero:",frame_index);								
									}									
								}
							}
						}
					}
				}
				//pos 指向下一个pcap 包头的地址
				pos += len_pkt_left;
			}
			else{ 
				if(parse_exit){
					break;
				}
				usleep(20000);
			}
		}
		if(read_to_end || parse_exit){
			//printf("==%d\n",pos - pcap_buffer + process);
			LOGI("len_read_left [%d], read_already [%ld]", len_read_left, pos - pcap_buffer + process);
			if(isPlay == 0){
				(*env)->CallStaticVoidMethod(env, gs_class, methodID_setProgressRate, pos - pcap_buffer + process);
			}
			break;
		}
		len_read_left = len_read -(pos- pcap_buffer);
		
		process += pos- pcap_buffer;
		//printf("==%d\n",pos - pcap_buffer + process);
		LOGI("len_read_left [%d], read_already [%ld]", len_read_left, pos - pcap_buffer + process);
		if(isPlay == 0){
			(*env)->CallStaticVoidMethod(env, gs_class, methodID_setProgressRate, pos - pcap_buffer + process);
		}
		//将剩余部分移动到缓冲区头 然后继续从文件中读取 PCAP_BUFFER_SIZE - len_read_left 这么长
		memmove(pcap_buffer,pos,len_read_left);
		pos = pcap_buffer + len_read_left;
		len_read = fread(pos, sizeof(char), (PCAP_BUFFER_SIZE - len_read_left),filePCAP);
		if(len_read < PCAP_BUFFER_SIZE - len_read_left )  //如果读到文件尾，就把警戒值置0，让下一个循环读完
		{
			read_to_end = 1;
			len_alert = 0;
		}
		//待处理的长度为  剩余部分 + 新读取部分
		len_read += len_read_left;
		pos = pcap_buffer;
	}
	
	LOGI("00000000000000");
	LOGI("11111111111111");
	// fclose(fileYUV);
	fclose(filePCAP);
	fclose(fileVideo);
	free(pcap_file_header);
	free(pcap_buffer);
	// if(!strcmp(info_str->a_line,"AMR")){
		// LOGI("22222222222222");
		// swr_free(&au_convert_ctx);
		// LOGI("33333333333333");
		// //av_free(iobuffer);
		// LOGI("44444444444444");
		// av_free(swr_buffer);
		// LOGI("55555555555555");
		// av_frame_free(&pFrame);
		// LOGI("66666666666666");
		// // Close the codec
		// if(pCodecCtx != NULL)avcodec_close(pCodecCtx);
		// LOGI("77777777777777");
		// // Close the video file
		// if(pFormatCtx != NULL)avformat_close_input(&pFormatCtx);
	// }
	LOGI("88888888888888");
	if(isPlay){
		LOGI("DetachCurrentThread");
		(*gs_jvm)->DetachCurrentThread(gs_jvm);
	}
	LOGI("decode end");
	returnValue = 0;
	return &returnValue;
}

JNIEXPORT jint JNICALL Java_com_example_pcapdecoder_activity_MainActivity_play(JNIEnv *env, jobject thiz, jstring pcap_jstr, jstring video_jstr, jstring yuv_jstr, jobject object_videoInfo)
{
	LOGI("play");
    return 0;
}


JNIEXPORT void JNICALL Java_com_example_pcapdecoder_activity_MainActivity_playCancel(JNIEnv *env, jobject thiz)
{
	isPlay = 1;
    parse_exit = 1;
}

JNIEXPORT jint JNICALL Java_com_example_pcapdecoder_activity_MainActivity_decode(JNIEnv *env, jobject thiz, jstring pcap_jstr, jstring video_jstr, jstring yuv_jstr, jobject object_videoInfo)
{
	LOGI("decode");
	jclass objcetClass = (*env)->FindClass(env,"com/example/pcapdecoder/activity/MainActivity");
	jmethodID methodID_setProgressRateFull = (*env)->GetStaticMethodID(env, objcetClass, "setProgressRateFull", "()V");
	jmethodID methodID_setProgressRateEmpty = (*env)->GetStaticMethodID(env, objcetClass, "setProgressRateEmpty", "()V");
	
	jclass class_video = (*env)->FindClass(env,"com/example/pcapdecoder/bean/VideoInfo");
	jfieldID fieldID_ip_src = (*env)->GetFieldID(env, class_video, "ip_src", "I");
	jfieldID fieldID_ip_dst = (*env)->GetFieldID(env, class_video, "ip_dst", "I");
	jfieldID fieldID_ssrc = (*env)->GetFieldID(env, class_video, "ssrc", "I");
	jfieldID fieldID_type_num = (*env)->GetFieldID(env, class_video, "type_num", "I");
	jfieldID fieldID_a_line = (*env)->GetFieldID(env, class_video, "a_line", "Ljava/lang/String;");
	jfieldID fieldID_pkt_count = (*env)->GetFieldID(env, class_video, "pkt_count", "I");
	
	char pcap_str[500]={0};
	char video_str[500]={0};
	char yuv_str[500]={0};
	char a_line[10]={0};
	u_int32 ip_src = (*env)->GetIntField(env, object_videoInfo, fieldID_ip_src);
    u_int32 ip_dst = (*env)->GetIntField(env, object_videoInfo, fieldID_ip_dst);
    u_int32 ssrc = (*env)->GetIntField(env, object_videoInfo, fieldID_ssrc);
    int type_num = (*env)->GetIntField(env, object_videoInfo, fieldID_type_num);
	sprintf(a_line,"%s",(*env)->GetStringUTFChars(env,(*env)->GetObjectField(env, object_videoInfo, fieldID_a_line), NULL));
	int pkt_count = (*env)->GetIntField(env, object_videoInfo, fieldID_pkt_count);
	sprintf(pcap_str,"%s",(*env)->GetStringUTFChars(env,pcap_jstr, NULL));
	sprintf(video_str,"%s",(*env)->GetStringUTFChars(env,video_jstr, NULL));
	sprintf(yuv_str,"%s",(*env)->GetStringUTFChars(env,yuv_jstr, NULL));
	LOGI("Cpp input pcap_str:%s",pcap_str);
	LOGI("Cpp output video_str:%s",video_str);
	LOGI("Cpp output yuv_str:%s",yuv_str);
	LOGI("Cpp ip_src:%d",ip_src);
	LOGI("Cpp ip_dst:%d",ip_dst);
	LOGI("Cpp ssrc:%d",ssrc);
	LOGI("Cpp type_num:%d",type_num);
	LOGI("Cpp a_line:%s",a_line);
	LOGI("Cpp pkt_count:%d",pkt_count);

	INFO_STR info_str;
	info_str.pcap_str = pcap_str;
	info_str.video_str = video_str;
	info_str.yuv_str = yuv_str;
	info_str.ip_src = ip_src;
	info_str.ip_dst = ip_dst;
	info_str.ssrc = ssrc;
	info_str.type_num = type_num;
	info_str.a_line = a_line;
	info_str.pkt_count = pkt_count;
	
	isPlay = 0;
	parse_exit = 0;
	if(isPlay == 0){
		info_str.env = env;
	}
	gs_class=(*env)->NewGlobalRef(env,objcetClass);
	decode(&info_str);
	clearYUVBuffer(&yuv_buffers);
	if(parse_exit == 0){
		LOGI("nativeStartPlayback completed !");
		(*env)->CallStaticVoidMethod(env, objcetClass, methodID_setProgressRateFull);
	}
	else{
		LOGI("nativeStartPlayback Cancel !!!");
		(*env)->CallStaticVoidMethod(env, objcetClass, methodID_setProgressRateEmpty);
	}
	if(gs_class!=NULL)
		(*env)->DeleteGlobalRef(env,gs_class);
    return 0;
}


JNIEXPORT void JNICALL Java_com_example_pcapdecoder_activity_MainActivity_decodeCancel(JNIEnv *env, jobject thiz)
{
	isPlay = 0;
    parse_exit = 1;
}

jint JNI_OnLoad(JavaVM* vm, void* reserved){
	gs_jvm = vm;
	LOGI("JNI_OnLoad");
	return JNI_VERSION_1_4;
}
