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

package de.tap.easy_xkcd.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.database.DatabaseManager;

public class OfflineComic extends Comic {
    private Context mContext;
    private static final String OFFLINE_PATH = "/easy xkcd";
    private PrefHelper prefHelper;

    public OfflineComic(Integer number, Context context, PrefHelper prefHelper){
        this.prefHelper = prefHelper;
        mContext = context;
        comicNumber = number;
    }

    public String[] getComicData() {
        String[] result = new String[2];
        result[0] = prefHelper.getTitle(comicNumber);
        result[1] = prefHelper.getAlt(comicNumber);
        return result;
    }

    public Bitmap getBitmap() {
        Bitmap mBitmap = null;
        try {
            File sdCard = prefHelper.getOfflinePath();
            File dir = new File(sdCard.getAbsolutePath() + OFFLINE_PATH);
            File file = new File(dir, String.valueOf(comicNumber) + ".png");
            FileInputStream fis = new FileInputStream(file);
            mBitmap = BitmapFactory.decodeStream(fis);
            fis.close();
        } catch (IOException e) {
            Log.e("Error", "Image not found, looking in internal storage");
            try {
                FileInputStream fis = mContext.openFileInput(String.valueOf(comicNumber));
                mBitmap = BitmapFactory.decodeStream(fis);
                fis.close();
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
        return mBitmap;
    }

    public String getTranscript() {
        return DatabaseManager.getTranscript(comicNumber, mContext);
    }

}

