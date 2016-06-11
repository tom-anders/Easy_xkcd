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
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.customtabs.CustomTabsIntent;
import android.support.design.widget.Snackbar;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseArray;
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

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.kogitune.activity_transition.ExitActivityTransition;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.Activities.SearchResultsActivity;
import de.tap.easy_xkcd.CustomTabHelpers.BrowserFallback;
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper;
import de.tap.easy_xkcd.database.DatabaseManager;
import de.tap.easy_xkcd.fragments.overview.OverviewListFragment;
import de.tap.easy_xkcd.misc.HackyViewPager;
import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.utils.ThemePrefs;
import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

/**
 * Superclass for ComicBrowserFragment, OfflineFragment & FavoritesFragment
 */

public abstract class ComicFragment extends android.support.v4.app.Fragment {
    public int lastComicNumber;
    public int newestComicNumber;
    public int favoriteIndex = 0;
    public SparseArray<Comic> comicMap = new SparseArray<>();

    protected HackyViewPager pager;
    protected ComicAdapter adapter;

    public static boolean fromSearch = false;
    static final String LAST_FAV = "last fav";
    static final String LAST_COMIC = "Last Comic";

    protected PrefHelper prefHelper;
    protected ThemePrefs themePrefs;
    protected DatabaseManager databaseManager;
    public ExitActivityTransition transition;

    protected View inflateLayout(int resId, LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(resId, container, false);
        setHasOptionsMenu(true);

        pager = (HackyViewPager) view.findViewById(R.id.pager);
        pager.setOffscreenPageLimit(2);

        prefHelper = ((MainActivity) getActivity()).getPrefHelper();
        themePrefs = ((MainActivity) getActivity()).getThemePrefs();
        databaseManager = ((MainActivity) getActivity()).getDatabaseManager();

        if (!(this instanceof FavoritesFragment)) {
            if (savedInstanceState != null) {
                lastComicNumber = savedInstanceState.getInt(LAST_COMIC);
            } else if (lastComicNumber == 0) {
                lastComicNumber = prefHelper.getLastComic();
            }
            if (MainActivity.overviewLaunch && !SearchResultsActivity.isOpen
                    ) {
                MainActivity.overviewLaunch = false;
                ((MainActivity) getActivity()).showOverview(false);
            }
        }

        if (((MainActivity) getActivity()).getCurrentFragment() == R.id.nav_browser && prefHelper.subtitleEnabled() && (this instanceof ComicBrowserFragment || this instanceof OfflineFragment))
            ((MainActivity) getActivity()).getToolbar().setSubtitle(String.valueOf(lastComicNumber));

        return view;
    }

    abstract protected class ComicAdapter extends PagerAdapter {
        protected Context mContext;
        protected LayoutInflater mLayoutInflater;
        protected Boolean fingerLifted = true;
        protected int count;

        public ComicAdapter(Context context, int count) {
            this.count = count;
            mContext = context;
            mLayoutInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        protected View setupPager(ViewGroup container, int position) {
            final View itemView = mLayoutInflater.inflate(R.layout.pager_item, container, false);
            itemView.setTag(position);
            final PhotoView pvComic = (PhotoView) itemView.findViewById(R.id.ivComic);
            final TextView tvAlt = (TextView) itemView.findViewById(R.id.tvAlt);

            if (!prefHelper.defaultZoom()) {
                pvComic.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                pvComic.setMaximumScale(10f);
            }

            if (prefHelper.altByDefault())
                tvAlt.setVisibility(View.VISIBLE);

            pvComic.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener() {
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (pvComic.getScale() < 0.5f * pvComic.getMaximumScale()) {
                        pvComic.setScale(0.5f * pvComic.getMaximumScale(), true);
                    } else if (pvComic.getScale() < pvComic.getMaximumScale()) {
                        pvComic.setScale(pvComic.getMaximumScale(), true);
                    } else {
                        pvComic.setScale(1.0f, true);
                    }
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (!prefHelper.altLongTap()) {
                        if (prefHelper.classicAltStyle()) {
                            toggleVisibility(tvAlt);
                        } else {
                            android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
                            mDialog.setMessage(tvAlt.getText());
                            mDialog.show();
                        }
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

            pvComic.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (fingerLifted && prefHelper.altLongTap()) {
                        if (prefHelper.altVibration())
                            ((Vibrator) getActivity().getSystemService(MainActivity.VIBRATOR_SERVICE)).vibrate(10);
                        setAltText(false);
                    }
                    return true;
                }
            });

            if (themePrefs.invertColors())
                pvComic.setColorFilter(themePrefs.getNegativeColorFilter());

            if (prefHelper.scrollDisabledWhileZoom() && prefHelper.defaultZoom())
                pvComic.setOnMatrixChangeListener(new PhotoViewAttacher.OnMatrixChangedListener() {
                    @Override
                    public void onMatrixChanged(RectF rectF) {
                        if (pvComic.getScale() > 1.4) {
                            pager.setLocked(true);
                        } else {
                            pager.setLocked(false);
                        }
                    }
                });

            return itemView;
        }

        protected int getGifId(int position) {
            switch (position + 1) {
                case 961:
                    return R.raw.eternal_flame;
                case 1116:
                    return R.raw.traffic_lights;
                case 1264:
                    return R.raw.slideshow;
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

    protected void animateToolbar() {
        Toolbar toolbar = ((MainActivity) getActivity()).getToolbar();
        if (toolbar.getAlpha() == 0) {
            toolbar.setTranslationY(-300);
            toolbar.animate().setDuration(300).translationY(0).alpha(1);
            View view;
            for (int i = 0; i < toolbar.getChildCount(); i++) {
                view = toolbar.getChildAt(i);
                view.setTranslationY(-300);
                view.animate().setStartDelay(50 * (i + 1)).setDuration(70 * (i + 1)).translationY(0);
            }
        }
    }

    protected void saveComic(int number, Bitmap bitmap) {
        try {
            File sdCard = prefHelper.getOfflinePath();
            File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File file = new File(dir, String.valueOf(number) + ".png");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            Log.e("Error", "Saving to external storage failed");
            try {
                FileOutputStream fos = getActivity().openFileOutput(String.valueOf(number), Context.MODE_PRIVATE);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
    }

    public class SaveComicImageTask extends AsyncTask<Boolean, Void, Void> {
        protected int mAddedNumber = lastComicNumber;
        private Bitmap mBitmap;
        private Comic mAddedComic;
        private boolean downloadImage;

        @Override
        protected Void doInBackground(Boolean... downloadImage) {
            this.downloadImage = downloadImage[0];
            if (this.downloadImage) {
                mAddedComic = comicMap.get(lastComicNumber);
                try {
                    String url = mAddedComic.getComicData()[2];
                    mBitmap = Glide
                            .with(getActivity())
                            .load(url)
                            .asBitmap()
                            .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                            .into(-1, -1)
                            .get();
                } catch (Exception e) {
                    databaseManager.setFavorite(mAddedNumber, false);
                    Log.e("Saving Image failed!", e.toString());
                }
                prefHelper.addTitle(mAddedComic.getComicData()[0], mAddedNumber);
                prefHelper.addAlt(mAddedComic.getComicData()[1], mAddedNumber);
            }

            databaseManager.setFavorite(mAddedNumber, true);
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            if (downloadImage) {
                saveComic(mAddedNumber, mBitmap);
            }
            //refresh the FavoritesFragment
            FavoritesFragment f = (FavoritesFragment) getActivity().getSupportFragmentManager().findFragmentByTag("favorites");
            if (f != null)
                f.refresh();
            //Sometimes the floating action button does not animate back to the bottom when the snackbar is dismissed, so force it to its original position
            ((MainActivity) getActivity()).getFab().forceLayout();
            getActivity().invalidateOptionsMenu();
        }
    }

    protected class DeleteComicImageTask extends AsyncTask<Boolean, Void, Void> {
        protected int mRemovedNumber = lastComicNumber;
        protected View.OnClickListener oc;

        @Override
        protected Void doInBackground(final Boolean... deleteImage) {
            if (deleteImage[0]) {
                //delete the image from internal storage
                getActivity().deleteFile(String.valueOf(mRemovedNumber));
                //delete from external storage
                File sdCard = prefHelper.getOfflinePath();
                File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                File file = new File(dir, String.valueOf(mRemovedNumber) + ".png");
                //noinspection ResultOfMethodCallIgnored
                file.delete();

                prefHelper.addTitle("", mRemovedNumber);
                prefHelper.addAlt("", mRemovedNumber);
            }
            oc = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new SaveComicImageTask().execute(deleteImage[0]);
                }
            };
            databaseManager.setFavorite(mRemovedNumber, false);
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
        CustomTabsIntent.Builder intentBuilder = new CustomTabsIntent.Builder();
        intentBuilder.setToolbarColor(themePrefs.getPrimaryColor(false));
        CustomTabActivityHelper.openCustomTab(getActivity(), intentBuilder.build(), Uri.parse(url), new BrowserFallback());
        return true;
    }

    protected boolean openComicInBrowser(int number) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://xkcd.com/" + String.valueOf(number)));
        startActivity(intent);
        return true;
    }

    protected boolean showTranscript(String trans) {
        android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
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

    protected void shareComicUrl(Comic comic) {
        //shares the comics url along with its title
        Intent share = new Intent(android.content.Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, comic.getComicData()[0]);
        share.putExtra(Intent.EXTRA_TEXT, " https://" + (prefHelper.shareMobile() ? "m." : "") + "xkcd.com/" + comic.getComicNumber() + "/");
        startActivity(Intent.createChooser(share, this.getResources().getString(R.string.share_url)));
    }

    protected void shareComicImage(Uri uri, Comic comic) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/*");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.putExtra(Intent.EXTRA_SUBJECT, comic.getComicData()[0]);

        String extraText = prefHelper.shareAlt() ? comic.getComicData()[1] : "";
        if (prefHelper.includeLink())
            extraText += " https://" + (prefHelper.shareMobile() ? "m." : "") + "xkcd.com/" + comic.getComicNumber() + "/";
        if (!extraText.equals(""))
            share.putExtra(Intent.EXTRA_TEXT, extraText);

        startActivity(Intent.createChooser(share, this.getResources().getString(R.string.share_image)));
    }

    protected Uri getURI(int number) {
        File sdCard = prefHelper.getOfflinePath();
        File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
        File path = new File(dir, String.valueOf(number) + ".png");
        return Uri.fromFile(path);
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
        if (prefHelper.showAltTip() && fromMenu) {
            Toast toast = Toast.makeText(getActivity(), R.string.action_alt_tip, Toast.LENGTH_LONG);
            toast.show();
            prefHelper.setAltTip(false);
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
            android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
            mDialog.setMessage(tvAlt.getText());
            mDialog.show();
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
        if (prefHelper.subtitleEnabled() && ((MainActivity) getActivity()).getCurrentFragment() == R.id.nav_browser)
            ((MainActivity) getActivity()).getToolbar().setSubtitle(String.valueOf(lastComicNumber));

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
                return showTranscript(comicMap.get(lastComicNumber).getTranscript());

            case R.id.action_boomark:
                return addBookmark(lastComicNumber);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        //Update the favorites icon
        MenuItem fav = menu.findItem(R.id.action_favorite);
        if (databaseManager.checkFavorite(lastComicNumber)) {
            fav.setIcon(R.drawable.ic_action_favorite);
            fav.setTitle(R.string.action_favorite_remove);
        } else {
            fav.setIcon(R.drawable.ic_favorite_outline);
            fav.setTitle(R.string.action_favorite);
        }
        //If the FAB is visible, hide the random comic menu item
        if (((MainActivity) getActivity()).getFab().getVisibility() == View.GONE) {
            menu.findItem(R.id.action_random).setVisible(true);
        } else {
            menu.findItem(R.id.action_random).setVisible(false);
        }
        menu.findItem(R.id.action_alt).setVisible(prefHelper.showAltTip());
        if (Arrays.binarySearch(getResources().getIntArray(R.array.interactive_comics), lastComicNumber) >= 0)
            menu.findItem(R.id.action_browser).setVisible(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!(this instanceof FavoritesFragment))
            inflater.inflate(R.menu.menu_comic_fragment, menu);
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

}
