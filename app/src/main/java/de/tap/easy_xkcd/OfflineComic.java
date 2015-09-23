/**********************************************************************************
 * Copyright 2015 Tom Praschan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ********************************************************************************/

package de.tap.easy_xkcd;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import de.tap.easy_xkcd.MainActivity;

public class OfflineComic {
    //private String[] mComicData;
    private int mComicNumber;
    private Context mContext;

    public OfflineComic(Integer number, Context context){
        mContext = context;
        mComicNumber = number;
        //SharedPreferences preferences = ((MainActivity) context).getPreferences(Activity.MODE_PRIVATE);
        //mComicData = new String[2];
        //mComicData[0] = preferences.getString("title"+String.valueOf(number),"");
        //mComicData[1] = preferences.getString("alt"+String.valueOf(number),"");
    }

    public String[] getComicData() {
        String[] result = new String[2];
        result[0] = PrefHelper.getTitle(mComicNumber);
        result[1] = PrefHelper.getAlt(mComicNumber);
        return result;
    }

    public Bitmap getBitmap() {
        Bitmap mBitmap = null;
        try {
            File sdCard = Environment.getExternalStorageDirectory();
            File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
            File file = new File(dir, String.valueOf(mComicNumber) + ".png");
            FileInputStream fis = new FileInputStream(file);
            mBitmap = BitmapFactory.decodeStream(fis);
            fis.close();
        } catch (IOException e) {
            Log.e("Error", "Image not found, looking in internal storage");
            try {
                FileInputStream fis = mContext.openFileInput(String.valueOf(mComicNumber));
                mBitmap = BitmapFactory.decodeStream(fis);
                fis.close();
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
        return mBitmap;
    }
    public int getComicNumber() { return mComicNumber;}

}

