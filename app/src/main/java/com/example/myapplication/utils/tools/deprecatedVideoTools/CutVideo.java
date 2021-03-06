package com.example.myapplication.utils.tools.deprecatedVideoTools;


import android.text.TextUtils;
import android.util.Log;

import com.coremedia.iso.boxes.Container;
import com.example.myapplication.utils.tools.VideoInfo;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Author: AlanWang4523.
 * Date: 17/3/21 11:40.
 * Mail: alanwang6584@gmail.com
 * 在引入ffmpeg之前的视频剪辑工具，功能不完善，现在已经弃用
 */

public class CutVideo {
    private static final String TAG = "CLIP VIDEO";

    public static List<String> clipVideos(List<VideoInfo> infos, String outDir, double interval) {
        int num = infos.size();
        List<String> results = new ArrayList<>();
        File outputDir = new File(outDir);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        for (int i = 0; i < num; ++i) {
            double a = 0, stop = infos.get(i).len;
            String inPath = infos.get(i).path;
            int count = 0;
            while(a + 1000 * interval < stop) {
                double b = a + interval * 1000;

                String short_name = String.valueOf(i) + "_" + String.valueOf(count) + ".mp4";
                String outPath = outDir + "/" + short_name;
                try{
                    clip(inPath, outPath, a, b);
                    results.add(outPath);
                } catch (Exception e) {
                    Log.e("Error in CutVideo", e.getMessage());
                }
                count++; a += interval * 1000;
            }
        }
        return results;
    }
    /**
     * 裁剪视频
     * @param srcPath 需要裁剪的原视频路径
     * @param outPath 裁剪后的视频输出路径
     * @param startTimeMs 裁剪的起始时间
     * @param endTimeMs 裁剪的结束时间
     */
    private static void clip(String srcPath, String outPath, double startTimeMs, double endTimeMs) throws IOException, IllegalArgumentException {
        if (TextUtils.isEmpty(srcPath) || TextUtils.isEmpty(outPath)) {
            throw new IllegalArgumentException("file path can't be null!!!!");
        }
        if (!(new File(srcPath).exists())) {
            throw new IllegalArgumentException("the source file is not exist!!!!");
        }
        if (startTimeMs >= endTimeMs) {
            throw new IllegalArgumentException("the startTimeMs is larger than endTimeMs!!!!");
        }
        Movie movie = MovieCreator.build(srcPath);
        List<Track> tracks = movie.getTracks();
        //移除旧的track
        movie.setTracks(new LinkedList<Track>());
        //处理的时间以秒为单位
        double startTime = startTimeMs/1000;
        double endTime = endTimeMs/1000;
        Log.d(TAG, "--->>>>startTimeMs = " + startTimeMs + "\n endTimeMs = " + endTimeMs + "\n tracks.size = " + tracks.size());
        //计算剪切时间，视频的采样间隔大，以视频为准
        for (Track track : tracks) {
            if (track.getSyncSamples() != null && track.getSyncSamples().length > 0) {
                startTime = correctTimeToSyncSample(track, startTime, false);
                endTime = correctTimeToSyncSample(track, endTime, true);
                if (track.getHandler().equals("vide")) {
                    break;
                }
            }
        }
        Log.d(TAG, "--->>>>startTime = " + startTime + "\n endTime = " + endTime);

        long currentSample;
        double currentTime;
        double lastTime;
        long startSample1;
        long endSample1;
        long delta;

        for (Track track : tracks) {
            currentSample = 0;
            currentTime = 0;
            lastTime = -1;
            startSample1 = -1;
            endSample1 = -1;

            //根据起始时间和截止时间获取起始sample和截止sample的位置
            for (int i = 0; i < track.getSampleDurations().length; i++) {
                delta = track.getSampleDurations()[i];
                if (currentTime > lastTime && currentTime <= startTime) {
                    startSample1 = currentSample;
                }
                if (currentTime > lastTime && currentTime <= endTime) {
                    endSample1 = currentSample;
                }
                lastTime = currentTime;
                currentTime += (double)delta / (double)track.getTrackMetaData().getTimescale();
                currentSample++;
            }
            Log.d(TAG, "track.getHandler() = " + track.getHandler() + "\n startSample1 = " + startSample1 + "\n endSample1 = " + endSample1);
            if (startSample1 <= 0 && endSample1 <= 0) {
                throw new RuntimeException("clip failed !!");
            }
            movie.addTrack(new CroppedTrack(track, startSample1, endSample1));// 添加截取的track
        }

        //合成视频mp4
        Container out = new DefaultMp4Builder().build(movie);
        FileOutputStream fos = new FileOutputStream(outPath);
        FileChannel fco = fos.getChannel();
        out.writeContainer(fco);
        fco.close();
        fos.close();
    }


    /**
     * 换算剪切时间
     * @param track
     * @param cutHere
     * @param next
     * @return
     */
    public static double correctTimeToSyncSample(Track track, double cutHere, boolean next) {
        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
        long currentSample = 0;
        double currentTime = 0;
        for (int i = 0; i < track.getSampleDurations().length; i++) {
            long delta = track.getSampleDurations()[i];
            int index = Arrays.binarySearch(track.getSyncSamples(), currentSample + 1);
            if (index >= 0) {
                timeOfSyncSamples[index] = currentTime;
            }
            currentTime += ((double)delta / (double)track.getTrackMetaData().getTimescale());
            currentSample++;
        }
        double previous = 0;
        for (double timeOfSyncSample : timeOfSyncSamples) {
            if (timeOfSyncSample > cutHere) {
                if (next) {
                    return timeOfSyncSample;
                } else {
                    return previous;
                }
            }
            previous = timeOfSyncSample;
        }
        return timeOfSyncSamples[timeOfSyncSamples.length - 1];
    }

}

