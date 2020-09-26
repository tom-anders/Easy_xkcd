/**
 * *******************************************************************************
 * Copyright 2016 Tom Praschan
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ******************************************************************************
 */

package de.tap.easy_xkcd.fragments.comics;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.os.Vibrator;

import androidx.browser.customtabs.CustomTabsIntent;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.content.FileProvider;
import androidx.core.view.MenuCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.widget.Toolbar;

import android.text.Html;
import android.transition.TransitionInflater;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.tap.xkcd_reader.R;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.Activities.SearchResultsActivity;
import de.tap.easy_xkcd.CustomTabHelpers.BrowserFallback;
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper;
import de.tap.easy_xkcd.GlideApp;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.database.RealmComic;
import de.tap.easy_xkcd.fragments.ImmersiveDialogFragment;
import de.tap.easy_xkcd.fragments.overview.OverviewListFragment;
import de.tap.easy_xkcd.misc.HackyViewPager;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;
import timber.log.Timber;

/**
 * Superclass for ComicBrowserFragment, OfflineFragment & FavoritesFragment
 */

public abstract class ComicFragment extends Fragment {
    public int lastComicNumber;
    public int newestComicNumber;
    public int favoriteIndex = 0;

    protected HackyViewPager pager;
    protected ComicAdapter adapter;

    public static boolean newComicFound = false;
    static final String LAST_FAV = "last fav";
    public static final String LAST_COMIC = "Last Comic";

    public boolean transitionPending = false;

    protected PrefHelper prefHelper;
    protected ThemePrefs themePrefs;
    protected DatabaseManager databaseManager;

    protected View inflateLayout(int resId, LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(resId, container, false);
        setHasOptionsMenu(true);

        pager = (HackyViewPager) view.findViewById(R.id.pager);
        pager.setOffscreenPageLimit(2);

        prefHelper = getMainActivity().getPrefHelper();
        themePrefs = getMainActivity().getThemePrefs();
        databaseManager = getMainActivity().getDatabaseManager();

        if (!(this instanceof FavoritesFragment)) {
            if (savedInstanceState != null) {
                lastComicNumber = savedInstanceState.getInt(LAST_COMIC);
            } else if (lastComicNumber == 0) {
                lastComicNumber = prefHelper.getLastComic();
            }
            if (MainActivity.overviewLaunch && !SearchResultsActivity.isOpen
                    ) {
                MainActivity.overviewLaunch = false;
                getMainActivity().showOverview(false);
            }
        }

        if (savedInstanceState == null && transitionPending) {
            postponeEnterTransition();
            Timber.d("posponing transition...");
            setSharedElementEnterTransition(TransitionInflater.from(getContext()).inflateTransition(R.transition.image_shared_element_transition));
        }

        if (getMainActivity().getCurrentFragment() == MainActivity.CurrentFragment.Browser && (this instanceof ComicBrowserFragment || this instanceof OfflineFragment))
            getMainActivity().getToolbar().setSubtitle(prefHelper.subtitleEnabled() ? String.valueOf(lastComicNumber) : "");

        return view;
    }

    abstract protected boolean modifyFavorites(MenuItem item);

    abstract protected class ComicAdapter extends PagerAdapter {  //TODO ise FragmentPaderAdapter instead
        protected Context context;
        protected LayoutInflater mLayoutInflater;
        protected Boolean fingerLifted = true;
        protected int count;

        abstract RealmComic getRealmComic(int position);

        abstract void loadComicImage(RealmComic comic, PhotoView pvComic);

        boolean loadGif(int number, PhotoView pvComic) {
            if (getGifId(number) != 0) {
                Timber.d("loading gif %d", number);
                GlideApp.with(ComicFragment.this)
                        .load(getGifId(number))
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                postImageLoaded(number);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                postImageLoaded(number);
                                return false;
                            }
                        })
                        .into(pvComic);
                return true;
            }
            return false;
        }

        public ComicAdapter(Context context, int count) {
            this.count = count;
            this.context = context;
            mLayoutInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        protected CircularProgressDrawable getProgressCircle() {
            CircularProgressDrawable circularProgressDrawable = new CircularProgressDrawable(getActivity());
            circularProgressDrawable.setStrokeWidth(5.0f);
            circularProgressDrawable.setCenterRadius(100.0f);
            circularProgressDrawable.setColorSchemeColors(themePrefs.nightThemeEnabled() ? themePrefs.getAccentColorNight() : themePrefs.getAccentColor());
            circularProgressDrawable.start();
            return circularProgressDrawable;
        }

        @Override
        public Object instantiateItem(final ViewGroup container, final int position) {
            View itemView = setupPager(container, position);
            final PhotoView pvComic = itemView.findViewById(R.id.ivComic);
            final TextView tvAlt = itemView.findViewById(R.id.tvAlt);
            final TextView tvTitle = itemView.findViewById(R.id.tvTitle);

            RealmComic comic = getRealmComic(position); //TODO check if comic is null

            try {
                tvAlt.setText(Html.fromHtml(Html.escapeHtml(comic.getAltText())));
                tvTitle.setText((prefHelper.subtitleEnabled() ? "" : comic.getComicNumber() + ": ") + Html.fromHtml(RealmComic.getInteractiveTitle(comic, getActivity())));
                pvComic.setTransitionName("im" + comic.getComicNumber());
                tvTitle.setTransitionName(String.valueOf(comic.getComicNumber()));

                loadComicImage(comic, pvComic);
            } catch (NullPointerException e) {
                Timber.e(e, "NullPointerException at %s", position);
            }


            container.addView(itemView);
            return itemView;
        }

        protected void postImageLoadedSetupPhotoView(PhotoView pvComic, Bitmap bitmap, RealmComic comic) {
            if (themePrefs.invertColors(false) && themePrefs.bitmapContainsColor(bitmap, comic.getComicNumber()))
                pvComic.clearColorFilter();

            if (!transitionPending) {
                pvComic.setAlpha(0f);
                pvComic.animate()
                        .alpha(1f)
                        .setDuration(200);
            }
        }

        protected void postImageLoaded(int number) {
            if (number == lastComicNumber) {
                animateToolbar();
                if (transitionPending) {
                    Timber.d("start transition at %d", number);
                    startPostponedEnterTransition();
                    transitionPending = false;
                }
                MainActivity mainActivity = (MainActivity) getActivity();
                if (MainActivity.fromSearch) {
                    Timber.d("start transition at %d", number);
                    mainActivity.startPostponedEnterTransition();
                    MainActivity.fromSearch = false;
                }

                if (mainActivity.getProgressDialog() != null) {
                    mainActivity.getProgressDialog().dismiss();
                }
            }
        }

        protected View setupPager(ViewGroup container, int position) {
            final View itemView = mLayoutInflater.inflate(R.layout.pager_item, container, false);
            itemView.setTag(position);
            final PhotoView pvComic = itemView.findViewById(R.id.ivComic);
            final TextView tvAlt = itemView.findViewById(R.id.tvAlt);
            final TextView tvTitle = itemView.findViewById(R.id.tvTitle);

            //If the FAB is disabled, remove the right margin of the alt text
            if ((ComicFragment.this instanceof FavoritesFragment && prefHelper.fabDisabledFavorites()) || prefHelper.fabDisabledComicBrowser()) {
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) tvAlt.getLayoutParams();
                params.rightMargin = getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
            }
            //If the FAB is left, swap the margins
            if (prefHelper.fabLeft()) {
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) tvAlt.getLayoutParams();
                params.leftMargin = getResources().getDimensionPixelSize(R.dimen.text_alt_margin_right);
                params.rightMargin = getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
            }

            if (Arrays.binarySearch(context.getResources().getIntArray(R.array.large_comics), position + 1) >= 0)
                pvComic.setMaximumScale(15.0f);

            if (themePrefs.nightThemeEnabled()) {
                tvAlt.setTextColor(Color.WHITE);
                tvTitle.setTextColor(Color.WHITE);
            }

            if (!prefHelper.defaultZoom()) {
                pvComic.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                pvComic.setMaximumScale(10f);
            }

            if (prefHelper.altByDefault())
                tvAlt.setVisibility(View.VISIBLE);

            pvComic.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (prefHelper.doubleTapToFavorite()) {
                        modifyFavorites(getMainActivity().getToolbar().getMenu().findItem(R.id.action_favorite));
                        if (getActivity().getSystemService(Context.VIBRATOR_SERVICE) != null) {
                            ((Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE)).vibrate(100);
                        }
                    } else {
                        if (pvComic.getScale() < 0.5f * pvComic.getMaximumScale()) {
                            pvComic.setScale(0.5f * pvComic.getMaximumScale(), true);
                        } else if (pvComic.getScale() < pvComic.getMaximumScale()) {
                            pvComic.setScale(pvComic.getMaximumScale(), true);
                        } else {
                            pvComic.setScale(1.0f, true);
                        }
                    }
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    MainActivity mainActivity = getMainActivity();
                    if (mainActivity != null) {
                        if (prefHelper.altLongTap()) {
                            if (RealmComic.isInteractiveComic(position + 1, getActivity())) {
                                openComicInBrowser(position + 1);
                            } else if (prefHelper.fullscreenModeEnabled()) {
                                mainActivity.toggleFullscreen();
                            }
                        } else {
                            if (prefHelper.altVibration())
                                if (getActivity().getSystemService(Context.VIBRATOR_SERVICE) != null) {
                                    ((Vibrator) mainActivity.getSystemService(Context.VIBRATOR_SERVICE)).vibrate(25);
                                }
                            setAltText(false);
                        }
                    } else {
                        Timber.e("Main Activity is null!");
                    }
                    return false;
                }

                @Override
                public boolean onDoubleTapEvent(MotionEvent e) {
                    if (e.getAction() == MotionEvent.ACTION_UP)
                        fingerLifted = true;
                    if (e.getAction() == MotionEvent.ACTION_DOWN)
                        fingerLifted = false;
                    return false;
                }
            });

            pvComic.setOnLongClickListener(v -> {
                if (fingerLifted) {
                    if (prefHelper.altLongTap()) {
                        if (prefHelper.altVibration())
                            if (getActivity().getSystemService(Context.VIBRATOR_SERVICE) != null) {
                                ((Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE)).vibrate(25);
                            }
                        setAltText(false);
                    } else {
                        if (RealmComic.isInteractiveComic(position + 1, getActivity())) {
                            openComicInBrowser(position + 1);
                        } else if (prefHelper.fullscreenModeEnabled()) {
                            getMainActivity().toggleFullscreen();
                        }
                    }
                }
                return true;
            });

            if (themePrefs.invertColors(false))
                pvComic.setColorFilter(themePrefs.getNegativeColorFilter());

            if (prefHelper.scrollDisabledWhileZoom() && prefHelper.defaultZoom())
                pvComic.setOnMatrixChangeListener(rectF -> {
                    if (pvComic.getScale() > 1.4) {
                        pager.setLocked(true);
                    } else {
                        pager.setLocked(false);
                    }
                });

            return itemView;
        }

        protected int getGifId(int number) {
            switch (number) {
                case 961:
                    return R.raw.eternal_flame;
                case 1116:
                    return R.raw.traffic_lights;
                case 1264:
                    return R.raw.slideshow;
                case 2293:
                    return R.raw.rip_john_conway;
                default:
                    return 0;
            }
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((RelativeLayout) object);
        }
    }

    public TextView getCurrentTitleTextView() {
        return pager.findViewWithTag(lastComicNumber - 1).findViewById(R.id.tvTitle);
    }

    public PhotoView getCurrentPhotoView() {
        return pager.findViewWithTag(lastComicNumber - 1).findViewById(R.id.ivComic);
    }

    protected void animateToolbar() {
        Toolbar toolbar = getMainActivity().getToolbar();
        if (toolbar.getAlpha() == 0) {
            toolbar.setTranslationY(-300);
            toolbar.animate().setDuration(380).translationY(0).alpha(1);
            View view;
            for (int i = 0; i < toolbar.getChildCount(); i++) {
                view = toolbar.getChildAt(i);
                view.setTranslationY(-300);
                view.animate().setStartDelay(50 * (i + 1)).setDuration(70 * (i + 1)).translationY(0);
            }
        }
    }

    public void addFavorite(final int addedNumber, boolean downloadImage) {
        //DatabaseManager databaseManager = new DatabaseManager(getActivity()); //Create a new one here since we're in a background thread
        if (downloadImage) {
            try {
                GlideApp.with(this)
                        .asBitmap()
                        .load(databaseManager.getRealmComic(addedNumber).getUrl())
                        .listener(new RequestListener<Bitmap>() {
                            @Override
                            public boolean onLoadFailed(@androidx.annotation.Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                                Timber.e("Sharing failed!");
                                return true;
                            }

                            @Override
                            public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                                RealmComic.saveOfflineBitmap(resource, prefHelper, addedNumber, getActivity());
                                Timber.d("Save successful!");
                                return true;
                            }
                        }).submit();
            } catch (Exception e) {
                databaseManager.setFavorite(addedNumber, false);
                Timber.d("Saving Image failed for Comic %d!", addedNumber);
            }
        }

        databaseManager.setFavorite(addedNumber, true);
        //Sometimes the floating action button does not animate back to the bottom when the snackbar is dismissed, so force it to its original position
        getMainActivity().getFab().forceLayout();
        getActivity().invalidateOptionsMenu();
    }

    /*protected class DeleteComicImageTask extends AsyncTask<Boolean, Void, Void> {
        private int removedNumber;
        private View.OnClickListener oc;

        public DeleteComicImageTask(int removedNumber) {
            this.removedNumber = removedNumber;
        }

        @Override
        protected Void doInBackground(final Boolean... deleteImage) {
            if (deleteImage[0]) {
                //delete the image from internal storage
                getActivity().deleteFile(String.valueOf(removedNumber));
                //delete from external storage
                File sdCard = prefHelper.getOfflinePath();
                File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                File file = new File(dir, String.valueOf(removedNumber) + ".png");
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            oc = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //new SaveComicImageTask(removedNumber).execute(deleteImage[0]);
                    addFavorite(removedNumber, deleteImage[0]);
                }
            };
            (new DatabaseManager(getActivity())).setFavorite(removedNumber, false);
            Snackbar.make(((MainActivity) getActivity()).getFab(), R.string.snackbar_remove, Snackbar.LENGTH_LONG)
                    .setAction(R.string.snackbar_undo, oc)
                    .show();
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            //refresh the favorites fragment
            FavoritesFragment f = (FavoritesFragment) getActivity().getSupportFragmentManager().findFragmentByTag("favorites");
            if (f != null)
                f.refresh();
        }
    }*/

    protected void removeFavorite(final int number, final boolean deleteImage) {
        if (deleteImage) {
            if (!getActivity().deleteFile(number + ".png")) {
                Timber.d("trying to delete from external storage");
                File sdCard = prefHelper.getOfflinePath();
                File file = new File(sdCard.getAbsolutePath() + "/easy xkcd/" + number + ".png");
                boolean deleted = file.delete();
                Timber.d("deleted: %s", deleted);
            }
        }
        databaseManager.setFavorite(number, false);
        Snackbar.make(getMainActivity().getFab(), R.string.snackbar_remove, Snackbar.LENGTH_LONG)
                .setAction(R.string.snackbar_undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        addFavorite(number, deleteImage);
                    }
                })
                .show();
    }

    public boolean zoomReset() {
        int index;
        if (this instanceof FavoritesFragment)
            index = favoriteIndex;
        else
            index = lastComicNumber - 1;

        if (prefHelper.altBackButton() && !(pager.findViewWithTag(index).findViewById(R.id.tvAlt).getVisibility() == View.VISIBLE))
            return setAltText(false);

        try {
            PhotoView pv = (PhotoView) pager.findViewWithTag(index).findViewById(R.id.ivComic);
            float scale = pv.getScale();
            if (scale != 1f) {
                pv.setScale(1f, true);
                return true;
            } else {
                return false;
            }
        } catch (NullPointerException e) {
            Log.e("error", "pv nullPointer");
            return false;
        }
    }

    protected boolean explainComic(int number) {
        String url = "https://explainxkcd.com/" + String.valueOf(number);
        if (prefHelper.useCustomTabs()) {
            CustomTabsIntent.Builder intentBuilder = new CustomTabsIntent.Builder();
            intentBuilder.setToolbarColor(themePrefs.getPrimaryColor(false));
            CustomTabActivityHelper.openCustomTab(getActivity(), intentBuilder.build(), Uri.parse(url), new BrowserFallback());
        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        }
        return true;
    }

    protected boolean openComicInBrowser(int number) {
        // We open the mobile site (m.xkcd.com) by default
        // For interactive comics we use the desktop since it has better support for some interactive comics
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://" + (RealmComic.isInteractiveComic(number, getActivity()) ? "" : "m.") + "xkcd.com/" + number));

        // Since the app also handles xkcd intents, we need to exxlude it from the intent chooser
        // Code adapted from https://codedogg.wordpress.com/2018/11/09/how-to-exclude-your-own-activity-from-activity-startactivityintent-chooser/
        PackageManager packageManager = getActivity().getPackageManager();
        List<Intent> possibleIntents = new ArrayList<>();

        Set<String> possiblePackageNames = new HashSet<>();
        for (ResolveInfo resolveInfo : packageManager.queryIntentActivities(intent, 0)) {

            String packageName = resolveInfo.activityInfo.packageName;
            if (!packageName.equals(getActivity().getPackageName())) {

                Intent possibleIntent = new Intent(intent);
                possibleIntent.setPackage(resolveInfo.activityInfo.packageName);
                possiblePackageNames.add(resolveInfo.activityInfo.packageName);

                possibleIntents.add(possibleIntent);
            }
        }

        @Nullable ResolveInfo defaultResolveInfo = packageManager.resolveActivity(intent, 0);

        if (defaultResolveInfo == null || possiblePackageNames.isEmpty()) {
            Timber.e("No browser found!");
            return false;
        }

        // If there is a default app to handle the intent (which is not this app), use it.
        if (possiblePackageNames.contains(defaultResolveInfo.activityInfo.packageName)) {
            getActivity().startActivity(intent);
        } else { // Otherwise, let the user choose.
            Intent intentChooser = Intent.createChooser(possibleIntents.remove(0), getActivity().getResources().getString(R.string.chooser_title));
            intentChooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, possibleIntents.toArray(new Parcelable[]{}));
            getActivity().startActivity(intentChooser);
        }

        return true;
    }

    protected boolean showTranscript(String trans, int number) {
        androidx.appcompat.app.AlertDialog.Builder mDialog = new androidx.appcompat.app.AlertDialog.Builder(getActivity());
        if (number >= 1675) {
            trans = getResources().getString(R.string.no_transcript);
        }
        mDialog.setMessage(trans);
        mDialog.show();
        return true;
    }

    protected boolean addBookmark(int bookmark) {
        if (prefHelper.getBookmark() == 0)
            Toast.makeText(getActivity(), R.string.bookmark_toast, Toast.LENGTH_LONG).show();
        prefHelper.setBookmark(bookmark);
        OverviewListFragment.bookmark = bookmark;
        return true;
    }

    protected void shareComicUrl(RealmComic comic) {
       //shares the comics url along with its title
        Intent share = new Intent(android.content.Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, comic.getTitle());
        share.putExtra(Intent.EXTRA_TEXT, " https://" + (prefHelper.shareMobile() ? "m." : "") + "xkcd.com/" + comic.getComicNumber() + "/");
        startActivity(Intent.createChooser(share, this.getResources().getString(R.string.share_url)));
    }

    protected void shareComicImage(@Nullable Uri uri, RealmComic comic) {
        if (uri == null) {
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/*");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.putExtra(Intent.EXTRA_SUBJECT, comic.getTitle());

        String extraText = comic.getTitle();
        if (prefHelper.shareAlt()) {
            extraText += "\n" + comic.getAltText();
        }
        if (prefHelper.includeLink()) {
            extraText += "\n" + "https://" + (prefHelper.shareMobile() ? "m." : "")
                    + "xkcd.com/" + comic.getComicNumber() + "/";
        }
        share.putExtra(Intent.EXTRA_TEXT, extraText);

        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(share, this.getResources().getString(R.string.share_image)));
    }

    protected Uri getURI(int number) {
        try {
            File sdCard = prefHelper.getOfflinePath();
            File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
            File path = new File(dir, String.valueOf(number) + ".png");
            return FileProvider.getUriForFile(getActivity(), "de.tap.easy_xkcd.fileProvider", path);
        } catch (IllegalArgumentException e) {
            Timber.e( "Image not found, looking in internal storage");

            try {
                String cachePath = Environment.getExternalStorageDirectory() + "/easy xkcd";
                File dir = new File(cachePath);
                dir.mkdirs();
                File file = new File(dir, String.valueOf(lastComicNumber) + ".png");
                FileOutputStream stream = new FileOutputStream(file);

                FileInputStream fis = getActivity().openFileInput(String.valueOf(number));
                Bitmap resource = BitmapFactory.decodeStream(fis);
                fis.close();

                resource.compress(Bitmap.CompressFormat.PNG, 100, stream);
                stream.close();
                Uri uri = FileProvider.getUriForFile(getActivity(), "de.tap.easy_xkcd.fileProvider", file);
                return uri;
            } catch (Exception e2) {
                Toast.makeText(getActivity(), getResources().getString(R.string.comic_error), Toast.LENGTH_SHORT).show();
                return null;
            }
        }
    }

    public void getPreviousRandom() {
        lastComicNumber = prefHelper.getPreviousRandom(lastComicNumber);
        pager.setCurrentItem(lastComicNumber - 1, false);
    }

    public boolean getRandomComic() {
        lastComicNumber = prefHelper.getRandomNumber(lastComicNumber);
        pager.setCurrentItem(lastComicNumber - 1, false);
        return true;
    }

    protected boolean getLatestComic() {
        lastComicNumber = newestComicNumber;
        pager.setCurrentItem(lastComicNumber - 1, false);
        return true;
    }

    protected void scrollViewPager() {
        if (lastComicNumber != 0) {
            try {
                Field field = ViewPager.class.getDeclaredField("mRestoredCurItem");
                field.setAccessible(true);
                field.set(pager, lastComicNumber - 1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    abstract public void updatePager();

    protected boolean setAltText(boolean fromMenu) {
        //If the user selected the menu item for the first time, show the toast
        if (fromMenu && prefHelper.showAltTip()) {
            Snackbar snackbar = Snackbar.make(requireActivity().findViewById(android.R.id.content), R.string.action_alt_tip, BaseTransientBottomBar.LENGTH_LONG);
            snackbar.setAction(R.string.got_it, view -> prefHelper.setShowAltTip(false));
            snackbar.show();
        }
        //Show alt text
        int index;
        if (this instanceof FavoritesFragment)
            index = favoriteIndex;
        else
            index = lastComicNumber - 1;
        TextView tvAlt = (TextView) pager.findViewWithTag(index).findViewById(R.id.tvAlt);
        if (prefHelper.classicAltStyle()) {
            toggleVisibility(tvAlt);
        } else {
            //androidx.appcompat.app.AlertDialog.Builder dialog = new androidx.appcompat.app.AlertDialog.Builder(getActivity());
            //dialog.setMessage(tvAlt.getText());
            //dialog.show();
            //ImmersiveDialogFragment.getInstance(String.valueOf(tvAlt.getText())).showImmersive(((MainActivity) getActivity()));
            ImmersiveDialogFragment.getInstance(String.valueOf(tvAlt.getText())).showImmersive(getMainActivity());
        }
        return true;
    }

    protected void toggleVisibility(View view) {
        // Switches a view's visibility between GONE and VISIBLE
        if (view.getVisibility() == View.GONE) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    protected void pageSelected(int position) {
        try {
            getActivity().invalidateOptionsMenu();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        databaseManager.setRead(position + 1, true);
        lastComicNumber = position + 1;
        getMainActivity().lastComicNumber = lastComicNumber;
        if (getMainActivity().getCurrentFragment() == MainActivity.CurrentFragment.Browser)
            getMainActivity().getToolbar().setSubtitle(prefHelper.subtitleEnabled() ? String.valueOf(lastComicNumber) : "");

        animateToolbar();
    }

    @Override
    public void onStop() {
        if (!(this instanceof FavoritesFragment))
            prefHelper.setLastComic(lastComicNumber);
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_alt:
                return setAltText(true);

            case R.id.action_explain:
                return explainComic(lastComicNumber);

            case R.id.action_latest:
                return getLatestComic();

            case R.id.action_browser:
                return openComicInBrowser(lastComicNumber);

            case R.id.action_trans:
                return showTranscript(databaseManager.getRealmComic(lastComicNumber).getTranscript(), lastComicNumber);

            case R.id.action_boomark:
                return addBookmark(lastComicNumber);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        //Update the favorites icon
        MenuItem fav = menu.findItem(R.id.action_favorite);
        if (databaseManager.isFavorite(lastComicNumber)) {
            fav.setIcon(R.drawable.ic_favorite_on_24dp);
            fav.setTitle(R.string.action_favorite_remove);
        } else {
            fav.setIcon(R.drawable.ic_favorite_off_24dp);
            fav.setTitle(R.string.action_favorite);
        }
        //If the FAB is visible, hide the random comic menu item
        if (getActivity() != null) {
            FloatingActionButton fab = getMainActivity().getFab();
            menu.findItem(R.id.action_random).setVisible(fab != null && fab.getVisibility() == View.GONE);
        }
        menu.findItem(R.id.action_alt).setVisible(prefHelper.showAltTip());
        menu.findItem(R.id.action_browser).setVisible(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!(this instanceof FavoritesFragment))
            inflater.inflate(R.menu.menu_comic_fragment, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putInt(LAST_COMIC, lastComicNumber);
        savedInstanceState.putInt(LAST_FAV, favoriteIndex);
        super.onSaveInstanceState(savedInstanceState);
    }

    public void scrollTo(int pos, boolean smooth) {
        pager.setCurrentItem(pos, smooth);
    }

    public MainActivity getMainActivity() {
        //if getActivity is not null, it will always be an instance of MainActivity, so the cast is safe
        if (getActivity() != null) {
            return (MainActivity) getActivity();
        }
        return null;
    }

}
