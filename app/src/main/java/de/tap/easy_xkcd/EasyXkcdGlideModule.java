package de.tap.easy_xkcd;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Paint;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.TransformationUtils;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import androidx.annotation.NonNull;

import java.security.MessageDigest;

import timber.log.Timber;

@GlideModule
public class EasyXkcdGlideModule extends AppGlideModule {
    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        // Sometimes, Randall accidentally posts an insanely high resolution version of the latest comic.
        // This can lead to crashes since by default where not scaling down the image at all. For
        // "normal" images this is fine and looks great, but for those huge images it will lead to a
        // crash. So limit the size to at most 4000 pixels width and height.
        builder.setDefaultRequestOptions(new RequestOptions().override(Target.SIZE_ORIGINAL).transform(new BigBitmapTransformation(4000)));
        super.applyOptions(context, builder);
    }

    // Adapted from https://github.com/bumptech/glide/issues/2391
    static private class BigBitmapTransformation extends BitmapTransformation {
        private static final String ID = "BigBitmapTransformation";
        private static final byte[] ID_BYTES = ID.getBytes(CHARSET);

        final private int maxSize;

        public BigBitmapTransformation(int maxSize) {
            this.maxSize = maxSize;
        }

        @Override
        protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
            if (toTransform.getHeight() <= maxSize && toTransform.getWidth() <= maxSize) {
                return toTransform;
            }
            float scale = Math.min(((float)maxSize / toTransform.getWidth()), ((float)maxSize / toTransform.getHeight()));
            return TransformationUtils.centerCrop(pool, toTransform,
                    (int) (toTransform.getWidth() * scale), (int) (toTransform.getHeight() * scale));
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof BigBitmapTransformation;
        }

        @Override
        public int hashCode() {
            return ID.hashCode();
        }

        @Override
        public void updateDiskCacheKey(MessageDigest messageDigest) {
            messageDigest.update(ID_BYTES);
        }
    }
}

