package com.example.pcapdecoder.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.support.design.widget.FloatingActionButton;

import com.example.pcapdecoder.R;
import com.example.pcapdecoder.base.BaseRecyclerViewAdapter;
import com.example.pcapdecoder.base.OnItemClickListener;
import com.example.pcapdecoder.bean.VideoInfo;
import com.example.pcapdecoder.bean.PktInfo;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;

public class MainActivity extends Activity {

	private static final String PERMISSION_WRITE_EXTERNAL_STORAGE= "android.permission.WRITE_EXTERNAL_STORAGE";
	private static final int PERMISSION_REQUESTCODE = 0;
	private static final int GET_CONTENT_REQUESTCODE = 0;
	private static final String OUTPUT_VIDEO_SUBSTRING_BEGIN = "VIDEO输出：";
	private static final String OUTPUT_YUV_SUBSTRING_BEGIN = "YUV输出：";
	private static final String TAG = MainActivity.class.getSimpleName();
	private Toast mToast;
	private boolean isShow;

	private FloatingActionButton btn_parse;
	private Timer floatingActionButtonTimer;
	private static ShowUIHandler showUIHandler;
	private ProgressBar progressBar;
	private TextView input_pcap;
	private EditText output_video;
	private EditText output_yuv;
	private LinearLayout ly_output;
	private boolean isSelectPath = false;
	private static Handler progressRateHandler;
	private Thread parseThread;

	private RadioGroup parse_method;
	private RadioButton direct_decode;
	private RadioButton play_ffmpeg;
	private RadioButton play_mediacodec;
	private int parse_method_type = 0;

	private RadioGroup parse_paramter;
	private RadioButton custom;
	private int parse_paramter_type = 0;

	private TextView label_sdp;
	private LinearLayout ly_none;
	private RecyclerView mainRecyclerView;
	private LinearLayoutManager mainLayoutManager;
	private MainActivityRecyclerAdapter mainRecyclerViewAdapter;
	private static ArrayList<VideoInfo> videoInfos;

	private PopupWindow analysePopupWindow;
	private TextView text_sum;
	private CheckBox check_normal;
	private CheckBox check_loss;
	private CheckBox check_disorder;
	public static Boolean isCheckNormal;
	public static Boolean isCheckLoss;
	public static Boolean isCheckDisorder;
	private RecyclerView popupRecyclerView;
	private LinearLayoutManager popupLayoutManager;
	private PopupWindowRecyclerAdapter popupRecyclerViewAdapter;
	private static ArrayList<PktInfo> pktInfos;
	private static ArrayList<PktInfo> filer_pktInfos;
	public static final int PKTINFO_STATUS_NORMAL = 0;
	public static final int PKTINFO_STATUS_LOSS = 1;
	public static final int PKTINFO_STATUS_DISORDER = 2;

	private AlertDialog.Builder customDialogbuilder;
	private AlertDialog.Builder isTryCustomDialogbuilder;

	private static long time_start;
	private static long time_end;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
		progressRateHandler = new progressRateHandler(this);
		btn_parse = (FloatingActionButton) this.findViewById(R.id.floatingActionButton);
		toggleBtnParse(false);

		progressBar = (ProgressBar) this.findViewById(R.id.progressBar);

		input_pcap = (TextView) this.findViewById(R.id.input_pcap);
		ly_output = (LinearLayout) this.findViewById(R.id.ly_output);
		ly_output.setAlpha(0.5f);
		output_video = (EditText) this.findViewById(R.id.output_video);
		output_video.setEnabled(false);
		output_yuv = (EditText) this.findViewById(R.id.output_yuv);
		output_yuv.setEnabled(false);
		output_yuv.setVisibility(View.GONE);

		input_pcap.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				if(Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
					if (checkSelfPermission(PERMISSION_WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
						requestPermissions(new String[]{PERMISSION_WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUESTCODE);
					} else {
						Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
						intent.setType("*/*");
						intent.addCategory(Intent.CATEGORY_OPENABLE);
						startActivityForResult(Intent.createChooser(intent, "请选择文件管理器"), GET_CONTENT_REQUESTCODE);
					}
				}
				else{
					Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
					intent.setType("*/*");
					intent.addCategory(Intent.CATEGORY_OPENABLE);
					startActivityForResult(Intent.createChooser(intent, "请选择文件管理器"), GET_CONTENT_REQUESTCODE);
				}
			}
		});

		btn_parse.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0){
//				String folderurl=Environment.getExternalStorageDirectory().getPath();
				if(isSelectPath){
					if(videoInfos.size()>0 && mainRecyclerViewAdapter.positionSet.size()>0) {
						final String pcap_str = input_pcap.getText().toString();
						final String video_str = output_video.getText().toString().substring(OUTPUT_VIDEO_SUBSTRING_BEGIN.length());
						final String yuv_str = output_yuv.getText().toString().substring(OUTPUT_YUV_SUBSTRING_BEGIN.length());
						final VideoInfo videoInfo = videoInfos.get(mainRecyclerViewAdapter.positionSet.iterator().next());
//						output_yuv.setVisibility(View.GONE);

						Log.i("pcap_str", pcap_str);
						Log.i("video_str", video_str);
						Log.i("yuv_str", yuv_str);
						Log.i("videoInfo", videoInfo.toString());
						parseThread = new Thread() {
							@Override
							public void run() {
								time_start = System.currentTimeMillis();
								updateAnalysePopupWindow(mainRecyclerViewAdapter.positionSet.iterator().next());

								decode(pcap_str, video_str, yuv_str, videoInfo);
							}
						};
						progressBar.setProgress(0);

						progressBar.setMax((int)new File(pcap_str).length());

						progressBar.setVisibility(View.VISIBLE);
						parseThread.start();
						btn_parse.setClickable(false);
						toggleBtnParse(false);
					}
					else{
						showToast("该PCAP文件没有发现可用的AMR/NOVC音频流",Toast.LENGTH_SHORT);
					}
				}
				else{
					showToast("请先选择PCAP路径",Toast.LENGTH_SHORT);
				}
			}
		});

		final SharedPreferences sp = getSharedPreferences("custom",Context.MODE_PRIVATE);
		parse_method_type = sp.getInt("parse_method_type",0);
		parse_paramter_type = sp.getInt("parse_paramter_type",0);

		parse_method = (RadioGroup) this.findViewById(R.id.parse_method);
		direct_decode = (RadioButton) this.findViewById(R.id.direct_decode);
		play_ffmpeg = (RadioButton) this.findViewById(R.id.play_ffmpeg);
		play_mediacodec = (RadioButton) this.findViewById(R.id.play_mediacodec);

		if(parse_method_type == 0){
			parse_method.check(R.id.direct_decode );
		}
		else if(parse_method_type == 1){
			parse_method.check(R.id.play_ffmpeg );
		}
		else if(parse_method_type == 2){
			parse_method.check(R.id.play_mediacodec );
		}

		parse_method.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				if(checkedId == direct_decode.getId()){
					parse_method_type = 0;
					SharedPreferences.Editor editor = sp.edit();
					editor.putInt("parse_method_type", 0);
					editor.commit();

//					output_yuv.setVisibility(View.GONE);
				}
				else if(checkedId == play_ffmpeg.getId()){
					parse_method_type = 1;
					SharedPreferences.Editor editor = sp.edit();
					editor.putInt("parse_method_type", 1);
					editor.commit();

//					output_yuv.setVisibility(View.VISIBLE);
				}
				else if(checkedId == play_mediacodec.getId()){
					parse_method_type = 2;
					SharedPreferences.Editor editor = sp.edit();
					editor.putInt("parse_method_type", 2);
					editor.commit();

//					output_yuv.setVisibility(View.VISIBLE);
				}
			}
		});

		parse_paramter = (RadioGroup) this.findViewById(R.id.payload_type);
		if(parse_paramter_type == 0){
			parse_paramter.check(R.id.parseSDP );
		}
		else if(parse_paramter_type == 1){
			parse_paramter.check(R.id.custom );
		}
		parse_paramter.check(parse_paramter_type == 0 ? R.id.parseSDP : R.id.custom);
		parse_paramter.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				if(checkedId == R.id.parseSDP){
					parse_paramter_type = 0;
					SharedPreferences.Editor editor = sp.edit();
					editor.putInt("parse_paramter_type", 0);
					editor.commit();

					String pcap_str = input_pcap.getText().toString();
					updateVideoInfo(pcap_str,false);
				}
				else{
					parse_paramter_type = 1;
					SharedPreferences.Editor editor = sp.edit();
					editor.putInt("parse_paramter_type", 1);
					editor.commit();

				}
			}
		});

		custom = (RadioButton) this.findViewById(R.id.custom);
		custom.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showCustomPayloadTypeDialog();
			}
		});


		label_sdp = (TextView) this.findViewById(R.id.label_sdp);
		ly_none = (LinearLayout) this.findViewById(R.id.ly_none);
		mainRecyclerView = (RecyclerView) this.findViewById(R.id.recyclerView);
		mainLayoutManager = new LinearLayoutManager(this);
		mainLayoutManager.setOrientation(OrientationHelper.VERTICAL);
		mainRecyclerView.setLayoutManager(mainLayoutManager);
		mainRecyclerViewAdapter = new MainActivityRecyclerAdapter(this);
		mainRecyclerViewAdapter.setCreateViewLayout(R.layout.item_recycler_main);

		mainRecyclerViewAdapter.setOnItemClickListener(new OnItemClickListener(){
			@Override
			public void onClick(View view,int pos,String viewName) {
				if ("itemView".equals(viewName)) {
					mainRecyclerViewAdapter.positionSet.clear();
					mainRecyclerViewAdapter.positionSet.add(pos);
					mainRecyclerViewAdapter.notifyDataSetChanged();
					String pcap_str = input_pcap.getText().toString();
					output_video.setText(OUTPUT_VIDEO_SUBSTRING_BEGIN + pcap_str.substring(0, pcap_str.lastIndexOf(".")) + "."
							+ videoInfos.get(pos).getA_line().toLowerCase());
					output_video.setSelection(output_video.getText().length());
				}
				else if ("btn_analyse".equals(viewName)) {
					updateAnalysePopupWindow(pos);
					showAnalysePopupWindow();
				}
			}
		});
		mainRecyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
				super.onScrolled(recyclerView, dx, dy);
				if(floatingActionButtonTimer == null) {
					floatingActionButtonTimer = new Timer();
					floatingActionButtonTimer.schedule(new TimerTask() {
						@Override
						public void run() {
							showUIHandler.sendEmptyMessage(0);
						}
					}, 1000);
					btn_parse.setVisibility(View.GONE);
				}
				else{
					floatingActionButtonTimer.cancel();
					floatingActionButtonTimer = new Timer();
					floatingActionButtonTimer.schedule(new TimerTask() {
						@Override
						public void run() {
							showUIHandler.sendEmptyMessage(0);
						}
					}, 1000);
					btn_parse.setVisibility(View.GONE);
				}
			}
		});
		showUIHandler = new ShowUIHandler(this);
		label_sdp.setVisibility(View.GONE);
		ly_none.setVisibility(View.GONE);
		mainRecyclerView.setVisibility(View.GONE);

		initAnalysePopupWindow();
    }

	void updateFilterPktInfos(){
    	if(isCheckNormal && isCheckLoss && isCheckDisorder) {
			filer_pktInfos = pktInfos;
		}
		else{
			filer_pktInfos = new ArrayList<PktInfo>();
			for(int i = 0; i<pktInfos.size(); i++ ){
				if(pktInfos.get(i).getStatus() == PKTINFO_STATUS_NORMAL && isCheckNormal){
					filer_pktInfos.add(pktInfos.get(i));
				}
				else if(pktInfos.get(i).getStatus() == PKTINFO_STATUS_LOSS && isCheckLoss){
					filer_pktInfos.add(pktInfos.get(i));
				}
				else if(pktInfos.get(i).getStatus() == PKTINFO_STATUS_DISORDER && isCheckDisorder){
					filer_pktInfos.add(pktInfos.get(i));
				}
			}
		}
		text_sum.setText("("+filer_pktInfos.size()+"):");
		popupRecyclerViewAdapter.addDatas("pkts", filer_pktInfos);
	}

	private void initAnalysePopupWindow() {
		View contentView = LayoutInflater.from(this).inflate(R.layout.popupwindow_analyse, null);
		analysePopupWindow = new PopupWindow(contentView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true);
		text_sum = (TextView) contentView.findViewById(R.id.text_sum);
		check_normal = (CheckBox)contentView.findViewById(R.id.check_normal);
		check_loss = (CheckBox)contentView.findViewById(R.id.check_loss);
		check_disorder = (CheckBox)contentView.findViewById(R.id.check_disorder);
		isCheckNormal = true;
		isCheckLoss = true;
		isCheckDisorder = true;
		check_normal.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				isCheckNormal = isChecked;
				updateFilterPktInfos();
			}
		});
		check_loss.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				isCheckLoss = isChecked;
				updateFilterPktInfos();
			}
		});
		check_disorder.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				isCheckDisorder = isChecked;
				updateFilterPktInfos();
			}
		});
		popupRecyclerView =  (RecyclerView)contentView.findViewById(R.id.recyclerView);
		popupLayoutManager = new LinearLayoutManager(this);
		popupLayoutManager.setOrientation(OrientationHelper.VERTICAL);
		popupRecyclerView.setLayoutManager(popupLayoutManager);
		popupRecyclerViewAdapter = new PopupWindowRecyclerAdapter(this);
		popupRecyclerViewAdapter.setCreateViewLayout(R.layout.item_recycler_popup);

		pktInfos = new ArrayList<PktInfo>();
		popupRecyclerView.setAdapter(popupRecyclerViewAdapter);
	}

	private void updateAnalysePopupWindow(int pos){
		pktInfos.clear();
		String pcap_str = input_pcap.getText().toString();
		int stutas = parsePktInfo(pcap_str, pktInfos, videoInfos.get(pos));
		if (stutas != 0) {
			switch (stutas) {
				case -1:
					showToast("视频流分析失败，该视频流可能已经损坏！", Toast.LENGTH_SHORT);
					break;
			}
		}
		else {
			Log.i(TAG, "updateAnalysePopupWindow: pktInfos.size()=" + pktInfos.size());
			Collections.sort(pktInfos);
			for(int i = 1; i<pktInfos.size(); i++) {
				if(pktInfos.get(i).getTimestamp() < pktInfos.get(i-1).getTimestamp()){
					pktInfos.get(i).setStatus(PKTINFO_STATUS_DISORDER);
					pktInfos.get(i-1).setStatus(PKTINFO_STATUS_DISORDER);
				}
			}
			for(int i = 1; i<pktInfos.size(); i++){
				for(int j = PktInfo.shortiToUshort(pktInfos.get(i).getSeq()) - 1; j > PktInfo.shortiToUshort(pktInfos.get(i-1).getSeq()); j--){
					PktInfo lostPkt = new PktInfo();
					lostPkt.setSeq((short)j);
					lostPkt.setStatus(PKTINFO_STATUS_LOSS);
					pktInfos.add(i, lostPkt);
				}
			}
		}
		updateFilterPktInfos();
	}

	private void showAnalysePopupWindow(){
		popupRecyclerViewAdapter.notifyDataSetChanged();
		popupRecyclerView.smoothScrollToPosition(0);
		View rootview = LayoutInflater.from(this).inflate(R.layout.activity_main, null);
		analysePopupWindow.showAtLocation(rootview, Gravity.CENTER, 0, 0);
	}

	private void showCustomPayloadTypeDialog(){
		customDialogbuilder = new AlertDialog.Builder(this);
		customDialogbuilder.setTitle("自定义解码参数");
		LayoutInflater layoutInflater = getLayoutInflater();
		View dialogView = layoutInflater.inflate(R.layout.dialog_custom,null);
		final EditText edit_h264_type_num = (EditText) dialogView.findViewById(R.id.h264_type_num);

		final SharedPreferences sp = getSharedPreferences("custom",Context.MODE_PRIVATE);
		edit_h264_type_num.setText(sp.getInt("h264_type_num",98)+"");
		edit_h264_type_num.setSelection(edit_h264_type_num.getText().length());

		customDialogbuilder.setView(dialogView);
		customDialogbuilder.setPositiveButton("确定", null);
		customDialogbuilder.setNegativeButton("取消", null);
		customDialogbuilder.setNeutralButton("恢复默认值",null);
		customDialogbuilder.setCancelable(false);
		final AlertDialog dialog = customDialogbuilder.show();
		dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.customGreen));
		dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.customBlue));
		dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setTextColor(getResources().getColor(R.color.textSecondaryColor));
		dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				InputMethodManager manager= (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				manager.hideSoftInputFromWindow( dialog.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
				dialog.dismiss();

				String pcap_str = input_pcap.getText().toString();
				updateVideoInfo(pcap_str,false);
			}
		});
		dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				SharedPreferences.Editor editor = sp.edit();
				editor.putInt("h264_type_num",98);
				editor.commit();

				edit_h264_type_num.setText(sp.getInt("h264_type_num",98)+"");
				edit_h264_type_num.setSelection(edit_h264_type_num.getText().length());
			}
		});
		dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					int h264_type_num = Integer.parseInt(edit_h264_type_num.getText().toString());
					if(h264_type_num < 0 && h264_type_num > 127) {
						showToast("PayLoad Type参数范围为0~127，请输入正确的参数",Toast.LENGTH_SHORT);
						return;
					}
					SharedPreferences.Editor editor = sp.edit();
					editor.putInt("h264_type_num", h264_type_num);
					editor.commit();
					InputMethodManager manager= (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					manager.hideSoftInputFromWindow( dialog.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
					dialog.dismiss();

					String pcap_str = input_pcap.getText().toString();
					updateVideoInfo(pcap_str,false);
				}
				catch (NumberFormatException e){
					showToast("PayLoad Type参数范围为0~127，请输入正确的参数",Toast.LENGTH_SHORT);
				}
			}
		});
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		switch (requestCode) {
			case PERMISSION_REQUESTCODE:
				if (PERMISSION_WRITE_EXTERNAL_STORAGE.equals(permissions[0]) &&
						grantResults[0] == PackageManager.PERMISSION_GRANTED ) {
					Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
					intent.setType("*/*");
					intent.addCategory(Intent.CATEGORY_OPENABLE);
					startActivityForResult(Intent.createChooser(intent,"请选择文件管理器"),0);
				}
				else{
					showToast("请允许应用程序所需权限，否则无法正常工作！",Toast.LENGTH_SHORT);
				}
		}
	}

	private void showIsTryCustomParseDialog() {
		isTryCustomDialogbuilder = new AlertDialog.Builder(this);
		isTryCustomDialogbuilder.setTitle("未获取到可解析的视频流");
		if(parse_paramter_type == 0) {
			isTryCustomDialogbuilder.setMessage("PCAP包SDP信息缺失或出错，是否自定义解析参数？");
		}
		else if(parse_paramter_type == 1) {
			isTryCustomDialogbuilder.setMessage("目前自定义解析参数可能有误，是否更新自定义解析参数？");
		}

		isTryCustomDialogbuilder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				parse_paramter.check(R.id.custom);
				custom.performClick();
				dialog.dismiss();
			}
		});
		isTryCustomDialogbuilder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		isTryCustomDialogbuilder.show();
	}

	void updateVideoInfo(String input_url, Boolean isNewPath){
		if(isSelectPath) {
			videoInfos = new ArrayList<VideoInfo>();
			if (parse_paramter_type == 1) {
				final SharedPreferences sp = getSharedPreferences("custom", Context.MODE_PRIVATE);
				VideoInfo h264_paramter = new VideoInfo();
				h264_paramter.setType_num(sp.getInt("h264_type_num", 98));
				videoInfos.add(h264_paramter);
			}
			int stutas = parseVideoInfo(input_url, parse_paramter_type, videoInfos);
			if (stutas != 0) {
				switch (stutas) {
					case -1:
						showToast("无法打开文件，请检查是否拥有读写权限！", Toast.LENGTH_SHORT);
						break;
					case -2:
						showToast("无法识别文件头，该文件不是pcap文件！", Toast.LENGTH_SHORT);
						break;
				}
			} else {
				label_sdp.setVisibility(View.VISIBLE);
				if (videoInfos.size() > 0) {
					mainRecyclerViewAdapter.addDatas("infos", videoInfos);
					mainRecyclerViewAdapter.positionSet.clear();
					mainRecyclerViewAdapter.positionSet.add(0);
					mainRecyclerView.setVisibility(View.VISIBLE);
					mainRecyclerView.setAdapter(mainRecyclerViewAdapter);
					ly_none.setVisibility(View.GONE);
					toggleBtnParse(true);

					Log.i(TAG, videoInfos.get(0).toString());

					ly_output.setAlpha(1);
					input_pcap.setText(input_url);
					output_video.setEnabled(true);
					output_video.setText(OUTPUT_VIDEO_SUBSTRING_BEGIN + input_url.substring(0,
							input_url.lastIndexOf(".")) + "." + videoInfos.get(0).getA_line().toLowerCase());
					output_video.setSelection(output_video.getText().length());
					output_yuv.setEnabled(true);
					output_yuv.setText(OUTPUT_YUV_SUBSTRING_BEGIN + input_url.substring(0, input_url.lastIndexOf(".")) + ".yuv");
					output_yuv.setSelection(output_yuv.getText().length());
				} else {
					ly_none.setVisibility(View.VISIBLE);
					mainRecyclerView.setVisibility(View.GONE);
					toggleBtnParse(false);

					ly_output.setAlpha(0.5f);
					input_pcap.setText(input_url);
					output_video.setEnabled(false);
					output_video.setText("");
					output_yuv.setEnabled(false);
					output_yuv.setText("");

					if(isNewPath){
						showIsTryCustomParseDialog();
					}
				}
			}
		}
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode == Activity.RESULT_OK){
			switch (requestCode){
				case GET_CONTENT_REQUESTCODE:
					Uri uri = data.getData();
					String input_url = uri.getPath();
					if("/root".equals(input_url.substring(0,5))){
						input_url = input_url.substring(5);
					}
					if(input_url.toLowerCase().endsWith(".pcap")) {
						isSelectPath = true;
						updateVideoInfo(input_url, true);
					}
					else{
						showToast(input_url+"打开失败，该文件不是PCAP文件！", Toast.LENGTH_SHORT);
					}
				break;
			}
		}
	}

	private boolean isAppExist(String packageName){
		PackageManager packageManager = getPackageManager();
		List<PackageInfo> packageInfoList = packageManager.getInstalledPackages(0);
		for(PackageInfo info:packageInfoList){
			if(info.packageName.equalsIgnoreCase(packageName)){
				return true;
			}
		}
		return false;
	}

	static class ShowUIHandler extends Handler {
		private WeakReference<MainActivity> weakReference;
		public ShowUIHandler(MainActivity sdlActivity){
			weakReference = new WeakReference<MainActivity>(sdlActivity);
		}
		@Override
		public void handleMessage(Message msg) {
			final MainActivity activity= weakReference.get();
			if(activity != null) {
				switch (msg.what) {
					case 0:
						activity.btn_parse.setVisibility(View.VISIBLE);
						break;
				}
			}
		}
	}
	static class progressRateHandler extends Handler {
		WeakReference<MainActivity> mActivityReference;
		progressRateHandler(MainActivity activity) {
			mActivityReference= new WeakReference(activity);
		}
		@Override
		public void handleMessage(Message msg) {
			final MainActivity activity = mActivityReference.get();
			if (activity != null) {
				if(msg.what == -1) {
					if(activity.parse_method_type == 0) {
						activity.progressBar.setProgress(activity.progressBar.getMax());
						activity.showToast("PCAP解析完成", Toast.LENGTH_SHORT);
						activity.btn_parse.postDelayed(new Runnable() {
							@Override
							public void run() {
								activity.btn_parse.setClickable(true);
								activity.toggleBtnParse(true);
								activity.progressBar.setVisibility(View.INVISIBLE);
							}
						}, 200);
					}
					else if(activity.parse_method_type == 1) {
						activity.progressBar.setProgress(activity.progressBar.getMax());
						activity.btn_parse.postDelayed(new Runnable() {
							@Override
							public void run() {
								activity.btn_parse.setClickable(true);
								activity.toggleBtnParse(true);
								activity.progressBar.setVisibility(View.INVISIBLE);
							}
						}, 200);

						String h264Path = activity.output_video.getText().toString().substring(OUTPUT_VIDEO_SUBSTRING_BEGIN.length());
						File h264File = new File(h264Path);
						if (h264File.exists()) {
//							if (activity.isAppExist("com.example.ffmpegsdlplayer")) {
//								PackageManager packageManager = activity.getPackageManager();
//								Intent intent = packageManager.getLaunchIntentForPackage("com.example.ffmpegsdlplayer");
//								intent.setAction(Intent.ACTION_VIEW);
//								intent.setType(h264Path);
//								activity.startActivity(intent);
//							}
//							else{
//								activity.showToast("未找到FFmpegSDLPlayer应用，请进行安装。", Toast.LENGTH_SHORT);
//							}
							Intent intent = new Intent(activity,SDLActivity.class);
							intent.putExtra("input_url",h264Path);
							intent.putExtra("codec_type",0);
							activity.startActivity(intent);
						} else {
							activity.showToast("未找到输出文件，PCAP解析失败", Toast.LENGTH_SHORT);
						}
					}
					else if(activity.parse_method_type == 2) {
						activity.progressBar.setProgress(activity.progressBar.getMax());
						activity.btn_parse.postDelayed(new Runnable() {
							@Override
							public void run() {
								activity.btn_parse.setClickable(true);
								activity.toggleBtnParse(true);
								activity.progressBar.setVisibility(View.INVISIBLE);
							}
						}, 200);

						String h264Path = activity.output_video.getText().toString().substring(OUTPUT_VIDEO_SUBSTRING_BEGIN.length());
						File h264File = new File(h264Path);
						if (h264File.exists()) {
//							if (activity.isAppExist("com.example.ffmpegsdlplayer")) {
//								PackageManager packageManager = activity.getPackageManager();
//								Intent intent = packageManager.getLaunchIntentForPackage("com.example.ffmpegsdlplayer");
//								intent.setAction(Intent.ACTION_VIEW);
//								intent.setType(h264Path);
//								activity.startActivity(intent);
//							}
//							else{
//								activity.showToast("未找到FFmpegSDLPlayer应用，请进行安装。", Toast.LENGTH_SHORT);
//							}
							Intent intent = new Intent(activity,SDLActivity.class);
							intent.putExtra("input_url",h264Path);
							intent.putExtra("codec_type",1);
							activity.startActivity(intent);
						} else {
							activity.showToast("未找到输出文件，PCAP解析失败", Toast.LENGTH_SHORT);
						}
					}
				}
				else if(msg.what == -2) {
					activity.progressBar.setProgress(0);
					activity.showToast("PCAP解析取消", Toast.LENGTH_SHORT);
					activity.btn_parse.setClickable(true);
					activity.toggleBtnParse(true);
					activity.progressBar.setVisibility(View.INVISIBLE);
				}
				else {
					if(activity.isShow) {
						if(activity.parse_method_type == 0) {
							activity.showToast("已解析 " + msg.what + " 字节", Toast.LENGTH_SHORT);
						}
						else{
							activity.showToast("已播放 " + msg.what + " 帧", Toast.LENGTH_SHORT);
						}
					}
					activity.progressBar.setProgress(msg.what);
				}
			}
		}
	}

	//JNI
	public static void setProgressRate(int progress){
		progressRateHandler.sendEmptyMessage(progress);
//		Log.i("++++++", progress+"");
	}

	public static void setProgressRateFull(){
		time_end = System.currentTimeMillis();
		Log.i("time_duration", ""+((double)(time_end - time_start))/1000);
		progressRateHandler.sendEmptyMessage(-1);
//		Log.i("+++---", -1+"");
	}
	public static void setProgressRateEmpty(){
		progressRateHandler.sendEmptyMessage(-2);
//		Log.i("+++---", -2+"");
	}

	public native int parseVideoInfo(String pcap_str, int parse_paramter_type, ArrayList videoInfos);
	public native int parsePktInfo(String pcap_str, ArrayList pktInfos, VideoInfo videoInfo);
	public native int play(String pcap_str, String video_str, String yuv_str, VideoInfo videoInfo);
	public native void playCancel();
    public native int decode(String pcap_str, String video_str, String yuv_str, VideoInfo videoInfo);
	public native void decodeCancel();
	//public native int decode2(String pcap_str, String video_str, String yuv_str, VideoInfo videoInfo);

	static {
		System.loadLibrary("native_video");
		//System.loadLibrary("native_nvoc");
	}

	void toggleBtnParse(boolean isClickable){
		if(isClickable) {
			btn_parse.setImageResource(R.drawable.ic_export_send_while);
			btn_parse.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.customBlue)));
			btn_parse.setRippleColor(getResources().getColor(R.color.colorPrimary));
		}
		else {
			btn_parse.setImageResource(R.drawable.ic_export_send_while);
			btn_parse.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.darker_gray)));
			btn_parse.setRippleColor(getResources().getColor(R.color.lighter_gray));
		}
	}
	@Override
	protected void onResume() {
		super.onResume();
		isShow = true;
	}

	@Override
	protected void onPause() {
		super.onPause();
		isShow = false;
		cancelToast();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		cancelToast();
		if(floatingActionButtonTimer != null){
			floatingActionButtonTimer.cancel();
			floatingActionButtonTimer = null;
		}
	}

	/**
	 * 显示Toast，解决重复弹出问题
	 */
	public void showToast(String text , int time) {
		if(mToast == null) {
			mToast = Toast.makeText(this, text, time);
		} else {
			mToast.setText(text);
			mToast.setDuration(Toast.LENGTH_SHORT);
		}
		mToast.show();
	}

	/**
	 * 隐藏Toast
	 */
	public void cancelToast() {
		if (mToast != null) {
			mToast.cancel();
			mToast = null;
		}
	}


	public void onBackPressed() {
		if(parseThread != null && parseThread.isAlive()){
			if(parse_method_type == 0) {
				decodeCancel();
			}
			else{
				playCancel();
			}
			cancelToast();
		}
		else{
			cancelToast();
			super.onBackPressed();
		}
	}
}

class MainActivityRecyclerAdapter extends BaseRecyclerViewAdapter {
	private Context mContext;
	public Set<Integer> positionSet;
	public boolean isSelectAll = false;

	public MainActivityRecyclerAdapter(Context context) {
		super(context);
		mContext = context;
		positionSet = new TreeSet<Integer>();
	}

	@Override
	public int getItemCount() {
		if (mHeaderView == null && mFooterView == null) return mDatas.get("infos").size();
		if (mHeaderView != null && mFooterView != null) return mDatas.get("infos").size() + 2;
		return mDatas.get("infos").size() + 1;
	}

	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		if (viewType == TYPE_HEADER) return new Holder(mHeaderView, viewType);
		if (viewType == TYPE_FOOTER) return new Holder(mFooterView, viewType);
		View layout = mInflater.inflate(mCreateViewLayout, parent, false);
		return new Holder(layout, viewType);
	}

	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
		Holder holder = (Holder) viewHolder;
		final int pos = getRealPosition(holder);
		if (getItemViewType(position) == TYPE_HEADER || getItemViewType(position) == TYPE_FOOTER)
			return;
		if (getItemViewType(position) == TYPE_NORMAL) {
			VideoInfo info = (VideoInfo)mDatas.get("infos").get(pos);
			String ip_src = VideoInfo.byteArrayToIP(VideoInfo.intToByteArray(Integer.valueOf(info.getIp_src())));
			String ip_dst = VideoInfo.byteArrayToIP(VideoInfo.intToByteArray(Integer.valueOf(info.getIp_dst())));
			long ssrc = VideoInfo.intToUint(info.getSsrc());
			int type_num = info.getType_num();
			int pkt_count = info.getPkt_count();
			String a_line = info.getA_line();
			//holder.img.setCardBackgroundColor(ConstantUtils.getColorByID(pos%ConstantUtils.SUM_ID));
			holder.type.setText(a_line);
			holder.ip_src.setText(ip_src);
			holder.ip_dst.setText(ip_dst);
			holder.ssrc.setText(ssrc+"");
			holder.type_num.setText(type_num+"");
			holder.pkt_count.setText(pkt_count+"");
			holder.itemView.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					mOnItemClickListener.onClick(v, pos, "itemView");
				}
			});
			holder.btn_analyse.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					mOnItemClickListener.onClick(v, pos, "btn_analyse");
				}
			});
			if(pos == 0){
				holder.line.setVisibility(View.INVISIBLE);
			}
			else{
				holder.line.setVisibility(View.VISIBLE);
			}
			if (positionSet.contains(pos)) {
				holder.itemView.setBackgroundColor(mContext.getResources().getColor(R.color.lighter_gray));
			} else {
				holder.itemView.setBackgroundColor(mContext.getResources().getColor(R.color.transparent));
			}
		}
	}

	static class Holder extends RecyclerView.ViewHolder {
		public TextView ip_src;
		public TextView ip_dst;
		public TextView ssrc;
		public TextView pkt_count;
		public TextView type_num;
		public CardView img;
		public TextView type;
		public ImageView btn_analyse;
		public RelativeLayout back;
		public View line;

		public Holder(View itemView, int viewType) {
			super(itemView);
			if (viewType == TYPE_HEADER || viewType == TYPE_FOOTER) return;
			if (viewType == TYPE_NORMAL) {
				back = (RelativeLayout) itemView.findViewById(R.id.back);
				ip_src = (TextView) itemView.findViewById(R.id.ip_src);
				ip_dst = (TextView) itemView.findViewById(R.id.ip_dst);
				ssrc = (TextView) itemView.findViewById(R.id.ssrc);
				pkt_count = (TextView) itemView.findViewById(R.id.pkt_count);
				type_num = (TextView) itemView.findViewById(R.id.type_num);
				img = (CardView) itemView.findViewById(R.id.img);
				btn_analyse = (ImageView) itemView.findViewById(R.id.btn_analyse);
				type = (TextView) itemView.findViewById(R.id.type);
				line = itemView.findViewById(R.id.line);
			}
		}
	}
}

class PopupWindowRecyclerAdapter extends BaseRecyclerViewAdapter {
	private Context mContext;

	public PopupWindowRecyclerAdapter(Context context) {
		super(context);
		mContext = context;
	}

	@Override
	public int getItemCount() {
		if (mHeaderView == null && mFooterView == null) return mDatas.get("pkts").size();
		if (mHeaderView != null && mFooterView != null) return mDatas.get("pkts").size() + 2;
		return mDatas.get("pkts").size() + 1;
	}

	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		if (viewType == TYPE_HEADER) return new Holder(mHeaderView, viewType);
		if (viewType == TYPE_FOOTER) return new Holder(mFooterView, viewType);
		View layout = mInflater.inflate(mCreateViewLayout, parent, false);
		return new Holder(layout, viewType);
	}

	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
		Holder holder = (Holder) viewHolder;
		final int pos = getRealPosition(holder);
		if (getItemViewType(position) == TYPE_HEADER || getItemViewType(position) == TYPE_FOOTER)
			return;
		if (getItemViewType(position) == TYPE_NORMAL) {
			PktInfo info = (PktInfo)mDatas.get("pkts").get(pos);
			if(info.getStatus() == MainActivity.PKTINFO_STATUS_NORMAL && !MainActivity.isCheckNormal){
				return;
			}
			if(info.getStatus() == MainActivity.PKTINFO_STATUS_LOSS && !MainActivity.isCheckLoss){
				return;
			}
			if(info.getStatus() == MainActivity.PKTINFO_STATUS_DISORDER && !MainActivity.isCheckDisorder){
				return;
			}
			if(info.getStatus() == MainActivity.PKTINFO_STATUS_NORMAL){
				holder.status.setText("正常");
				holder.frame.setTextColor(mContext.getResources().getColor(R.color.textSecondaryColor));
				holder.seq.setTextColor(mContext.getResources().getColor(R.color.textSecondaryColor));
				holder.timestamp.setTextColor(mContext.getResources().getColor(R.color.textSecondaryColor));
				holder.mode.setTextColor(mContext.getResources().getColor(R.color.textSecondaryColor));
				holder.status.setTextColor(mContext.getResources().getColor(R.color.textSecondaryColor));

				holder.frame.setText(info.getFrame()+"");
				holder.seq.setText(PktInfo.shortiToUshort(info.getSeq())+"");
				holder.timestamp.setText(PktInfo.intToUint(info.getTimestamp())+"");
				if(info.getMode() == 0) {
					holder.mode.setText("Single");
				}
				else{
					if(info.getS() == 1 && info.getE() == 0){
						holder.mode.setText("FU-A Start");
					}
					if(info.getS() == 0 && info.getE() == 0){
						holder.mode.setText("FU-A          ");
					}
					if(info.getS() == 0 && info.getE() == 1){
						holder.mode.setText("FU-A End  ");
					}
				}
			}
			else if(info.getStatus() == MainActivity.PKTINFO_STATUS_LOSS){
				holder.status.setText("丢包");
				holder.frame.setTextColor(mContext.getResources().getColor(R.color.orangered));
				holder.seq.setTextColor(mContext.getResources().getColor(R.color.orangered));
				holder.timestamp.setTextColor(mContext.getResources().getColor(R.color.orangered));
				holder.mode.setTextColor(mContext.getResources().getColor(R.color.orangered));
				holder.status.setTextColor(mContext.getResources().getColor(R.color.orangered));

				holder.frame.setText("——");
				holder.seq.setText(PktInfo.shortiToUshort(info.getSeq())+"");
				holder.timestamp.setText("——");
				holder.mode.setText("——");
			}
			else if(info.getStatus() == MainActivity.PKTINFO_STATUS_DISORDER){
				holder.status.setText("乱序");
				holder.frame.setTextColor(mContext.getResources().getColor(R.color.mediumblue));
				holder.seq.setTextColor(mContext.getResources().getColor(R.color.mediumblue));
				holder.timestamp.setTextColor(mContext.getResources().getColor(R.color.mediumblue));
				holder.mode.setTextColor(mContext.getResources().getColor(R.color.mediumblue));
				holder.status.setTextColor(mContext.getResources().getColor(R.color.mediumblue));

				holder.frame.setText(info.getFrame()+"");
				holder.seq.setText(PktInfo.shortiToUshort(info.getSeq())+"");
				holder.timestamp.setText(PktInfo.intToUint(info.getTimestamp())+"");
				if(info.getMode() == 0) {
					holder.mode.setText("Single");
				}
				else{
					if(info.getS() == 1 && info.getE() == 0){
						holder.mode.setText("FU-A Start");
					}
					if(info.getS() == 0 && info.getE() == 0){
						holder.mode.setText("FU-A          ");
					}
					if(info.getS() == 0 && info.getE() == 1){
						holder.mode.setText("FU-A End  ");
					}
				}
			}
		}
	}

	static class Holder extends RecyclerView.ViewHolder {
		public TextView frame;
		public TextView seq;
		public TextView timestamp;
		public TextView mode;
		public TextView status;

		public Holder(View itemView, int viewType) {
			super(itemView);
			if (viewType == TYPE_HEADER || viewType == TYPE_FOOTER) return;
			if (viewType == TYPE_NORMAL) {
				frame = (TextView) itemView.findViewById(R.id.frame);
				seq = (TextView) itemView.findViewById(R.id.seq);
				timestamp = (TextView) itemView.findViewById(R.id.timestamp);
				mode = (TextView) itemView.findViewById(R.id.mode);
				status = (TextView) itemView.findViewById(R.id.status);
			}
		}
	}
}