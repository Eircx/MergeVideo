package com.example.myapplication;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.String;

import com.example.myapplication.databinding.ActivityMainBinding;
import com.example.myapplication.jni.FFmpegCmd;
import com.example.myapplication.utils.Decompress;
import com.example.myapplication.utils.FullyGridLayoutManager;
import com.example.myapplication.utils.GlideEngine;
import com.example.myapplication.utils.adapter.GridImageAdapter;
import com.example.myapplication.utils.tools.ClearCache;
import com.example.myapplication.utils.tools.Constants;
import com.example.myapplication.utils.tools.CutVideo;
import com.example.myapplication.utils.tools.FFmepgUtils;
import com.example.myapplication.utils.tools.SelectVideos;
import com.example.myapplication.utils.tools.VideoInfo;
import com.luck.picture.lib.PictureSelector;
import com.luck.picture.lib.broadcast.BroadcastAction;
import com.luck.picture.lib.broadcast.BroadcastManager;
import com.luck.picture.lib.camera.CustomCameraType;
import com.luck.picture.lib.compress.Luban;
import com.luck.picture.lib.config.PictureConfig;
import com.luck.picture.lib.config.PictureMimeType;
import com.luck.picture.lib.engine.ImageEngine;
import com.luck.picture.lib.entity.LocalMedia;
import com.luck.picture.lib.entity.MediaExtraInfo;
import com.luck.picture.lib.manager.PictureCacheManager;
import com.luck.picture.lib.permissions.PermissionChecker;
import com.luck.picture.lib.tools.MediaUtils;
import com.luck.picture.lib.tools.ToastUtils;
import com.yalantis.ucrop.view.OverlayView;

//import com.alibaba.fastjson.JSONObject;


import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private Button next_button, clear_cache_button, install_button;
    private EditText input_text;
    private RecyclerView videoRecycle;
    private GridImageAdapter mAdapter;
    private ActivityResultLauncher<Intent> launcherResult;
    private int selectMax = 10;
    private List<VideoInfo> videoInfos;
    private int interval = 5;
    private TextView progress_text;
    private ProgressBar progressBar;
    private LinearLayout progress_layout;
    private final String TEMPDIR = "/data/data/com.example.myapplication/tempfile";

    static {
        System.loadLibrary("myapplication");
    }

    //主线程的Handler

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String cacheDir = getApplicationContext().getExternalCacheDir().getParent() + "/files/Movies";
        Constants.updateCacheDir(cacheDir);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        next_button = binding.buttonNext;
        clear_cache_button = binding.clearCache;
        input_text = binding.editTextTextMultiLine;
        videoRecycle = binding.recycler;
        progressBar = binding.progressBar;
        progress_text = binding.progressText;
        progress_layout = binding.progressLayout;
        next_button.setClickable(true);
        install_button = binding.installButton;
        videoInfos = new ArrayList<>();
        Toast.makeText(this, FFmpegCmd.test(), Toast.LENGTH_SHORT).show();
//        ffmpegTest();



        next_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                roughCut();
            }
        });

        install_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                install_button.setClickable(false);
                installJittor();
            }
        });

        clear_cache_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.e("cache", getApplicationContext().getExternalCacheDir().getParent());
                String cacheDir = Constants.getCacheDir();
                int count = ClearCache.delFolder(cacheDir);
                int count1 = ClearCache.delAllFile(TEMPDIR);
                Toast.makeText(getApplicationContext(), "清空缓存 [" + count + "," + count1 + "] " + cacheDir, Toast.LENGTH_SHORT).show();
            }
        });

        FullyGridLayoutManager manager = new FullyGridLayoutManager(this,
                4, GridLayoutManager.VERTICAL, false);
        videoRecycle.setLayoutManager(manager);

        mAdapter = new GridImageAdapter(this, onAddPicClickListener);
        if (savedInstanceState != null && savedInstanceState.getParcelableArrayList("selectorList") != null) {
            mAdapter.setList(savedInstanceState.getParcelableArrayList("selectorList"));
        }
        mAdapter.setSelectMax(selectMax);
        mAdapter.setOnItemClickListener((v, position) -> {
            List<LocalMedia> selectList = mAdapter.getData();
            if (selectList.size() > 0) {
                LocalMedia media = selectList.get(position);
                String mimeType = media.getMimeType();
                int mediaType = PictureMimeType.getMimeType(mimeType);
                switch (mediaType) {
                    case PictureConfig.TYPE_VIDEO:
                        // 预览视频
                        PictureSelector.create(MainActivity.this)
                                .themeStyle(R.style.picture_default_style)
                                .externalPictureVideo(TextUtils.isEmpty(media.getAndroidQToPath()) ? media.getPath() : media.getAndroidQToPath());
                        break;
                    default:
                        break;
                }
            }
        });
        videoRecycle.setAdapter(mAdapter);

        // 注册广播
        BroadcastManager.getInstance(this).registerReceiver(broadcastReceiver,
                BroadcastAction.ACTION_DELETE_PREVIEW_POSITION);

        launcherResult = createActivityResultLauncher();


        

    }

    @Override
    protected void onStart() {
        super.onStart();
        SelectVideos.clear();
        next_button.setClickable(true);
        progress_layout.setVisibility(View.INVISIBLE);
    }

    private final GridImageAdapter.onAddPicClickListener onAddPicClickListener = new GridImageAdapter.onAddPicClickListener() {
        @Override
        public void onAddPicClick() {
            // 进入相册 以下是例子：不需要的api可以不写
            PictureSelector.create(MainActivity.this)
                    .openGallery(PictureMimeType.ofVideo())// 全部.PictureMimeType.ofAll()、图片.ofImage()、视频.ofVideo()、音频.ofAudio()
                    .imageEngine(GlideEngine.createGlideEngine())// 外部传入图片加载引擎，必传项
                    .isWeChatStyle(true)// 是否开启微信图片选择风格
                    .isUseCustomCamera(false)// 是否使用自定义相机
                    .isMaxSelectEnabledMask(true)// 选择数到了最大阀值列表是否启用蒙层效果
//                    .setQuerySandboxDirectory(createCustomCameraOutPath())// 查询自定义相机输出目录
                    .isGetOnlySandboxDirectory(false) // 是否只显示某个目录下的资源；需与setQuerySandboxDirectory相对应
                    .maxVideoSelectNum(selectMax) // 视频最大选择数量
                    .minVideoSelectNum(1)// 视频最小选择数量
                    .imageSpanCount(4)// 每行显示个数
                    .isReturnEmpty(true)// 未选择数据时点击按钮是否可以返回
                    .isAndroidQTransform(true)// 是否需要处理Android Q 拷贝至应用沙盒的操作，只针对compress(false); && .isEnableCrop(false);有效,默认处理
                    .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)// 设置相册Activity方向，不设置默认使用系统
                    .isDisplayOriginalSize(true)// 是否显示原文件大小，isOriginalImageControl true有效

                    .selectionMode(PictureConfig.MULTIPLE)// 多选 or 单选
                    .isPreviewVideo(true)// 是否可预览视频
                    .isCompress(true)// 是否压缩
                    .synOrAsy(false)//同步true或异步false 压缩 默认同步
                    .minimumCompressSize(100)// 小于多少kb的图片不压缩
                    //.forResult(PictureConfig.CHOOSE_REQUEST);//结果回调onActivityResult code
                    //.forResult(new MyResultCallback(mAdapter));
                    .forResult(launcherResult);


        }
    };


    private ActivityResultLauncher<Intent> createActivityResultLauncher() {
        return registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        int resultCode = result.getResultCode();
                        if (resultCode == RESULT_OK) {
                            List<LocalMedia> selectList = PictureSelector.obtainMultipleResult(result.getData());
                            videoInfos.clear();
                            // 例如 LocalMedia 里面返回五种path
                            // 1.media.getPath(); 原图path
                            // 2.media.getCutPath();裁剪后path，需判断media.isCut();切勿直接使用
                            // 3.media.getCompressPath();压缩后path，需判断media.isCompressed();切勿直接使用
                            // 4.media.getOriginalPath()); media.isOriginal());为true时此字段才有值
                            // 5.media.getAndroidQToPath();Android Q版本特有返回的字段，但如果开启了压缩或裁剪还是取裁剪或压缩路径；注意：.isAndroidQTransform 为false 此字段将返回空
                            // 如果同时开启裁剪和压缩，则取压缩路径为准因为是先裁剪后压缩
                            for (LocalMedia media : selectList) {
                                Log.d("media", media.toString());
                                videoInfos.add(new VideoInfo(media.getAndroidQToPath(), media.getDuration()));
                                if (media.getWidth() == 0 || media.getHeight() == 0) {
                                    if (PictureMimeType.isHasImage(media.getMimeType())) {
//                                        Log.d("media path", media.getPath());
                                        MediaExtraInfo imageExtraInfo = MediaUtils.getImageSize(media.getPath());
                                        media.setWidth(imageExtraInfo.getWidth());
                                        media.setHeight(imageExtraInfo.getHeight());
                                    } else if (PictureMimeType.isHasVideo(media.getMimeType())) {
                                        MediaExtraInfo videoExtraInfo = MediaUtils.getVideoSize(getParent(), media.getPath());
                                        media.setWidth(videoExtraInfo.getWidth());
                                        media.setHeight(videoExtraInfo.getHeight());
                                    }
                                }
                                // TODO 可以通过PictureSelectorExternalUtils.getExifInterface();方法获取一些额外的资源信息，如旋转角度、经纬度等信息
                            }
                            Log.d("videoinfos", videoInfos.toString());
                            mAdapter.setList(selectList);
                            mAdapter.notifyDataSetChanged();

                        }
                    }
                });
    }

    /**
     * 清空缓存包括裁剪、压缩、AndroidQToPath所生成的文件，注意调用时机必须是处理完本身的业务逻辑后调用；非强制性
     */
    private void clearCache() {
        // 清空图片缓存，包括裁剪、压缩后的图片 注意:必须要在上传完成后调用 必须要获取权限
        if (PermissionChecker.checkSelfPermission(getParent(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            //PictureCacheManager.deleteCacheDirFile(this, PictureMimeType.ofImage());
            PictureCacheManager.deleteAllCacheDirRefreshFile(getParent());
        } else {
            PermissionChecker.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PictureConfig.APPLY_STORAGE_PERMISSIONS_CODE);
        }
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TextUtils.isEmpty(action)) {
                return;
            }
            if (BroadcastAction.ACTION_DELETE_PREVIEW_POSITION.equals(action)) {
                // 外部预览删除按钮回调
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    int position = extras.getInt(PictureConfig.EXTRA_PREVIEW_DELETE_POSITION);
                    ToastUtils.s(getParent().getApplicationContext(), "delete image index:" + position);
                    mAdapter.remove(position);
                    mAdapter.notifyItemRemoved(position);
                }
            }
        }
    };

    public native String stringFromJNI();

    private List<String> resampleVideos(List<VideoInfo> infos, String outDir) {
        int num = infos.size();
        List<String> results = new ArrayList<>();
        File outputDir = new File(outDir);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        Log.d("resample videos", "MKDIR" + outDir);
        for (int i = 0; i < num; ++i) {
            long startTime = System.currentTimeMillis();
            String inPath = infos.get(i).path;
            String short_name = String.valueOf(i) + ".mp4";
            String outPath = outDir + "/" + short_name;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progress_text.setText("FFmpeg resampling video " + short_name);
                }
            });
            FFmepgUtils.resampleVideo(inPath, outPath);
            results.add(outPath);
        }
        Log.d("Main activity", "finish resampling");
        return results;
    }


//    private void ffmpegTest() {
//        new Thread(){
//            @Override
//            public void run() {
//                long startTime = System.currentTimeMillis();
//                String input = "storage/emulated/0/Android/data/com.example.myapplication/files/Movies/test.mp4";
//                String output = "/storage/emulated/0/Android/data/com.example.myapplication/files/Movies/test1.mp4";
//                String output_pic = "/storage/emulated/0/Android/data/com.example.myapplication/files/Movies/test2.jpeg";
//                java.io.File myFilePath = new java.io.File(output);
//                myFilePath.delete(); // 删除空文件夹
//                //剪切视频从00：20-00：28的片段
//                String cmd = "ffmpeg -d -ss 00:00:02 -t 00:00:05 -i %s -vcodec copy -acodec copy %s";
//                cmd = String.format(cmd,input,output);
////                cmd = "ffmpeg -i %s -r 1 -f image2 %s";
////                cmd = String.format(cmd,input,output_pic);
//                cmd = "ffmpeg -i %s -s sxga -r 30 %s";
//                cmd = String.format(cmd,input,output);
//                FFmpegCmd.run(cmd.split(" "));
//                Log.d("FFmpegTest", "run: 耗时："+(System.currentTimeMillis()-startTime));
//            }
//        }.start();
//    }

    private void roughCut() {
        new Thread() {
            @Override
            public void run(){
                // 开启进度条
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress_layout.setVisibility(View.VISIBLE);
                        next_button.setClickable(false);
                    }
                });

                String sentences = input_text.getText().toString();

                // 视频重采样
                String outputDir = Constants.getRunningDir() + "/resample";
                File f = new File(outputDir);
                if (f.exists()){
                    ClearCache.delFolder(f.getAbsolutePath());
                }
                List<String> cutResults = resampleVideos(videoInfos, outputDir);
                String[] results = cutResults.toArray(new String[0]);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress_layout.setVisibility(View.VISIBLE);
                        progress_text.setText("VSE运行中");
                    }
                });

                List<String> roughCut_inputs = new ArrayList<>();
                for (int i = 0; i < cutResults.size(); ++i) {
                    String srcVideo = cutResults.get(i);
                    String frame_dir = "/data/data/com.example.myapplication/tempfile/frames";
                    File ff = new File(frame_dir);
                    if (ff.exists() && ff.isDirectory()) {
                        ClearCache.delFolder(frame_dir);
                    }

//                    roughCut_inputs.add(frame_dir);
                    FFmepgUtils.getFrames(srcVideo, frame_dir);
                    File img_list_file = new File(frame_dir);
                    String[] img_list = img_list_file.list();
                    Log.d("image_list", String.valueOf(img_list.length));
                    String src_json = frame_dir + "/input.json";

                    try{
                        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(src_json),"UTF-8");

                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("text", sentences);
                        for (int j = 0; j < img_list.length; ++j) {
//                            jsonObject.fluentPut("imgs", frame_dir + "/" + img_list[j]);
                            jsonObject.accumulate("imgs",frame_dir + "/" + img_list[j]);
                        }
                        String jsonConent = jsonObject.toString().replace("\\", "");
                        Log.d("jsonContent", jsonConent);
                        osw.write(jsonConent);
                        osw.flush();//清空缓冲区，强制输出数据
                        osw.close();//关闭输出流
                        runVse(frame_dir);

                    } catch (Exception e){
                        e.printStackTrace();
                    }






                }

//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        progress_layout.setVisibility(View.VISIBLE);
//                        progress_text.setText("VSE 运行中");
//
//                    }
//                });

//                for (String inDir : roughCut_inputs) {
//                    runVse(inDir);
//                }


                List<LocalMedia> selectList = new ArrayList<>();



                // 页面跳转
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.setList(selectList);
                        mAdapter.notifyDataSetChanged();
                        input_text.setText("");
                        Intent editIntent = new Intent(MainActivity.this, EditActivity.class);
                        editIntent.putExtra("sentences", sentences);
                        editIntent.putExtra("cut_files", results);
                        startActivity(editIntent);
                        Log.d("MainActivity", "End!!");
                    }
                });
            }
        }.start();
    }

    private void installJittor() {
        Context mc = getApplicationContext();
        Toast.makeText(mc,"开始安装", Toast.LENGTH_SHORT).show();
        try {
            // install environment from termux.
            Decompress dc = new Decompress();
            dc.unzipFromAssets(getApplicationContext(),"termux.zip","/data/data/com.example.myapplication/");
            Toast.makeText(mc,"安装成功", Toast.LENGTH_SHORT).show();
            Log.d("termux", "安装成功");

            Process process1 = Runtime.getRuntime().exec("chmod 777 -R ./termux",null,new File("/data/data/com.example.myapplication/"));
            // install jittor code
            File jtdir = new File("/data/data/com.example.myapplication/myjittor");
            if(jtdir.exists()){
                jtdir.mkdir();
            }
            //start
            dc.unzipFromAssets(getApplicationContext(),"jittor.tgz","/data/data/com.example.myapplication/myjittor");
            Toast.makeText(mc,"Jittor安装成功", Toast.LENGTH_SHORT).show();
            Process process2 = Runtime.getRuntime().exec("chmod 777 -R ./myjittor",null,new File("/data/data/com.example.myapplication/"));
            File tmpdir = new File("/data/data/com.example.myapplication/tempfile");
            if(!tmpdir.exists()){
                tmpdir.mkdir();
            }
            Process process3 = Runtime.getRuntime().exec("chmod 777 -R ./tempfile",null,new File("/data/data/com.example.myapplication/"));
            dc.unzipFromAssets(getApplicationContext(),"vse.tgz","/data/data/com.example.myapplication");
            Toast.makeText(mc,"VSE安装成功", Toast.LENGTH_SHORT).show();
            Process process4 = Runtime.getRuntime().exec("chmod 777 -R ./vsepp-jittor",null,new File("/data/data/com.example.myapplication/"));
                        File tmpdir2 = new File("/data/data/com.example.myapplication/tempfile");
                        if(!tmpdir2.exists()){
                            tmpdir2.mkdir();
                        }
            //end
        }
        catch (Error e){
            e.printStackTrace();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    void runVse(String inDir){
        try{
            Process process = Runtime.getRuntime().exec("./termux/bin/sh", null, new File("/data/data/com.example.myapplication/"));
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
//            os.writeBytes("chmod 777 -R ./tempfile" + "\n");
//            os.writeBytes("chmod 777 -R ./vsepp-jittor" + "\n");
//            os.writeBytes("./termux/bin/sh" + "\n");
//            Log.d("runVse", "1");
            os.writeBytes("mkdir -p .cache/jittor/default/clang" + "\n");
            os.writeBytes("cd .cache/jittor/default/clang" + "\n");
            os.writeBytes("ln -s /data/data/com.example.myapplication/termux/lib/crtbegin_so.o crtbegin_so.o" + "\n");
            os.writeBytes("ln -s /data/data/com.example.myapplication/termux/lib/crtend_so.o crtend_so.o" + "\n");
            os.writeBytes("ln -s jit_utils_core.cpython-39.so libjit_utils_core.so" + "\n");
            os.writeBytes("ln -s jittor_core.cpython-39.so libjittor_core.so" + "\n");
            os.writeBytes("export LD_LIBRARY_PATH=/data/data/com.example.myapplication/termux/lib:/data/data/com.example.myapplication/.cache/jittor/default/clang" + "\n");
            os.writeBytes("export PATH=/data/data/com.example.myapplication/termux/bin:$PATH" + "\n");
            os.writeBytes("export cc_path=clang" + "\n");
            os.writeBytes("export TMPDIR=/data/data/com.example.myapplication/tempfile" + "\n");
            os.writeBytes("echo test \n");
            os.writeBytes("export PYTHONPATH=/data/data/com.example.myapplication/myjittor" + "\n");
            os.writeBytes("export C_INCLUDE_PATH=/data/data/com.example.myapplication/.cache/jittor/default/clang:/data/data/com.example.myapplication/termux/include:/data/data/com.example.myapplication/termux/include/c++/v1" + "\n");
            os.writeBytes("export CPLUS_INCLUDE_PATH=/data/data/com.example.myapplication/.cache/jittor/default/clang:/data/data/com.example.myapplication/termux/include:/data/data/com.example.myapplication/termux/include/c++/v1" + "\n");
            os.writeBytes("python -c 'print(123)'" + "\n");
            String comm = "export input_json=\"" + inDir + "/input.json\"" + "\n";
            System.out.println(comm);
            os.writeBytes(comm);
            os.writeBytes("cd /data/data/com.example.myapplication/vsepp-jittor/\n");
            os.writeBytes("is_mobile=1 use_c=0 python run.py\n");
            os.writeBytes("exit" + "\n");
            os.flush();
            String temp;
            String temp2;
            String show = "";
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((temp = reader.readLine()) != null) {
                show = show + temp + "\n";
                Log.d("PYTHON", temp);
                System.out.println(temp);
            }
            BufferedReader errorreader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((temp2 = errorreader.readLine()) != null) {
                show = show + temp2 + "\n";
                Log.d("PYTHON ERROR", temp2);
                System.out.println(temp2);
            }
            process.waitFor();
            Log.d("runVSE", "finish vse");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}