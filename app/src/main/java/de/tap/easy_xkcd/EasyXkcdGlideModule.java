package de.tap.easy_xkcd;

import android.content.Context;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import androidx.annotation.NonNull;

@GlideModule
public class EasyXkcdGlideModule extends AppGlideModule {
    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder.setDefaultRequestOptions(new RequestOptions().override(Target.SIZE_ORIGINAL));
        super.applyOptions(context, builder);
    }
}
