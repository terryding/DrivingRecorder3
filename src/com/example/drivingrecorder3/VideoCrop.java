package com.example.drivingrecorder3;

import android.annotation.SuppressLint;
import android.os.Environment;
import android.util.Log;

//import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Container;
import com.coremedia.iso.boxes.TimeToSampleBox;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;

import java.io.File;
//import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Shortens/Crops a track
 */
public class VideoCrop {


    @SuppressLint("SimpleDateFormat")
	public static String crop(String fileName) throws IOException {
        //Movie movie = new MovieCreator().build(new RandomAccessFile("/home/sannies/suckerpunch-distantplanet_h1080p/suckerpunch-distantplanet_h1080p.mov", "r").getChannel());
        Movie movie = MovieCreator.build(fileName);
        
        List<Track> tracks = movie.getTracks();
        movie.setTracks(new LinkedList<Track>());
        // remove all tracks we will create new tracks from the old

        //double startTime = 0;
        //double endTime = (double) getDuration(tracks.get(0)) / tracks.get(0).getTrackMetaData().getTimescale();
        double endTime = (double) getDuration(tracks.get(0)) / tracks.get(0).getTrackMetaData().getTimescale();
        Log.d("ClipTest", "endTime = " + endTime);
        double startTime = endTime - 10.000 < 0 ? 0 : endTime - 10.000;
        boolean timeCorrected = false;

        // Here we try to find a track that has sync samples. Since we can only start decoding
        // at such a sample we SHOULD make sure that the start of the new fragment is exactly
        // such a frame
        for (Track track : tracks) {
            if (track.getSyncSamples() != null && track.getSyncSamples().length > 0) {
                if (timeCorrected) {
                    // This exception here could be a false positive in case we have multiple tracks
                    // with sync samples at exactly the same positions. E.g. a single movie containing
                    // multiple qualities of the same video (Microsoft Smooth Streaming file)

                    throw new RuntimeException("The startTime has already been corrected by another track with SyncSample. Not Supported.");
                }
                startTime = correctTimeToSyncSample(track, startTime, false);
                endTime = correctTimeToSyncSample(track, endTime, true);
                timeCorrected = true;
            }
        }

        for (Track track : tracks) {
            long currentSample = 0;
            double currentTime = 0;
            long startSample = -1;
            long endSample = -1;

            for (int i = 0; i < track.getDecodingTimeEntries().size(); i++) {
                TimeToSampleBox.Entry entry = track.getDecodingTimeEntries().get(i);
                for (int j = 0; j < entry.getCount(); j++) {
                    // entry.getDelta() is the amount of time the current sample covers.

                    if (currentTime <= startTime) {
                        // current sample is still before the new starttime
                        startSample = currentSample;
                    }
                    if (currentTime <= endTime) {
                        // current sample is after the new start time and still before the new endtime
                        endSample = currentSample;
                    } else {
                        // current sample is after the end of the cropped video
                        break;
                    }
                    currentTime += (double) entry.getDelta() / (double) track.getTrackMetaData().getTimescale();
                    currentSample++;
                }
            }
            movie.addTrack(new CroppedTrack(track, startSample, endSample));
        }
        long start1 = System.currentTimeMillis();
        Container out = new DefaultMp4Builder().build(movie);
        long start2 = System.currentTimeMillis();
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), "DrivingRecorder");
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        //String outName = mediaStorageDir.getPath() + File.separator + "CLP_"+ timeStamp + ".mp4";
        String outName = String.format(mediaStorageDir.getPath() + File.separator + "CLP_"+ timeStamp + "-%f-%f.mp4", startTime, endTime);
        FileOutputStream fos = new FileOutputStream(outName);
        FileChannel fc = fos.getChannel();
        out.writeContainer(fc);
        fc.close();
        fos.close();
        long start3 = System.currentTimeMillis();
        System.err.println("Building IsoFile took : " + (start2 - start1) + "ms");
        System.err.println("Writing IsoFile took  : " + (start3 - start2) + "ms");
        System.err.println("Writing IsoFile speed : " + (new File(String.format("output-%f-%f.mp4", startTime, endTime)).length() / (start3 - start2) / 1000) + "MB/s");
        return outName;
    }

    protected static long getDuration(Track track) {
        long duration = 0;
        for (TimeToSampleBox.Entry entry : track.getDecodingTimeEntries()) {
            duration += entry.getCount() * entry.getDelta();
        }
        return duration;
    }

    private static double correctTimeToSyncSample(Track track, double cutHere, boolean next) {
        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
        long currentSample = 0;
        double currentTime = 0;
        for (int i = 0; i < track.getDecodingTimeEntries().size(); i++) {
            TimeToSampleBox.Entry entry = track.getDecodingTimeEntries().get(i);
            for (int j = 0; j < entry.getCount(); j++) {
                if (Arrays.binarySearch(track.getSyncSamples(), currentSample + 1) >= 0) {
                    // samples always start with 1 but we start with zero therefore +1
                    timeOfSyncSamples[Arrays.binarySearch(track.getSyncSamples(), currentSample + 1)] = currentTime;
                }
                currentTime += (double) entry.getDelta() / (double) track.getTrackMetaData().getTimescale();
                currentSample++;
            }
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