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
import com.hellsayenci.mrzscanner.mrz.MrzParser;
import com.hellsayenci.mrzscanner.mrz.MrzRecord;
import com.hellsayenci.mrzscanner.mrz.types.MrzFormat;

/**
 * Created by jsjem on 17.11.2016.
 */
public class TextRecognitionHelper {

	private static final String TAG = "TextRecognitionHelper";

	private static final String TESSERACT_TRAINED_DATA_FOLDER = "tessdata";
	private static String TESSERACT_PATH = null;

	private final Context applicationContext;
	private final TessBaseAPI tessBaseApi;

	Pattern passportLine1Pattern = Pattern.compile("[A-Z0-9<]{2}[A-Z<]{3}[A-Z0-9<]{39}");
	Pattern passportLine2Pattern = Pattern.compile("[A-Z0-9<]{9}[0-9]{1}[A-Z<]{3}[0-9]{6}[0-9]{1}[FM<]{1}[0-9]{6}[0-9]{1}[A-Z0-9<]{14}[0-9]{1}[0-9]{1}");

	private List<MrzFormat> mrzFormats = new ArrayList<>();

	private static List<MrzFormat> supportedFormats = new ArrayList<>();

	private OnMRZScanned listener;

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

		mrzFormats.add(MrzFormat.PASSPORT);
		mrzFormats.add(MrzFormat.MRTD_TD2);
		mrzFormats.add(MrzFormat.SLOVAK_ID_234);
		mrzFormats.add(MrzFormat.MRTD_TD1);

		supportedFormats.add(MrzFormat.PASSPORT);
		supportedFormats.add(MrzFormat.SLOVAK_ID_234);
		supportedFormats.add(MrzFormat.MRTD_TD2);
		supportedFormats.add(MrzFormat.FRENCH_ID);
		supportedFormats.add(MrzFormat.MRTD_TD1);
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
	public void doOCR() {
		String text = tessBaseApi.getUTF8Text();
		Log.v(TAG, "OCRED TEXT: " + text);
		checkMRZ(text);
	}

	public void checkMRZ(String txt){
		final String mrzText = preProcessText(txt);

		if(mrzText != null) {
			Log.i("Found possible MRZ", mrzText);
			try {
				MrzRecord mrzRecord = MrzParser.parse(mrzText);
				if(mrzRecord != null) {
					if(supportedFormats.contains(mrzRecord.format)) {
						boolean additionalPassportCheckOK = true;
						if(mrzRecord.format == MrzFormat.PASSPORT){
							if(!passportLine1Pattern.matcher(mrzText).find()
							|| !passportLine2Pattern.matcher(mrzText).find())
								additionalPassportCheckOK = false;
						}

						if(additionalPassportCheckOK) {
							new Handler(Looper.getMainLooper()).post(new Runnable() {
								@Override
								public void run() {
									listener.onScanned(mrzText);
								}
							});
							return;
						}
					}
				}
			} catch (Exception e){
				Log.i("MRZ Parser", "Failed");
			}
		}
	}

	private String preProcessText(String txt) {
		String[] lines = txt.split("\n");
		if(lines == null || lines.length < 1)
			return null;
		for(MrzFormat mrzFormat : mrzFormats) {
			for (int i = lines.length - 1; i >= 0; i--) {
				String line2 = lines[i].replace(" ", "");
				if(line2.length() >= mrzFormat.columns){
					if(i == 0)
						break;
					String line1 = lines[i - 1].replace(" ", "");
					if(line1.length() >= mrzFormat.columns)
						if(mrzFormat.rows == 2)
							return line1.substring(0, mrzFormat.columns) + "\n" +
									line2.substring(0, mrzFormat.columns);
						else if(mrzFormat.rows == 3){
							if(lines.length < 2 || i < 1)
								break;
							String line0 = lines[i - 2].replace(" ", "");
							if(line0.length() >= mrzFormat.columns)
								return line0.substring(0, mrzFormat.columns) + "\n" +
										line1.substring(0, mrzFormat.columns) + "\n" +
										line2.substring(0, mrzFormat.columns);
							else
								break;
						}
					else
						break;
				}
			}
		}
		return null;
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
