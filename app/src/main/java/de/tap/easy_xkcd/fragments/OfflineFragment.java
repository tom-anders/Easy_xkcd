package de.tap.easy_xkcd.fragments;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.support.customtabs.CustomTabsIntent;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.kogitune.activity_transition.ActivityTransition;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.CustomTabHelpers.BrowserFallback;
import de.tap.easy_xkcd.CustomTabHelpers.CustomTabActivityHelper;
import de.tap.easy_xkcd.utils.Favorites;
import de.tap.easy_xkcd.misc.HackyViewPager;
import de.tap.easy_xkcd.utils.OfflineComic;
import de.tap.easy_xkcd.utils.PrefHelper;
import de.tap.easy_xkcd.Activities.MainActivity;
import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

public class OfflineFragment extends android.support.v4.app.Fragment {
    public static int sLastComicNumber = 0;
    public static int sNewestComicNumber = 0;
    public static SparseArray<OfflineComic> sComicMap = new SparseArray<>();
    public static HackyViewPager sPager;
    private OfflineBrowserPagerAdapter adapter;
    private Boolean randomSelected = false;
    public static boolean fromSearch = false;
    private PrefHelper prefHelper;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.pager_layout, container, false);
        setHasOptionsMenu(true);
        prefHelper = ((MainActivity) getActivity()).getPrefHelper();

        if (((MainActivity) getActivity()).getProgressDialog() != null)
            ((MainActivity) getActivity()).getProgressDialog().dismiss();

        if (savedInstanceState != null) {
            sLastComicNumber = savedInstanceState.getInt("Last Comic");
        } else if (sLastComicNumber == 0) {
            sLastComicNumber = prefHelper.getLastComic();
        }

        sPager = (HackyViewPager) v.findViewById(R.id.pager);
        sPager.setOffscreenPageLimit(2);

        if (savedInstanceState == null && prefHelper.isOnline(getActivity()) && (prefHelper.isWifi(getActivity()) | prefHelper.mobileEnabled()) && !fromSearch) {
            new updateImages().execute();
        } else {
            sNewestComicNumber = prefHelper.getHighestOffline();
            if (sLastComicNumber != 0) {
                try {
                    Field field = ViewPager.class.getDeclaredField("mRestoredCurItem");
                    field.setAccessible(true);
                    field.set(sPager, sLastComicNumber - 1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            adapter = new OfflineBrowserPagerAdapter(getActivity());
            sPager.setAdapter(adapter);
        }

        sPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                try {
                    getActivity().invalidateOptionsMenu();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
                sLastComicNumber = position + 1;
                if (prefHelper.subtitleEnabled() && ((MainActivity) getActivity()).getCurrentFragment() == R.id.nav_browser)
                    ((MainActivity) getActivity()).getToolbar().setSubtitle(String.valueOf(sLastComicNumber));
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        return v;
    }

    public void updatePager() {
        new updateImages().execute();
    }

    public class updateImages extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog progress;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(getActivity());
            progress.setTitle(getResources().getString(R.string.loading_comics));
            progress.setCancelable(false);
            progress.show();
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        protected Boolean doInBackground(Void... pos) {
            boolean showSnackbar = false;
            try {
                sNewestComicNumber = new Comic(0).getComicNumber();
                if (sNewestComicNumber > prefHelper.getHighestOffline()) {
                    showSnackbar = prefHelper.getNotificationInterval() == 0 && sLastComicNumber != sNewestComicNumber;
                    for (int i = prefHelper.getHighestOffline(); i <= sNewestComicNumber; i++) {
                        Log.d("comic added", String.valueOf(i));
                        Comic comic = new Comic(i, getActivity());
                        String url = comic.getComicData()[2];
                        Bitmap mBitmap = Glide.with(getActivity())
                                .load(url)
                                .asBitmap()
                                .into(-1, -1)
                                .get();
                        try {
                            File sdCard = prefHelper.getOfflinePath();
                            File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                            dir.mkdirs();
                            File file = new File(dir, String.valueOf(i) + ".png");
                            FileOutputStream fos = new FileOutputStream(file);
                            mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            fos.flush();
                            fos.close();
                        } catch (Exception e) {
                            Log.e("Error", "Saving to external storage failed");
                            try {
                                FileOutputStream fos = getActivity().openFileOutput(String.valueOf(i), Context.MODE_PRIVATE);
                                mBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                                fos.close();
                            } catch (Exception e2) {
                                e2.printStackTrace();
                            }
                        }
                        prefHelper.addTitle(comic.getComicData()[0], i);
                        prefHelper.addAlt(comic.getComicData()[1], i);
                        prefHelper.setHighestOffline(sNewestComicNumber);
                        prefHelper.setNewestComic(i);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!prefHelper.nomediaCreated()) {
                File sdCard = prefHelper.getOfflinePath();
                File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
                File nomedia = new File(dir, ".nomedia");
                try {
                    boolean created = nomedia.createNewFile();
                    Log.d("created", String.valueOf(created));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return showSnackbar;
        }

        @Override
        protected void onPostExecute(Boolean showSnackbar) {
            progress.dismiss();
            if (((MainActivity) getActivity()).getProgressDialog() != null)
                ((MainActivity) getActivity()).getProgressDialog().dismiss();
            if (sLastComicNumber != 0) {
                try {
                    Field field = ViewPager.class.getDeclaredField("mRestoredCurItem");
                    field.setAccessible(true);
                    field.set(sPager, sLastComicNumber - 1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            adapter = new OfflineBrowserPagerAdapter(getActivity());
            sPager.setAdapter(adapter);
            if (showSnackbar) {
                View.OnClickListener oc = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getLatestComic();
                    }
                };
                FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.fab);
                Snackbar.make(fab, getActivity().getResources().getString(R.string.new_comic), Snackbar.LENGTH_LONG)
                        .setAction(getActivity().getResources().getString(R.string.new_comic_view), oc)
                        .show();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_favorite:
                return ModifyFavorites(item);

            case R.id.delete_favorites:
                return deleteAllFavorites();

            case R.id.action_share:
                return shareComic();

            case R.id.action_alt:
                return setAltText();

            case R.id.action_latest:
                return getLatestComic();

            case R.id.action_random:
                return getRandomComic();

            case R.id.action_explain:
                return explainComic(sLastComicNumber);

            case R.id.action_trans:
                return showTranscript();
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean getRandomComic() {
        if (sNewestComicNumber != 0) {
            sLastComicNumber = prefHelper.getRandomNumber(sLastComicNumber);
            randomSelected = true;
            sPager.setCurrentItem(sLastComicNumber - 1, false);
        }
        return true;
    }

    public void getPreviousRandom() {
        if (sNewestComicNumber != 0) {
            sLastComicNumber = prefHelper.getPreviousRandom(sLastComicNumber);
            randomSelected = true;
            sPager.setCurrentItem(sLastComicNumber - 1, false);
        }
    }

    private boolean shareComic() {
        if (prefHelper.shareImage()) {
            shareComicImage();
            return true;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setItems(R.array.share_dialog, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        shareComicImage();
                        break;
                    case 1:
                        shareComicUrl();
                        break;
                }
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
        return true;
    }

    private boolean ModifyFavorites(MenuItem item) {
        if (Favorites.checkFavorite(getActivity(), sLastComicNumber)) {
            new DeleteComicImageTask().execute();
            item.setIcon(R.drawable.ic_favorite_outline);
            return true;
        } else {
            //save image to internal storage
            new SaveComicImageTask().execute();
            item.setIcon(R.drawable.ic_action_favorite);
            return true;
        }
    }

    private class SaveComicImageTask extends AsyncTask<Void, Void, Void> {
        private int mAddedNumber = sLastComicNumber;

        @Override
        protected Void doInBackground(Void... params) {
            Favorites.addFavoriteItem(getActivity(), String.valueOf(mAddedNumber));
            return null;
        }

        @Override
        protected void onPostExecute(Void dummy) {
            //refresh the FavoritesFragment
            FavoritesFragment f = (FavoritesFragment) getActivity().getSupportFragmentManager().findFragmentByTag("favorites");
            if (f != null)
                f.refresh();
            //Sometimes the floating action button does not animate back to the bottom when the snackbar is dismissed, so force it to its original position
            ((MainActivity) getActivity()).getFab().forceLayout();
        }
    }

    private class DeleteComicImageTask extends AsyncTask<Integer, Void, Void> {
        private int mRemovedNumber = sLastComicNumber;
        private View.OnClickListener oc;

        @Override
        protected Void doInBackground(Integer... pos) {
            Favorites.removeFavoriteItem(getActivity(), String.valueOf(mRemovedNumber));
            oc = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new SaveComicImageTask().execute();
                }
            };
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

    private boolean deleteAllFavorites() {
        new android.support.v7.app.AlertDialog.Builder(getActivity())
                .setMessage(R.string.delete_favorites_dialog)
                .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Favorites.putStringInPreferences(getActivity(), null, "favorites");
                        android.support.v4.app.FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                        FavoritesFragment f = (FavoritesFragment) fragmentManager.findFragmentByTag("favorites");
                        if (f != null)
                            fragmentManager.beginTransaction().remove(f).commit();
                        getActivity().invalidateOptionsMenu();
                        Toast toast = Toast.makeText(getActivity(), R.string.favorites_cleared, Toast.LENGTH_SHORT);
                        toast.show();
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setCancelable(true)
                .show();
        return true;
    }

    private void shareComicUrl() {
        //shares the comics url along with its title
        Intent share = new Intent(android.content.Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, sComicMap.get(sLastComicNumber).getComicData()[0]);
        if (prefHelper.shareMobile()) {
            share.putExtra(Intent.EXTRA_TEXT, "http://m.xkcd.com/" + String.valueOf(sComicMap.get(sLastComicNumber).getComicNumber()));
        } else {
            share.putExtra(Intent.EXTRA_TEXT, "http://xkcd.com/" + String.valueOf(sComicMap.get(sLastComicNumber).getComicNumber()));
        }
        startActivity(Intent.createChooser(share, this.getResources().getString(R.string.share_url)));
    }

    private void shareComicImage() {
        //shares the comic's image along with its title
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/*");
        share.putExtra(Intent.EXTRA_STREAM, getURI());
        share.putExtra(Intent.EXTRA_SUBJECT, sComicMap.get(sLastComicNumber).getComicData()[0]);
        if (prefHelper.shareAlt()) {
            share.putExtra(Intent.EXTRA_TEXT, sComicMap.get(sLastComicNumber).getComicData()[1]);
        }
        startActivity(Intent.createChooser(share, this.getResources().getString(R.string.share_image)));
    }

    private Uri getURI() {
        File sdCard = prefHelper.getOfflinePath();
        File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
        File path = new File(dir, String.valueOf(sLastComicNumber) + ".png");
        return Uri.fromFile(path);
    }

    private boolean setAltText() {
        //If the user selected the menu item for the first time, show the toast
        if (prefHelper.showAltTip()) {
            Toast toast = Toast.makeText(getActivity(), R.string.action_alt_tip, Toast.LENGTH_LONG);
            toast.show();
            prefHelper.setAltTip(false);
        }
        //Show alt text
        TextView tvAlt = (TextView) sPager.findViewWithTag(sLastComicNumber - 1).findViewById(R.id.tvAlt);
        if (prefHelper.classicAltStyle()) {
            toggleVisibility(tvAlt);
        } else {
            android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
            mDialog.setMessage(tvAlt.getText());
            mDialog.show();
        }
        return true;
    }

    private boolean getLatestComic() {
        sLastComicNumber = sNewestComicNumber;
        sPager.setCurrentItem(sLastComicNumber - 1, false);
        return true;
    }

    private boolean showTranscript() {
        String trans = sComicMap.get(sLastComicNumber).getTranscript();
        android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
        mDialog.setMessage(trans);
        mDialog.show();
        return true;
    }

    private boolean explainComic(int number) {
        String url = "http://explainxkcd.com/" + String.valueOf(number);
        CustomTabsIntent.Builder intentBuilder = new CustomTabsIntent.Builder();
        TypedValue typedValue = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.colorPrimary, typedValue, true);
        intentBuilder.setToolbarColor(typedValue.data);
        CustomTabActivityHelper.openCustomTab(getActivity(), intentBuilder.build(), Uri.parse(url), new BrowserFallback());
        return true;
    }

    private class OfflineBrowserPagerAdapter extends PagerAdapter {
        Context mContext;
        LayoutInflater mLayoutInflater;
        Boolean fingerLifted = true;

        public OfflineBrowserPagerAdapter(Context context) {
            mContext = context;
            mLayoutInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            return sNewestComicNumber;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(ViewGroup container, final int position) {
            final View itemView = mLayoutInflater.inflate(R.layout.pager_item, container, false);
            final PhotoView pvComic = (PhotoView) itemView.findViewById(R.id.ivComic);
            final TextView tvAlt = (TextView) itemView.findViewById(R.id.tvAlt);
            itemView.setTag(position);

            if (position == sLastComicNumber - 1 && fromSearch) {
                fromSearch = false;
                ActivityTransition.with(getActivity().getIntent()).duration(300).to(pvComic).start(null);
            }
            if (prefHelper.altByDefault())
                tvAlt.setVisibility(View.VISIBLE);

            sComicMap.put(position + 1, new OfflineComic(position + 1, getActivity()));
            //Setup the title text view
            TextView tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);
            tvTitle.setText(sComicMap.get(position + 1).getComicData()[0]);
            tvAlt.setText(sComicMap.get(position + 1).getComicData()[1]);
            //load the image
            pvComic.setImageBitmap(sComicMap.get(position + 1).getBitmap());

            //fix for issue #2
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
                    if (position == 1571) {
                        String url = "https://docs.google.com/forms/d/1e8htNa3bn5OZIgv83dodjZAHcQ424pgQPcFqWz2xSG4/viewform?c=0&w=1";
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(url));
                        startActivity(intent);
                    }
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
                        if (prefHelper.altVibration()) {
                            Vibrator vi = (Vibrator) getActivity().getSystemService(MainActivity.VIBRATOR_SERVICE);
                            vi.vibrate(10);
                        }
                        if (prefHelper.classicAltStyle()) {
                            toggleVisibility(tvAlt);
                        } else {
                            android.support.v7.app.AlertDialog.Builder mDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
                            mDialog.setMessage(tvAlt.getText());
                            mDialog.show();
                        }
                    }
                    return true;
                }
            });
            if (Arrays.binarySearch(mContext.getResources().getIntArray(R.array.large_comics), sLastComicNumber) >= 0)
                pvComic.setMaximumScale(7.0f);

            if (prefHelper.invertColors()) {
                float[] colorMatrix_Negative = {
                        -1.0f, 0, 0, 0, 255, //red
                        0, -1.0f, 0, 0, 255, //green
                        0, 0, -1.0f, 0, 255, //blue
                        0, 0, 0, 1.0f, 0 //alpha
                };
                ColorFilter cf = new ColorMatrixColorFilter(colorMatrix_Negative);
                pvComic.setColorFilter(cf);
            }
            //Disable ViewPager scrolling when the user zooms into an image
            if (prefHelper.scrollDisabledWhileZoom())
                pvComic.setOnMatrixChangeListener(new PhotoViewAttacher.OnMatrixChangedListener() {
                    @Override
                    public void onMatrixChanged(RectF rectF) {
                        if (pvComic.getScale() > 1.4) {
                            sPager.setLocked(true);
                        } else {
                            sPager.setLocked(false);
                        }
                    }
                });

            if (randomSelected && position == sLastComicNumber - 1) {
                Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), android.R.anim.fade_in);
                itemView.setAnimation(animation);
                randomSelected = false;
            }

            if (position == sLastComicNumber - 1) {
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

            if (position == sLastComicNumber + 1) {
                switch (Integer.parseInt(prefHelper.getOrientation())) {
                    case 1:
                        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
                        break;
                    case 2:
                        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        break;
                    case 3:
                        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        break;
                }
            }
            container.addView(itemView);
            return itemView;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((RelativeLayout) object);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        //Update the favorites icon
        MenuItem fav = menu.findItem(R.id.action_favorite);
        if (Favorites.checkFavorite(getActivity(), sLastComicNumber)) {
            fav.setIcon(R.drawable.ic_action_favorite);
        } else {
            fav.setIcon(R.drawable.ic_favorite_outline);
        }
        //If the FAB is visible, hide the random comic menu item
        if (((MainActivity) getActivity()).getFab().getVisibility() == View.GONE) {
            menu.findItem(R.id.action_random).setVisible(true);
        } else {
            menu.findItem(R.id.action_random).setVisible(false);
        }
        super.onPrepareOptionsMenu(menu);
    }

    public void scrollTo(int pos, boolean smooth) {
        sPager.setCurrentItem(pos, smooth);
    }

    private void toggleVisibility(View view) {
        // Switches a view's visibility between GONE and VISIBLE
        if (view.getVisibility() == View.GONE) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    @Override
    public void onStop() {
        prefHelper.setLastComic(sLastComicNumber);
        super.onStop();
    }

    public static boolean zoomReset() {
        PhotoView pv = (PhotoView) sPager.findViewWithTag(sLastComicNumber - 1).findViewById(R.id.ivComic);
        float scale = pv.getScale();
        if (scale != 1f) {
            pv.setScale(1f, true);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putInt("Last Comic", sLastComicNumber);
        super.onSaveInstanceState(savedInstanceState);
    }
}
