package com.iflytek.mscv5plusdemo;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.LexiconListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.VoiceWakeuper;
import com.iflytek.cloud.WakeuperListener;
import com.iflytek.cloud.WakeuperResult;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.iflytek.cloud.util.ContactManager;
import com.iflytek.cloud.util.ContactManager.ContactListener;
import com.iflytek.cloud.util.ResourceUtil;
import com.iflytek.speech.setting.IatSettings;
import com.iflytek.speech.util.FucUtil;
import com.iflytek.speech.util.JsonParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;



public class IatDemo extends Activity implements OnClickListener{
	VoiceWakeuper mIvw;
	private int stop_listen=0;
	private String keep_alive = "1";
	private String ivwNetMode = "0";
	private int curThresh = 50;
	// 语音合成对象
	private SpeechSynthesizer mTts;
	//播放进度
	private int mPercentForPlaying = 0;
	//缓冲进度
	private int mPercentForBuffering = 0;
	private static String TAG = "IatDemo";
	// 语音听写对象
	private SpeechRecognizer mIat;
	// 语音听写UI
	private RecognizerDialog mIatDialog;
	// 听写结果内容
	private EditText mResultText;
	// 用HashMap存储听写结果
	private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();
	
	private Toast mToast;

	private SharedPreferences mSharedPreferences;

	private JSONObject jsonObject;

	public Intent intent = null;
	public int request_code=0;

	private Handler handler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			showTip("你说的是：" + mResultText.getText());


			String text2 = null;
			try {
				text2 = jsonObject.getString("tts");
				if(text2.equals("")) text2="对不起，我暂时无法处理";
				mResultText.setText(text2);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			// 设置参数
			setParam();
			int code = mTts.startSpeaking(text2, mTtsListener);
//			/**
//			 * 只保存音频不进行播放接口,调用此接口请注释startSpeaking接口
//			 * text:要合成的文本，uri:需要保存的音频全路径，listener:回调接口
//			*/
//			String path = Environment.getExternalStorageDirectory()+"/tts.pcm";
//			int code = mTts.synthesizeToUri(text, path, mTtsListener);

			if (code != ErrorCode.SUCCESS) {
				showTip("语音合成失败,错误码: " + code);
			}
		}
	};

	public String des_latitude="39.915119";
	public String des_longitude="116.403963";

	public void start_navi(){
		//stop_wake();

		intent = new Intent(this, GPSNaviActivity.class);
		if(intent!=null){
			//用Bundle携带数据
			Bundle bundle=new Bundle();
			//传递name参数为tinyphp
			bundle.putString("latitude", des_latitude);
			bundle.putString("longitude", des_longitude);
			intent.putExtras(bundle);
//			startActivity(intent);
//			startActivity(intent);
			startActivityForResult(intent,1);
		}else{

			showTip("启动导航失败");
		}
		//start_wake();
	}

	public static String postDownloadJson(String path,String post){
		URL url = null;
		try {
			url = new URL(path);
			HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
			httpURLConnection.setRequestMethod("POST");// 提交模式
			// conn.setConnectTimeout(10000);//连接超时 单位毫秒
			// conn.setReadTimeout(2000);//读取超时 单位毫秒
			// 发送POST请求必须设置如下两行
			httpURLConnection.setDoOutput(true);
			httpURLConnection.setDoInput(true);
			// 获取URLConnection对象对应的输出流
			PrintWriter printWriter = new PrintWriter(httpURLConnection.getOutputStream());
			// 发送请求参数
			printWriter.write(post);//post的参数 xx=xx&yy=yy
			// flush输出流的缓冲
			printWriter.flush();
			//开始获取数据
			BufferedInputStream bis = new BufferedInputStream(httpURLConnection.getInputStream());
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			int len;
			byte[] arr = new byte[1024];
			while((len=bis.read(arr))!= -1){
				bos.write(arr,0,len);
				bos.flush();
			}
			bos.close();
			return bos.toString("utf-8");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public boolean post(String info) throws Exception {
		String token="123456";
		String params = "info=" +URLEncoder.encode(info)+"&token="+URLEncoder.encode(token);
		String result=postDownloadJson("http://120.24.15.92",params);
		showTip(result);
		try {
			//result="{\"tts\":\"服务端处理返回的结果\"}";
			jsonObject = new JSONObject(result);
		}catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return true;
	}

	private String getResource() {
		final String resPath = ResourceUtil.generateResourcePath(IatDemo.this, ResourceUtil.RESOURCE_TYPE.assets, "ivw/" + getString(R.string.app_id) + ".jet");
		Log.d(TAG, "resPath: " + resPath);
		return resPath;
	}

	public void start_listen(){
		need_start=1;
		mResultText.setText(null);// 清空显示内容
		mIatResults.clear();
		// 设置参数
		setParam();
		boolean isShowDialog = mSharedPreferences.getBoolean(getString(R.string.pref_key_iat_show), true);
		if (isShowDialog) {
			// 显示听写对话框
			mIatDialog.setListener(mRecognizerDialogListener);
			mIatDialog.show();
			showTip(getString(R.string.text_begin));
		} else {
			// 不显示听写对话框
			ret = mIat.startListening(mRecognizerListener);
			if (ret != ErrorCode.SUCCESS) {
				showTip("听写失败,错误码：" + ret);
			} else {
				showTip(getString(R.string.text_begin));
			}
		}
	}

	private WakeuperListener mWakeuperListener = new WakeuperListener() {

		@Override
		public void onResult(WakeuperResult result) {
			stop_listen=0;
			Log.d(TAG, "onResult");
			if(!"1".equalsIgnoreCase(keep_alive)) {
				//setRadioEnable(true);
			}
			try {
				String text = result.getResultString();
				JSONObject object;
				object = new JSONObject(text);
				StringBuffer buffer = new StringBuffer();
				buffer.append("【RAW】 "+text);
				buffer.append("\n");
				buffer.append("【操作类型】"+ object.optString("sst"));
				buffer.append("\n");
				buffer.append("【唤醒词id】"+ object.optString("id"));
				buffer.append("\n");
				buffer.append("【得分】" + object.optString("score"));
				buffer.append("\n");
				buffer.append("【前端点】" + object.optString("bos"));
				buffer.append("\n");
				buffer.append("【尾端点】" + object.optString("eos"));
				//resultString =buffer.toString();

//				showTip(object.optString("id"));
				if(object.optString("id")=="0"){
					showTip("已打开导航");
					start_navi();
					return;
				}
			} catch (JSONException e) {
				//resultString = "结果解析出错";
				e.printStackTrace();
			}


			//textView.setText(resultString);
			showTip("已唤醒,请开始输入");

			stop_wake();

			// 设置参数
			setParam();
			need_start=0;
			int code = mTts.startSpeaking("你好，你的小宝贝已上线", mTtsListener);

//			/**
//			 * 只保存音频不进行播放接口,调用此接口请注释startSpeaking接口
//			 * text:要合成的文本，uri:需要保存的音频全路径，listener:回调接口
//			*/
//			String path = Environment.getExternalStorageDirectory()+"/tts.pcm";
//			int code = mTts.synthesizeToUri(text, path, mTtsListener);

			if (code != ErrorCode.SUCCESS) {
				showTip("语音合成失败,错误码: " + code);
			}


//			mResultText.setText(null);// 清空显示内容
//			mIatResults.clear();
//			// 设置参数
//			setParam();
//			boolean isShowDialog = mSharedPreferences.getBoolean(getString(R.string.pref_key_iat_show), true);
//			if (isShowDialog) {
//				// 显示听写对话框
//				mIatDialog.setListener(mRecognizerDialogListener);
//				mIatDialog.show();
//				showTip(getString(R.string.text_begin));
//			} else {
//				// 不显示听写对话框
//				ret = mIat.startListening(mRecognizerListener);
//				if (ret != ErrorCode.SUCCESS) {
//					showTip("听写失败,错误码：" + ret);
//				} else {
//					showTip(getString(R.string.text_begin));
//				}
//			}
		}

		@Override
		public void onError(SpeechError error) {
			showTip(error.getPlainDescription(true));
			//setRadioEnable(true);
		}

		@Override
		public void onBeginOfSpeech() {
		}

		@Override
		public void onEvent(int eventType, int isLast, int arg2, Bundle obj) {
			switch( eventType ){
				// EVENT_RECORD_DATA 事件仅在 NOTIFY_RECORD_DATA 参数值为 真 时返回
				case SpeechEvent.EVENT_RECORD_DATA:
					final byte[] audio = obj.getByteArray( SpeechEvent.KEY_EVENT_RECORD_DATA );
					Log.i( TAG, "ivw audio length: "+audio.length );
					break;
			}
		}

		@Override
		public void onVolumeChanged(int volume) {

		}
	};

	public void start_wake(){
		mIvw = VoiceWakeuper.getWakeuper();
		if(mIvw != null) {

			// 清空参数
			mIvw.setParameter(SpeechConstant.PARAMS, null);
			// 唤醒门限值，根据资源携带的唤醒词个数按照“id:门限;id:门限”的格式传入
			mIvw.setParameter(SpeechConstant.IVW_THRESHOLD, "0:10;1:10;2:10");
			// 设置唤醒模式
			mIvw.setParameter(SpeechConstant.IVW_SST, "wakeup");
			// 设置持续进行唤醒
			mIvw.setParameter(SpeechConstant.KEEP_ALIVE, keep_alive);
			// 设置闭环优化网络模式
			mIvw.setParameter(SpeechConstant.IVW_NET_MODE, ivwNetMode);
			// 设置唤醒资源路径
			mIvw.setParameter(SpeechConstant.IVW_RES_PATH, getResource());
			// 设置唤醒录音保存路径，保存最近一分钟的音频
			mIvw.setParameter( SpeechConstant.IVW_AUDIO_PATH, Environment.getExternalStorageDirectory().getPath()+"/msc/ivw.wav" );
			mIvw.setParameter( SpeechConstant.AUDIO_FORMAT, "wav" );
			// 如有需要，设置 NOTIFY_RECORD_DATA 以实时通过 onEvent 返回录音音频流字节
			//mIvw.setParameter( SpeechConstant.NOTIFY_RECORD_DATA, "1" );

			// 启动唤醒
			mIvw.startListening(mWakeuperListener);
		} else {
			showTip("唤醒未初始化");
		}
	}

	public void stop_wake(){
		mIvw.stopListening();
	}

	private InitListener mTtsInitListener = new InitListener() {
		@Override
		public void onInit(int code) {
			Log.d(TAG, "InitListener init() code = " + code);
			if (code != ErrorCode.SUCCESS) {
				showTip("初始化失败,错误码："+code);
			} else {
				// 初始化成功，之后可以调用startSpeaking方法
				// 注：有的开发者在onCreate方法中创建完合成对象之后马上就调用startSpeaking进行合成，
				// 正确的做法是将onCreate中的startSpeaking调用移至这里
			}
		}
	};

	@SuppressLint("ShowToast")
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.iatdemo);
		initLayout();
		// 初始化识别无UI识别对象
		// 使用SpeechRecognizer对象，可根据回调消息自定义界面；
		mIat = SpeechRecognizer.createRecognizer(this, mInitListener);
		
		// 初始化听写Dialog，如果只使用有UI听写功能，无需创建SpeechRecognizer
		// 使用UI听写功能，请根据sdk文件目录下的notice.txt,放置布局文件和图片资源
		mIatDialog = new RecognizerDialog(this,mInitListener);
		
		mSharedPreferences = getSharedPreferences(IatSettings.PREFER_NAME, Activity.MODE_PRIVATE);
		mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);	
		mResultText = ((EditText)findViewById(R.id.iat_text));

		mIvw = VoiceWakeuper.createWakeuper(this, null);
		// 初始化合成对象
		mTts = SpeechSynthesizer.createSynthesizer(this, mTtsInitListener);
		start_wake();

		mResultText.setText("请对着手机说\"开启聊天\"");
		try {
			jsonObject=new JSONObject("{\"actionCode\":\"NEED_MORE\"}");
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 初始化Layout。
	 */
	private void initLayout(){
		findViewById(R.id.iat_recognize).setOnClickListener(this);
		findViewById(R.id.iat_recognize_stream).setOnClickListener(this);
		findViewById(R.id.iat_upload_contacts).setOnClickListener(this);
		findViewById(R.id.iat_upload_userwords).setOnClickListener(this);	
		findViewById(R.id.iat_stop).setOnClickListener(this);
		findViewById(R.id.iat_cancel).setOnClickListener(this);
		findViewById(R.id.image_iat_set).setOnClickListener(this);
	}

	int ret = 0;// 函数调用返回值
	@Override
	public void onClick(View view) {		
		if( null == mIat ){
			// 创建单例失败，与 21001 错误为同样原因，参考 http://bbs.xfyun.cn/forum.php?mod=viewthread&tid=9688
			this.showTip( "创建对象失败，请确认 libmsc.so 放置正确，\n 且有调用 createUtility 进行初始化" );
			return;
		}
		
		switch (view.getId()) {
		// 进入参数设置页面
		case R.id.image_iat_set:
			Intent intents = new Intent(IatDemo.this, IatSettings.class);
			startActivity(intents);
			break;
		// 开始听写
		// 如何判断一次听写结束：OnResult isLast=true 或者 onError
		case R.id.iat_recognize:
			mResultText.setText(null);// 清空显示内容
			mIatResults.clear();
			// 设置参数
			setParam();
			boolean isShowDialog = mSharedPreferences.getBoolean(getString(R.string.pref_key_iat_show), true);
			if (isShowDialog) {
				// 显示听写对话框
				mIatDialog.setListener(mRecognizerDialogListener);
				mIatDialog.show();
				showTip(getString(R.string.text_begin));
			} else {
				// 不显示听写对话框
				ret = mIat.startListening(mRecognizerListener);
				if (ret != ErrorCode.SUCCESS) {
					showTip("听写失败,错误码：" + ret);
				} else {
					showTip(getString(R.string.text_begin));
				}
			}
			break;
		// 音频流识别
		case R.id.iat_recognize_stream:
			mResultText.setText(null);// 清空显示内容
			mIatResults.clear();
			// 设置参数
			setParam();
			// 设置音频来源为外部文件
			mIat.setParameter(SpeechConstant.AUDIO_SOURCE, "-1");
			// 也可以像以下这样直接设置音频文件路径识别（要求设置文件在sdcard上的全路径）：
			// mIat.setParameter(SpeechConstant.AUDIO_SOURCE, "-2");
			// mIat.setParameter(SpeechConstant.ASR_SOURCE_PATH, "sdcard/XXX/XXX.pcm");
			ret = mIat.startListening(mRecognizerListener);
			if (ret != ErrorCode.SUCCESS) {
				showTip("识别失败,错误码：" + ret);
			} else {
				byte[] audioData = FucUtil.readAudioFile(IatDemo.this, "iattest.wav");
				
				if (null != audioData) {
					showTip(getString(R.string.text_begin_recognizer));
					// 一次（也可以分多次）写入音频文件数据，数据格式必须是采样率为8KHz或16KHz（本地识别只支持16K采样率，云端都支持），位长16bit，单声道的wav或者pcm
					// 写入8KHz采样的音频时，必须先调用setParameter(SpeechConstant.SAMPLE_RATE, "8000")设置正确的采样率
					// 注：当音频过长，静音部分时长超过VAD_EOS将导致静音后面部分不能识别
					mIat.writeAudio(audioData, 0, audioData.length);
					mIat.stopListening();
				} else {
					mIat.cancel();
					showTip("读取音频流失败");
				}
			}
			break;
		// 停止听写
		case R.id.iat_stop:
			mIat.stopListening();
			showTip("停止听写");
			break;
		// 取消听写
		case R.id.iat_cancel:
			mIat.cancel();
			showTip("取消听写");
			break;
		// 上传联系人
		case R.id.iat_upload_contacts:
			showTip(getString(R.string.text_upload_contacts));
			ContactManager mgr = ContactManager.createManager(IatDemo.this, mContactListener);	
			mgr.asyncQueryAllContactsName();
			break;
			// 上传用户词表
		case R.id.iat_upload_userwords:
			showTip(getString(R.string.text_upload_userwords));
			String contents = FucUtil.readFile(IatDemo.this, "userwords","utf-8");
			mResultText.setText(contents);
			// 指定引擎类型
			mIat.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
			// 置编码类型
			mIat.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
			ret = mIat.updateLexicon("userword", contents, mLexiconListener);
			if (ret != ErrorCode.SUCCESS)
				showTip("上传热词失败,错误码：" + ret);
			break;
		default:
			break;
		}
	}

	/**
	 * 初始化监听器。
	 */
	private InitListener mInitListener = new InitListener() {

		@Override
		public void onInit(int code) {
			Log.d(TAG, "SpeechRecognizer init() code = " + code);
			if (code != ErrorCode.SUCCESS) {
				showTip("初始化失败，错误码：" + code);
			}
		}
	};

	/**
	 * 上传联系人/词表监听器。
	 */
	private LexiconListener mLexiconListener = new LexiconListener() {

		@Override
		public void onLexiconUpdated(String lexiconId, SpeechError error) {
			if (error != null) {
				showTip(error.toString());
			} else {
				showTip(getString(R.string.text_upload_success));
			}
		}
	};

	/**
	 * 听写监听器。
	 */
	private RecognizerListener mRecognizerListener = new RecognizerListener() {

		@Override
		public void onBeginOfSpeech() {
			// 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
			showTip("开始说话");
		}

		@Override
		public void onError(SpeechError error) {
			// Tips：
			// 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
			showTip(error.getPlainDescription(true));
			// 设置参数
			setParam();
			need_start=1;
			stop_listen=0;
			int code = mTts.startSpeaking("你能不能快一点说啊，人家都等得着急了呢", mTtsListener);

//			/**
//			 * 只保存音频不进行播放接口,调用此接口请注释startSpeaking接口
//			 * text:要合成的文本，uri:需要保存的音频全路径，listener:回调接口
//			*/
//			String path = Environment.getExternalStorageDirectory()+"/tts.pcm";
//			int code = mTts.synthesizeToUri(text, path, mTtsListener);

			if (code != ErrorCode.SUCCESS) {
				showTip("语音合成失败,错误码: " + code);
			}
		}

		@Override
		public void onEndOfSpeech() {
			// 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
			showTip("结束说话");
		}




		@Override
		public void onResult(RecognizerResult results, boolean isLast) {		
			String text = JsonParser.parseIatResult(results.getResultString());
			mResultText.append(text);
			mResultText.setSelection(mResultText.length());
			if(isLast) {
				//TODO 最后的结果
//				new Thread(){
//
//					@Override
//
//					public void run() {
//
//						try {
//							post("播放小苹果");
//						} catch (Exception e) {
//							e.printStackTrace();
//						}
//
//						myHandler.sendEmptyMessage(0);
//					}
//				}.start();

				showTip("你说的是："+mResultText.getText());
				stop_wake();
			}
		}

		@Override
		public void onVolumeChanged(int volume, byte[] data) {
			showTip("当前正在说话，音量大小：" + volume);
			Log.d(TAG, "返回音频数据："+data.length);
		}

		@Override
		public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
			// 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
			// 若使用本地能力，会话id为null
			//	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
			//		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
			//		Log.d(TAG, "session id =" + sid);
			//	}
		}
	};
	public int need_start=0;

	public int read_news=0;

	/**
	 * 合成回调监听。
	 */
	private SynthesizerListener mTtsListener = new SynthesizerListener() {

		@Override
		public void onSpeakBegin() {
			showTip("开始播放");
		}

		@Override
		public void onSpeakPaused() {
			showTip("暂停播放");
		}

		@Override
		public void onSpeakResumed() {
			showTip("继续播放");
		}

		@Override
		public void onBufferProgress(int percent, int beginPos, int endPos,
									 String info) {
			// 合成进度
			mPercentForBuffering = percent;
			showTip(String.format(getString(R.string.tts_toast_format),
					mPercentForBuffering, mPercentForPlaying));
		}

		@Override
		public void onSpeakProgress(int percent, int beginPos, int endPos) {
			// 播放进度
			mPercentForPlaying = percent;
			showTip(String.format(getString(R.string.tts_toast_format),
					mPercentForBuffering, mPercentForPlaying));
		}



		@Override
		public void onCompleted(SpeechError error) {
			if (error == null) {
				showTip("播放完成");
				if(need_start==1){
					showTip("need start 1");
					try {
						showTip(jsonObject.getString("actionCode"));
						if(!jsonObject.getString("actionCode").equals("NEED_MORE")){
							showTip("进入处理逻辑");
                            try {
                                if(jsonObject.getString("actionCode").equals("PHONE")){
									stop_listen=1;
                                    Intent intent = new Intent(Intent.ACTION_CALL);
                                    Uri data = Uri.parse("tel:" + jsonObject.getJSONObject("appState").getJSONObject("parametes").getString("phone_number"));
                                    intent.setData(data);
                                    startActivity(intent);
                                }else if(jsonObject.getString("actionCode").equals("NAVIGATION")){
                                    stop_listen=1;
                                    des_latitude=jsonObject.getJSONObject("appState").getJSONObject("parametes").getString("latitude");
                                    des_longitude=jsonObject.getJSONObject("appState").getJSONObject("parametes").getString("longitude");
                                    showTip("准备进入导航");
                                    start_navi();
                                }else if(jsonObject.getString("actionCode").equals("NEWS")&&read_news==0){
									// 设置参数
									read_news=1;
									stop_listen=1;
									setParam();
									int code = mTts.startSpeaking(jsonObject.getJSONObject("parametes").getJSONArray("news").getJSONObject(0).getString("article"), mTtsListener);
									mResultText.setText(jsonObject.getJSONObject("parametes").getJSONArray("news").getJSONObject(0).getString("article"));
									if (code != ErrorCode.SUCCESS) {
										showTip("语音合成失败,错误码: " + code);
									}
								}else{
									showTip("未找到操作："+jsonObject.getString("actionCode"));
								}

								jsonObject.put("actionCode","NEED_MORE");
                            } catch (JSONException e) {
								showTip("处理过程中出现了一些bug");
                                e.printStackTrace();
                            }
                        }else{
							showTip("进入处理逻辑失败");
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}


					if(stop_listen==0){
						start_listen();
					}else if(read_news==1){
						read_news=2;
					}else if(read_news==2){
						read_news=0;
						stop_listen=0;
						start_listen();
					}else{
						start_wake();
						showTip("已进入监听模式");
					}


					//start_wake();
				}else if(need_start==0){
					showTip("need start 0");
					start_listen();
				}else{
					showTip("need start ?");
				}
			} else if (error != null) {
				showTip(error.getPlainDescription(true));
			}
		}

		@Override
		public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
			// 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
			// 若使用本地能力，会话id为null
			//	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
			//		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
			//		Log.d(TAG, "session id =" + sid);
			//	}
		}
	};
	
	/**
	 * 听写UI监听器
	 */
	private RecognizerDialogListener mRecognizerDialogListener = new RecognizerDialogListener() {
		public void onResult(RecognizerResult results, boolean isLast) {
			Log.d(TAG, "recognizer result：" + results.getResultString());
			String text = JsonParser.parseIatResult(results.getResultString());
			mResultText.append(text);
			mResultText.setSelection(mResultText.length());
			if(isLast) {
				//TODO 最后的结果

				if(mResultText.getText().toString().indexOf("去休息")!=-1||mResultText.getText().toString().indexOf("滚")!=-1){
					// 设置参数
					stop_listen=1;
					setParam();
					int code = mTts.startSpeaking("好的，我去休息了", mTtsListener);

					if (code != ErrorCode.SUCCESS) {
						showTip("语音合成失败,错误码: " + code);
					}
					return;
				}

				if(mResultText.getText().toString().indexOf("退出导航")!=-1){
					// 设置参数

					finishActivity(1);
//					stop_listen=0;
//					need_start=1;
//					setParam();
//					int code = mTts.startSpeaking("已退出导航", mTtsListener);
//
//					if (code != ErrorCode.SUCCESS) {
//						showTip("语音合成失败,错误码: " + code);
//					}

					start_wake();
					return;
				}

				new Thread(){

					@Override

					public void run() {

						try {
							post(""+mResultText.getText());
						} catch (Exception e) {
							e.printStackTrace();
						}

						handler.sendEmptyMessage(0);
					}
				}.start();

//				showTip("你说的是："+mResultText.getText());
//				String text2 = "你说的是："+mResultText.getText();
//				// 设置参数
//				setParam();
//				int code = mTts.startSpeaking(text2, mTtsListener);
////			/**
////			 * 只保存音频不进行播放接口,调用此接口请注释startSpeaking接口
////			 * text:要合成的文本，uri:需要保存的音频全路径，listener:回调接口
////			*/
////			String path = Environment.getExternalStorageDirectory()+"/tts.pcm";
////			int code = mTts.synthesizeToUri(text, path, mTtsListener);
//
//				if (code != ErrorCode.SUCCESS) {
//					showTip("语音合成失败,错误码: " + code);
//				}
			}


		}

		/**
		 * 识别回调错误.
		 */
		public void onError(SpeechError error) {
			showTip(error.getPlainDescription(true));
			setParam();
			need_start=1;
			stop_listen=0;
			int code = mTts.startSpeaking("你能不能快一点说啊，人家都等得着急了呢", mTtsListener);

//			/**
//			 * 只保存音频不进行播放接口,调用此接口请注释startSpeaking接口
//			 * text:要合成的文本，uri:需要保存的音频全路径，listener:回调接口
//			*/
//			String path = Environment.getExternalStorageDirectory()+"/tts.pcm";
//			int code = mTts.synthesizeToUri(text, path, mTtsListener);

			if (code != ErrorCode.SUCCESS) {
				showTip("语音合成失败,错误码: " + code);
			}
			//start_wake();
		}

	};

	/**
	 * 获取联系人监听器。
	 */
	private ContactListener mContactListener = new ContactListener() {

		@Override
		public void onContactQueryFinish(final String contactInfos, boolean changeFlag) {
			// 注：实际应用中除第一次上传之外，之后应该通过changeFlag判断是否需要上传，否则会造成不必要的流量.
			// 每当联系人发生变化，该接口都将会被回调，可通过ContactManager.destroy()销毁对象，解除回调。
			// if(changeFlag) {
			// 指定引擎类型
			runOnUiThread(new Runnable() {
				public void run() {
					mResultText.setText(contactInfos);
				}
			});
			
			mIat.setParameter(SpeechConstant.ENGINE_TYPE,SpeechConstant.TYPE_CLOUD);
			mIat.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
			ret = mIat.updateLexicon("contact", contactInfos, mLexiconListener);
			if (ret != ErrorCode.SUCCESS) {
				showTip("上传联系人失败：" + ret);
			}
		}
	};

	private void showTip(final String str)
	{
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mToast.setText(str);
				mToast.show();
			}
		});
	}

	/**
	 * 参数设置
	 * @param param
	 * @return 
	 */
	public void setParam(){
		// 清空参数
		mIat.setParameter(SpeechConstant.PARAMS, null);
		String lag = mSharedPreferences.getString("iat_language_preference", "mandarin");
		// 设置引擎
		mIat.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
		// 设置返回结果格式
		mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");

		if (lag.equals("en_us")) {
			// 设置语言
			mIat.setParameter(SpeechConstant.LANGUAGE, "en_us");
		}else {
			// 设置语言
			mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
			// 设置语言区域
			mIat.setParameter(SpeechConstant.ACCENT,lag);
		}

		// 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
		mIat.setParameter(SpeechConstant.VAD_BOS, mSharedPreferences.getString("iat_vadbos_preference", "4000"));
		
		// 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
		mIat.setParameter(SpeechConstant.VAD_EOS, mSharedPreferences.getString("iat_vadeos_preference", "1000"));
		
		// 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
		mIat.setParameter(SpeechConstant.ASR_PTT, mSharedPreferences.getString("iat_punc_preference", "1"));
		
		// 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
		// 注：AUDIO_FORMAT参数语记需要更新版本才能生效
		mIat.setParameter(SpeechConstant.AUDIO_FORMAT,"wav");
		mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/msc/iat.wav");
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		if( null != mIat ){
			// 退出时释放连接
			mIat.cancel();
			mIat.destroy();
		}
	}
}
