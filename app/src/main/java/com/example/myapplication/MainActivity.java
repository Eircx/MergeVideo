package com.example.myapplication;

//import androidx.activity.result.ActivityResult;
//import androidx.activity.result.ActivityResultCallback;
//import androidx.activity.result.ActivityResultLauncher;
//import androidx.activity.result.contract.ActivityResultContracts;
import static com.example.myapplication.R.color.purple_200;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.lang.String;

import com.example.myapplication.databinding.ActivityMainBinding;
import com.example.myapplication.jni.FFmpegCmd;
import com.example.myapplication.utils.TermuxUtils;
import com.example.myapplication.utils.tools.Decompress;
import com.example.myapplication.utils.album.FullyGridLayoutManager;

import com.example.myapplication.utils.album.adapter.GridImageAdapter;
import com.example.myapplication.utils.tools.ClearCache;
import com.example.myapplication.utils.tools.Constants;
import com.example.myapplication.utils.tools.FFmpegUtils;

import com.example.myapplication.utils.tools.Theme;
import com.example.myapplication.utils.tools.VideoInfo;
import com.luck.picture.lib.PictureSelector;
import com.luck.picture.lib.config.PictureConfig;
import com.luck.picture.lib.config.PictureMimeType;
import com.luck.picture.lib.entity.LocalMedia;
//import com.luck.picture.lib.broadcast.BroadcastAction;
//import com.luck.picture.lib.broadcast.BroadcastManager;
//import com.luck.picture.lib.compress.Luban;
//import com.luck.picture.lib.config.PictureConfig;
//import com.luck.picture.lib.config.PictureMimeType;
//import com.luck.picture.lib.engine.ImageEngine;
//import com.luck.picture.lib.entity.LocalMedia;
//import com.luck.picture.lib.entity.MediaExtraInfo;
//import com.luck.picture.lib.manager.PictureCacheManager;
//import com.luck.picture.lib.permissions.PermissionChecker;
//import com.luck.picture.lib.tools.MediaUtils;
//import com.luck.picture.lib.tools.ToastUtils;

//import com.alibaba.fastjson.JSONObject;


import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private Button next_button, clear_cache_button, install_button;
    private EditText input_text;
    private RecyclerView videoRecycle;
    private final int roughFPS = 1;
    private GridImageAdapter mAdapter;
//    private ActivityResultLauncher<Intent> launcherResult;
    private int selectMax = 10;
    private List<VideoInfo> videoInfos;
    private TextView progress_text;
    private ProgressBar progressBar;
    private LinearLayout progress_layout;


    static {
        System.loadLibrary("myapplication");
    }

    //????????????Handler

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Constants.init(getApplicationContext());
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
        progress_layout.setVisibility(View.INVISIBLE);



        next_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (videoInfos.size() == 0){
                    Toast.makeText(getApplicationContext(), "???????????????????????????", Toast.LENGTH_SHORT).show();
                    return;
                }
                roughCut();
            }
        });
        next_button.setClickable(true);

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
                int count1 = ClearCache.delAllFile(Constants.TEMPDIR);
                Toast.makeText(getApplicationContext(), "???????????? [" + count + "," + count1 + "] " + cacheDir, Toast.LENGTH_SHORT).show();
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
        mAdapter.setOnItemClickListener(new GridImageAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position, View v) {
                List<LocalMedia> selectList = mAdapter.getData();
                if (selectList.size() > 0) {
                    LocalMedia media = selectList.get(position);
                    String mimeType = media.getPictureType();
                    int mediaType = PictureMimeType.pictureToVideo(mimeType);
                    switch (mediaType) {
                        case PictureConfig.TYPE_VIDEO:
                            // ????????????
                            PictureSelector.create(MainActivity.this).externalPictureVideo(media.getPath());
                            break;
                        default:
                            break;
                    }
                }
            }
        });
//        mAdapter.setOnItemClickListener((v, position) -> {
//            List<LocalMedia> selectList = mAdapter.getData();
//            if (selectList.size() > 0) {
//                LocalMedia media = selectList.get(position);
//                String mimeType = media.getMimeType();
//                int mediaType = PictureMimeType.getMimeType(mimeType);
//                switch (mediaType) {
//                    case PictureConfig.TYPE_VIDEO:
//                        // ????????????
//                        PictureSelector.create(MainActivity.this)
//                                .themeStyle(R.style.picture_default_style)
//                                .externalPictureVideo(TextUtils.isEmpty(media.getAndroidQToPath()) ? media.getPath() : media.getAndroidQToPath());
//                        break;
//                    default:
//                        break;
//                }
//            }
//        });
        videoRecycle.setAdapter(mAdapter);

//        // ????????????
//        BroadcastManager.getInstance(this).registerReceiver(broadcastReceiver,
//                BroadcastAction.ACTION_DELETE_PREVIEW_POSITION);

//        launcherResult = createActivityResultLauncher();

    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private final GridImageAdapter.onAddPicClickListener onAddPicClickListener = new GridImageAdapter.onAddPicClickListener() {
        @Override
        public void onAddPicClick() {
            // ???????????? ??????????????????????????????api????????????

            PictureSelector.create(MainActivity.this)
                    .openGallery(PictureMimeType.ofVideo())// ??????.PictureMimeType.ofAll()?????????.ofImage()?????????.ofVideo()?????????.ofAudio()
                    .maxSelectNum(selectMax) // ????????????????????????
                    .minSelectNum(1)// ????????????????????????
                    .imageSpanCount(4)// ??????????????????
                    .selectionMode(PictureConfig.MULTIPLE)// ?????? or ??????
                    .previewVideo(true)
                    .compress(false)
                    //.forResult(PictureConfig.CHOOSE_REQUEST);//????????????onActivityResult code
                    //.forResult(new MyResultCallback(mAdapter));
                    .forResult(PictureConfig.CHOOSE_REQUEST);


        }
    };

//
//    private ActivityResultLauncher<Intent> createActivityResultLauncher() {
//        return registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
//                new ActivityResultCallback<ActivityResult>() {
//                    @Override
//                    public void onActivityResult(ActivityResult result) {
//                        int resultCode = result.getResultCode();
//                        if (resultCode == RESULT_OK) {
//                            List<LocalMedia> selectList = PictureSelector.obtainMultipleResult(result.getData());
//                            videoInfos.clear();
//                            // ?????? LocalMedia ??????????????????path
//                            // 1.media.getPath(); ??????path
//                            // 2.media.getCutPath();?????????path????????????media.isCut();??????????????????
//                            // 3.media.getCompressPath();?????????path????????????media.isCompressed();??????????????????
//                            // 4.media.getOriginalPath()); media.isOriginal());???true?????????????????????
//                            // 5.media.getAndroidQToPath();Android Q?????????????????????????????????????????????????????????????????????????????????????????????????????????.isAndroidQTransform ???false ?????????????????????
//                            // ???????????????????????????????????????????????????????????????????????????????????????
//                            for (LocalMedia media : selectList) {
//                                Log.d("media", media.toString());
//                                videoInfos.add(new VideoInfo(media.getAndroidQToPath(), media.getDuration()));
//                                if (media.getWidth() == 0 || media.getHeight() == 0) {
//                                    if (PictureMimeType.isHasImage(media.getMimeType())) {
////                                        Log.d("media path", media.getPath());
//                                        MediaExtraInfo imageExtraInfo = MediaUtils.getImageSize(media.getPath());
//                                        media.setWidth(imageExtraInfo.getWidth());
//                                        media.setHeight(imageExtraInfo.getHeight());
//                                    } else if (PictureMimeType.isHasVideo(media.getMimeType())) {
//                                        MediaExtraInfo videoExtraInfo = MediaUtils.getVideoSize(getParent(), media.getPath());
//                                        media.setWidth(videoExtraInfo.getWidth());
//                                        media.setHeight(videoExtraInfo.getHeight());
//                                    }
//                                }
//                                // TODO ????????????PictureSelectorExternalUtils.getExifInterface();??????????????????????????????????????????????????????????????????????????????
//                            }
//                            Log.d("videoinfos", videoInfos.toString());
//                            mAdapter.setList(selectList);
//                            mAdapter.notifyDataSetChanged();
//
//                        }
//                    }
//                });
//    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        List<LocalMedia> selectedList;
        if (resultCode == RESULT_OK) {
            if (requestCode == PictureConfig.CHOOSE_REQUEST) {// ????????????????????????

                selectedList = PictureSelector.obtainMultipleResult(data);

                //selectList = PictureSelector.obtainMultipleResult(data);

                // ?????? LocalMedia ??????????????????path
                // 1.media.getPath(); ?????????path
                // 2.media.getCutPath();????????????path????????????media.isCut();?????????true
                // 3.media.getCompressPath();????????????path????????????media.isCompressed();?????????true
                // ????????????????????????????????????????????????????????????????????????????????????
                videoInfos.clear();
                for (LocalMedia media : selectedList) {
                    Log.d("media", media.toString());
                    videoInfos.add(new VideoInfo(media.getPath(), media.getDuration()));
                    if (media.getWidth() == 0 || media.getHeight() == 0) {
                        Log.d("!!! width=height=0", media.getPath());
                    }
                    // TODO ????????????PictureSelectorExternalUtils.getExifInterface();??????????????????????????????????????????????????????????????????????????????
                }
                Log.d("videoinfos", videoInfos.toString());

                mAdapter.setList(selectedList);
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    /**
     * ????????????????????????????????????AndroidQToPath??????????????????????????????????????????????????????????????????????????????????????????????????????
     */
//    private void clearCache() {
//        // ?????????????????????????????????????????????????????? ??????:????????????????????????????????? ?????????????????????
//        if (PermissionChecker.checkSelfPermission(getParent(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
//            //PictureCacheManager.deleteCacheDirFile(this, PictureMimeType.ofImage());
//            PictureCacheManager.deleteAllCacheDirRefreshFile(getParent());
//        } else {
//            PermissionChecker.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
//                    PictureConfig.APPLY_STORAGE_PERMISSIONS_CODE);
//        }
//    }

//    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            String action = intent.getAction();
//            if (TextUtils.isEmpty(action)) {
//                return;
//            }
//            if (BroadcastAction.ACTION_DELETE_PREVIEW_POSITION.equals(action)) {
//                // ??????????????????????????????
//                Bundle extras = intent.getExtras();
//                if (extras != null) {
//                    int position = extras.getInt(PictureConfig.EXTRA_PREVIEW_DELETE_POSITION);
//                    ToastUtils.s(getParent().getApplicationContext(), "delete image index:" + position);
//                    mAdapter.remove(position);
//                    mAdapter.notifyItemRemoved(position);
//                }
//            }
//        }
//    };

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
            boolean result = FFmpegUtils.resampleVideo(inPath, outPath);
            if(result)
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
//                myFilePath.delete(); // ??????????????????
//                //???????????????00???20-00???28?????????
//                String cmd = "ffmpeg -d -ss 00:00:02 -t 00:00:05 -i %s -vcodec copy -acodec copy %s";
//                cmd = String.format(cmd,input,output);
////                cmd = "ffmpeg -i %s -r 1 -f image2 %s";
////                cmd = String.format(cmd,input,output_pic);
//                cmd = "ffmpeg -i %s -s sxga -r 30 %s";
//                cmd = String.format(cmd,input,output);
//                FFmpegCmd.run(cmd.split(" "));
//                Log.d("FFmpegTest", "run: ?????????"+(System.currentTimeMillis()-startTime));
//            }
//        }.start();
//    }

    private void roughCut() {
        new Thread() {
            @Override
            public void run(){
                // ???????????????
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress_layout.setVisibility(View.VISIBLE);
                        next_button.setClickable(false);
                        clear_cache_button.setClickable(false);
                        install_button.setClickable(false);
                    }
                });

                String sentences = input_text.getText().toString();

                // ???????????????
                String outputDir = Constants.getRunningDir() + "/resample";
                File f = new File(outputDir);
                if (f.exists()){
                    ClearCache.delFolder(f.getAbsolutePath());
                }
                List<String> resampleResults = resampleVideos(videoInfos, outputDir);

                // ????????????
                String[] frames = resampleResults.toArray(new String[0]);
                Theme theme = new Theme();
                theme.recogize(frames, sentences);
                Log.d("music", "theme:" + theme.getTheme() + " fpm:" + theme.getMusic().fpm + "src:" + theme.getMusic().src);
                Constants.updateBGM(theme.getMusic());

//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        List<LocalMedia> selectList = new ArrayList<>();
//                        mAdapter.setList(selectList);
//                        mAdapter.notifyDataSetChanged();
//                        input_text.setText("");
//                        progress_layout.setVisibility(View.INVISIBLE);
//                        clear_cache_button.setClickable(true);
//                        next_button.setClickable(true);
//                        Intent editIntent = new Intent(MainActivity.this, EditActivity.class);
//                        editIntent.putExtra("sentences", sentences);
//                        editIntent.putExtra("theme", theme.getTheme());
//                        editIntent.putExtra("cut_files", resampleResults.toArray(new String[0]));
//                        startActivity(editIntent);
//                        Log.d("MainActivity", "End!!");
//                        Log.d("path", Environment.getExternalStorageDirectory().getAbsolutePath());
//                    }
//                });




                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress_layout.setVisibility(View.VISIBLE);
                        progress_text.setText("VSE?????????");
                    }
                });
                List<String> roughCut_results = new ArrayList<>();
                for (int i = 0; i < resampleResults.size(); ++i) {
                    String srcVideo = resampleResults.get(i);
                    String frame_dir = "/data/data/com.example.myapplication/tempfile/frames";
                    File ff = new File(frame_dir);
                    if (ff.exists() && ff.isDirectory()) {
                        ClearCache.delFolder(frame_dir);
                    }

                    FFmpegUtils.getFrames(srcVideo, frame_dir, roughFPS);
                    File img_list_file = new File(frame_dir);
                    String[] img_list = img_list_file.list();
                    Log.d("image_list", String.valueOf(img_list.length));
                    String src_json = frame_dir + "/input.json";

                    try{
                        OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(src_json),"UTF-8");

                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("text", sentences);
                        if (img_list.length <= 6) {
                            for (int j = 0; j < img_list.length; ++j) {
                                jsonObject.accumulate("imgs",frame_dir + "/" + img_list[j]);
                            }
                        } else {
                            for (double j = 0; j < img_list.length; j += (img_list.length - 1) / 5.0) {
                                jsonObject.accumulate("imgs",frame_dir + "/" + img_list[(int)j]);
                                Log.d("selected frame", String.valueOf((int)j));
                            }
                        }



                        String jsonConent = jsonObject.toString().replace("\\", "");
//                        Log.d("jsonContent", jsonConent);
                        osw.write(jsonConent);
                        osw.flush();//????????????????????????????????????
                        osw.close();//???????????????
                        TermuxUtils.runVse(frame_dir);

                        File cut_txt = new File(frame_dir, "rough_cut.txt");
                        if (cut_txt.exists()) {
                            BufferedReader reader = new BufferedReader(new FileReader(cut_txt));
                            String tempString = null;
                            int cut_num = 0;
                            // ?????????????????????????????????null???????????????
                            List<String> tmp_results = new ArrayList<>();
                            while ((tempString = reader.readLine()) != null) {
                                // ????????????
                                System.out.println("start-end [" + tempString + "]");
                                String[] tmp = tempString.split(" ");
                                int start = Integer.valueOf(tmp[0]), end = Integer.valueOf(tmp[1]);
                                if ((float)(end - start) / (float)roughFPS <= Constants.minRoughInterval){
                                    continue;
                                }
                                String outPath = Constants.getRunningDir() + String.format("/rough/%d-%d.mp4", i, cut_num);
                                FFmpegUtils.cutVideo(srcVideo, outPath, start, end, roughFPS);
                                tmp_results.add(outPath);
                                cut_num++;
                            }
                            reader.close();

                            if (tmp_results.size() > 0){
                                // ??????????????????????????????????????????#????????????
                                String tmp_string = tmp_results.get(0);
                                for (int j = 1; j < tmp_results.size(); ++j) {
                                    tmp_string += "#" + tmp_results.get(j);
                                }
                                Log.d("Rough Result String", tmp_string);
                                roughCut_results.add(tmp_string);
                            } else {
                                roughCut_results.add("");
                            }
                        }

                    } catch (Exception e){
                        e.printStackTrace();
                    }


                }

                String[] results = roughCut_results.toArray(new String[0]);


                // ????????????
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        List<LocalMedia> selectList = new ArrayList<>();
                        mAdapter.setList(selectList);
                        mAdapter.notifyDataSetChanged();
                        input_text.setText("");
                        progress_layout.setVisibility(View.INVISIBLE);
                        clear_cache_button.setClickable(true);
                        next_button.setClickable(true);
                        Intent editIntent = new Intent(MainActivity.this, EditActivity.class);
                        editIntent.putExtra("sentences", sentences);
                        editIntent.putExtra("cut_files", results);
                        editIntent.putExtra("theme", theme.getTheme());
                        startActivity(editIntent);
                        Log.d("MainActivity", "End!!");
                    }
                });
            }
        }.start();
    }

    private void installJittor() {
        Context mc = getApplicationContext();
        Toast.makeText(mc,"????????????", Toast.LENGTH_LONG).show();
        try {
            // install environment from termux.
            Decompress dc = new Decompress();
            dc.unzipFromAssets(getApplicationContext(),"termux.zip","/data/data/com.example.myapplication/");
            Toast.makeText(mc,"????????????", Toast.LENGTH_SHORT).show();
            Log.d("termux", "????????????");

            Process process1 = Runtime.getRuntime().exec("chmod 777 -R ./termux",null,new File("/data/data/com.example.myapplication/"));
            // install jittor code
            File jtdir = new File("/data/data/com.example.myapplication/myjittor");
            if(!jtdir.exists()){
                jtdir.mkdir();
            }
            //start
            dc.unzipFromAssets(getApplicationContext(),"jittor.tgz","/data/data/com.example.myapplication/myjittor");
            Toast.makeText(mc,"Jittor????????????", Toast.LENGTH_SHORT).show();
            Process process2 = Runtime.getRuntime().exec("chmod 777 -R ./myjittor",null,new File("/data/data/com.example.myapplication/"));
            File tmpdir = new File("/data/data/com.example.myapplication/tempfile");
            if(!tmpdir.exists()){
                tmpdir.mkdir();
            }
            Process process3 = Runtime.getRuntime().exec("chmod 777 -R ./tempfile",null,new File("/data/data/com.example.myapplication/"));
            dc.unzipFromAssets(getApplicationContext(),"vse.tgz","/data/data/com.example.myapplication");
            Toast.makeText(mc,"VSE????????????", Toast.LENGTH_SHORT).show();
            Process process4 = Runtime.getRuntime().exec("chmod 777 -R ./vsepp-jittor",null,new File("/data/data/com.example.myapplication/"));
                        File tmpdir2 = new File("/data/data/com.example.myapplication/tempfile");
                        if(!tmpdir2.exists()){
                            tmpdir2.mkdir();
                        }
            // unzip music
            File musicDir = new File("/data/data/com.example.myapplication/music");
            if(!musicDir.exists()){
                musicDir.mkdir();
            }
            dc.unzipFromAssets(getApplicationContext(), "music.tgz", "/data/data/com.example.myapplication/music");


            initJittor();
        }
        catch (Error e){
            e.printStackTrace();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }


    void initJittor() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @SuppressLint("ResourceAsColor")
                    @Override
                    public void run() {
                        progress_layout.setVisibility(View.VISIBLE);
                        progress_text.setText("Jittor ????????????");
                        next_button.setBackgroundTintList(ColorStateList.valueOf(R.color.app_color_divider));
                        next_button.setClickable(false);
                        videoRecycle.setClickable(false);
                    }
                });
                TermuxUtils.installJittor();
                runOnUiThread(new Runnable() {
                    @SuppressLint("ResourceAsColor")
                    @Override
                    public void run() {
                        progress_layout.setVisibility(View.INVISIBLE);
                        next_button.setClickable(true);
                        next_button.setBackgroundTintList(ColorStateList.valueOf(purple_200));
                        videoRecycle.setClickable(true);
                    }
                });
            }
        }).start();
    }



}