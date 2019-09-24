package com.hellsayenci.mrzscanner;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;

import com.googlecode.leptonica.android.Pixa;
import com.googlecode.tesseract.android.TessBaseAPI;

/**
 * Created by jsjem on 17.11.2016.
 */
public class TextRecognitionHelper {

	private static final String TAG = "TextRecognitionHelper";

	private static final String TESSERACT_TRAINED_DATA_FOLDER = "tessdata";
	private static String TESSERACT_PATH = null;

	private final Context applicationContext;
	private final TessBaseAPI tessBaseApi;

	private String REGEX_MRZ_LINE_1 = "[A-Z0-9<]{2}[A-Z<]{3}[A-Z0-9<]{39}";
	private String REGEX_MRZ_LINE_2 = "[A-Z0-9<]{9}[0-9]{1}[A-Z<]{3}[0-9]{6}[0-9]{1}[FM<]{1}[0-9]{6}[0-9]{1}[A-Z0-9<]{14}[0-9]{1}[0-9]{1}";
	Pattern line1Pattern = Pattern.compile(REGEX_MRZ_LINE_1);
	Pattern line2Pattern = Pattern.compile(REGEX_MRZ_LINE_2);

	private OnMRZScanned listener;

	private HashMap<String, Integer> scanResults = new HashMap<>();

	/**
	 * Constructor.
	 *
	 * @param context Application context.
	 */
	public TextRecognitionHelper(final Context context, final OnMRZScanned listener) {
		this.applicationContext = context.getApplicationContext();
		this.listener = listener;
		this.tessBaseApi = new TessBaseAPI();
		this.TESSERACT_PATH = context.getFilesDir().getAbsolutePath() + "/";
		prepareTesseract("ocrb");
	}

	/**
	 * Initialize tesseract engine.
	 *
	 * @param language Language code in ISO-639-3 format.
	 */
	public void prepareTesseract(final String language) {
		try {
			prepareDirectory(TESSERACT_PATH + TESSERACT_TRAINED_DATA_FOLDER);
		} catch (Exception e) {
			e.printStackTrace();
		}

		copyTessDataFiles(TESSERACT_TRAINED_DATA_FOLDER);
		tessBaseApi.init(TESSERACT_PATH, language);
	}

	private void prepareDirectory(String path) {

		File dir = new File(path);
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				Log.e(TAG,
						"ERROR: Creation of directory " + path + " failed, check does Android Manifest have permission to write to external storage.");
			}
		} else {
			Log.i(TAG, "Created directory " + path);
		}
	}

	private void copyTessDataFiles(String path) {
		try {
			String fileList[] = applicationContext.getAssets().list(path);

			for (String fileName : fileList) {
				String pathToDataFile = TESSERACT_PATH + path + "/" + fileName;
				if (!(new File(pathToDataFile)).exists()) {
					InputStream in = applicationContext.getAssets().open(path + "/" + fileName);
					OutputStream out = new FileOutputStream(pathToDataFile);
					byte[] buf = new byte[1024];
					int length;
					while ((length = in.read(buf)) > 0) {
						out.write(buf, 0, length);
					}
					in.close();
					out.close();
					Log.d(TAG, "Copied " + fileName + "to tessdata");
				}
			}
		} catch (IOException e) {
			Log.e(TAG, "Unable to copy files to tessdata " + e.getMessage());
		}
	}

	/**
	 * Set image for recognition.
	 *
	 * @param bitmap Image data.
	 */
	public void setBitmap(final Bitmap bitmap) {
		//tessBaseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK_VERT_TEXT);
		tessBaseApi.setImage(bitmap);
	}

	/**
	 * Get recognized text for image.
	 *
	 * @return Recognized text string.
	 */
	public String doOCR() {
		String text = tessBaseApi.getUTF8Text();
		Log.v(TAG, "OCRED TEXT: " + text);
		checkMRZ(text);
		return tessBaseApi.getUTF8Text();
	}

	public void checkMRZ(String txt){
		Matcher line1Matcher = line1Pattern.matcher(txt);
		Matcher line2Matcher = line2Pattern.matcher(txt);

		if (line1Matcher.find() && line2Matcher.find()) {
			final String mrzText = line1Matcher.group(0) + "\n" + line2Matcher.group(0);
			int count = scanResults.containsKey(mrzText) ? scanResults.get(mrzText) : 0;
			Log.e("Found MRZ Count " + (count + 1), mrzText);
			//If the same MRZ scanned more than once, than call the listener
			if(count > 0) {
				new Handler(Looper.getMainLooper()).post(new Runnable() {
					@Override
					public void run() {
						listener.onScanned(mrzText);
					}
				});
				return;
			}
			scanResults.put(mrzText, count + 1);
		}
	}

	/**
	 * Clear tesseract data.
	 */
	public void stop() {
		tessBaseApi.clear();
	}

	public interface OnMRZScanned{
		void onScanned(String mrzText);
	}
}
