#ifndef __FF_TCPIP_H__
#define __FF_TCPIP_H__

#define __LITTLE_ENDIAN_BITFIELD

typedef  int int32;
typedef  unsigned int u_int32;
typedef  unsigned char u_char;
typedef  unsigned short u_short;

#pragma pack(1)

typedef struct ip_hdr{//ipv4ͷ��
#ifdef __LITTLE_ENDIAN_BITFIELD
	u_char ip_length:4,  
	ip_version:4;                
#else
	u_char ip_version:4,
	ip_length:4;
#endif
	u_char ip_tos;                        
	u_short ip_total_length;              
	u_short ip_id;                        
	u_short ip_flags;                    
	u_char ip_ttl;                        
	u_char ip_protocol;                  
	u_short ip_cksum;                    
	u_int32 ip_source;                    
	u_int32 ip_dest;                      
} IP_HDR;

typedef struct udp_hdr{//udpͷ��
	u_short s_port;      
	u_short d_port;      
	u_short length;      
	u_short cksum;      
} UDP_HDR;

typedef struct psd_header{//αͷ�������ڼ���У���
	u_int32 s_ip;//source ip
	u_int32 d_ip;//dest ip
	u_char mbz;//0
	u_char proto;//proto type
	u_short plen;//length
} PSD_HEADER;

typedef struct mac_frame_hdr{
	u_char m_cDstMacAddress[6];   //Ŀ��mac��ַ
	u_char m_cSrcMacAddress[6];   //Դmac��ַ
	u_short m_cType;              //��һ��Э�����ͣ���0x0800������һ����IPЭ�飬0x0806Ϊarp
} MAC_FRAME_HDR;

typedef struct linux_cooked_capture_hdr{
	u_short pcaket_type;   
	u_short link_layer_address_type;
	u_short link_layer_address_lenght;
	u_char source[6];
	u_short padding;
	u_short protocol;              //��һ��Э�����ͣ���0x0800������һ����IPЭ�飬0x0806Ϊarp
} LINUX_COOKED_CAPTURE_HDR;

typedef struct rtp_hdr{
#ifdef __LITTLE_ENDIAN_BITFIELD   //ע�⣬�����������С���ǲ�ͬ�ģ�����Ҫ����ʵ������޸Ĵ˺궨��
	u_char csrc_count:4,
	extension:1,
	padding:1,
	version:2;
	u_char payload_type:7,
	marker:1;
#else
	u_char version:2, //RTPЭ��İ汾�ţ�ռ2λ����ǰЭ��汾��Ϊ2
	padding:1, //����־��ռ1λ�����P=1�����ڸñ��ĵ�β�����һ����������İ�λ�飬���ǲ�����Ч�غɵ�һ����
	extension:1, //��չ��־��ռ1λ�����X=1������RTP��ͷ�����һ����չ��ͷ��
	csrc_count:4; //CSRC��������ռ4λ��ָʾCSRC ��ʶ���ĸ���
	u_char  marker:1, //��ͬ����Ч�غ��в�ͬ�ĺ��壬������Ƶ�����һ֡�Ľ�����������Ƶ����ǻỰ�Ŀ�ʼ
	payload_type:7; //��Ч�غ����ͣ�ռ7λ������˵��RTP��������Ч�غɵ����ͣ���GSM��Ƶ��JPEMͼ���
#endif
	u_short seq; //���кţ�ռ16λ�����ڱ�ʶ�����������͵�RTP���ĵ����кţ�ÿ����һ�����ģ����к���1
	u_int32 timestamp; //ʱ����ռ32λ��ʱ����ӳ�˸�RTP���ĵĵ�һ����λ��Ĳ���ʱ�̡�ʹ��ʱ���������ӳٺ��ӳٶ���
	u_int32 ssrc; //ռ32λ�����ڱ�ʶͬ����Դ���ñ�ʶ�������ѡ��ģ��μ�ͬһ��Ƶ���������ͬ����Դ��������ͬ��SSRC
	u_int32 csrc[0];
} RTP_HDR;

typedef struct rtp_extension_hdr{
	u_short define_by_profile;
	u_short extension_length;
	u_int32 header_extension[0];
} RTP_EXTENSION_HDR;

typedef struct pcap_file_header {
	u_int32 magic;
	u_short version_major;
	u_short version_minor;
	int32   thiszone;    
	u_int32 sigfigs;    
	u_int32 snaplen;    
	u_int32 linktype;  
} PCAP_FILE_HDR;


typedef struct pcap_pkt_hdr {
	u_int32 iTimeSecond;
	u_int32 iTimeSS;
	u_int32 caplen;    
	u_int32 len;        
} PCAP_PKT_HDR;

typedef struct pcapng_section_header {
	u_int32 block_type;//0x0A0D0D0A
	u_int32 block_total_length;
	u_int32 byte_order_magic;//�ֽ�˳��0x1A2B3C4D,���ִ�С��
	u_short version_major;//
	u_short version_minor;
	u_int32 section_length;  
	u_int32 section_length2;	
	u_int32 options[0];
} PCAPNG_SECTION_HDR;


typedef struct pcapng_interface_description {
	u_int32 block_type;//0x00000001
	u_int32 block_total_length;
	u_short linktype;//��·����
	u_short reserved;
	u_int32 snaplen; 
	u_int32 options[0];
} PCAPNG_INTERFACE_DESC;

typedef struct pcapng_enhanced_packet {
	u_int32 block_type;//0x00000006
	u_int32 block_total_length;
	u_int32 interface_id;
	u_int32 timestamp_high;
	u_int32 timestamp_low; 
	u_int32 captured_len; 
	u_int32 packet_len;
	u_int32 packet_data;
} PCAPNG_ENHANCED_PACKET;

typedef struct pcapng_simple_packet {
	u_int32 block_type;//0x00000003
	u_int32 block_total_length; 
	u_int32 packet_len;
	u_int32 packet_data;
} PCAPNG_SIMPLE_PACKET;

typedef struct pcapng_packet {
	u_int32 block_type;//0x00000002
	u_int32 block_total_length;
	u_short interface_id;
	u_short drops_count;
	u_int32 timestamp_high;
	u_int32 timestamp_low; 
	u_int32 captured_len; 
	u_int32 packet_len;
	u_int32 packet_data;
} PCAPNG_PACKET;

#define MAX_VIDEO_BUFFERS_SIZE 800
#define MAX_YUV_BUFFERS_SIZE 800


/**
 * �Զ����video������У�ʵ����jitterbuffer���ܣ�����RTP���򡢶���
 */
typedef struct video_buffer{
	u_char* buffer_data;
	int buffer_size;
	u_short seq;
	char mode; //0--single 1--FU-A
	char nal_unit_type;
	char S;
	char E;
} VIDEO_BUFFER;

typedef struct video_buffers{
	VIDEO_BUFFER buffer[MAX_VIDEO_BUFFERS_SIZE];
	int size;
} VIDEO_BUFFERS;

/**
 * YUV������У����̣߳������̡߳������̣߳���ͬʹ��ʱ������mutex
 */
typedef struct yuv_buffer{
	u_char* buffer_data;
	int buffer_size;
} YUV_BUFFER;
 
typedef struct yuv_buffers{
	YUV_BUFFER buffer[MAX_YUV_BUFFERS_SIZE];
	int size;
} YUV_BUFFERS;

typedef struct video_info{
	u_int32 ip_src;
	u_int32 ip_dst;
	u_int32 ssrc;
	int type_num;
	char a_line[5];
	int pkt_count;
} VIDEO_INFO;

#pragma pack()

#endif
