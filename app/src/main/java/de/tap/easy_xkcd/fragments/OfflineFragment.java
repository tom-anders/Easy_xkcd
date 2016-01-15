package de.tap.easy_xkcd.fragments;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseArray;
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

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.Favorites;
import de.tap.easy_xkcd.utils.OfflineComic;
import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

public class OfflineFragment extends ComicFragment {
    private OfflineBrowserPagerAdapter adapter;
    private Boolean randomSelected = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflateLayout(R.layout.pager_layout, inflater, container, savedInstanceState);

        if (((MainActivity) getActivity()).getProgressDialog() != null)
            ((MainActivity) getActivity()).getProgressDialog().dismiss();


        if (savedInstanceState == null && prefHelper.isOnline(getActivity()) && (prefHelper.isWifi(getActivity()) | prefHelper.mobileEnabled()) && !fromSearch) {
            new updateImages().execute();
        } else {
            newestComicNumber = prefHelper.getHighestOffline();
            scrollViewPager();
            adapter = new OfflineBrowserPagerAdapter(getActivity());
            mPager.setAdapter(adapter);
        }

        mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                pageSelected(position);
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
            Log.d("info", "updateImages started");
        }

        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Override
        protected Boolean doInBackground(Void... pos) {
            boolean showSnackbar = false;
            try {
                newestComicNumber = new Comic(0).getComicNumber();
                if (newestComicNumber > prefHelper.getHighestOffline()) {
                    showSnackbar = prefHelper.getNotificationInterval() == 0 && lastComicNumber != newestComicNumber;
                    for (int i = prefHelper.getHighestOffline(); i <= newestComicNumber; i++) {
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
                        prefHelper.setHighestOffline(newestComicNumber);
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
            scrollViewPager();
            adapter = new OfflineBrowserPagerAdapter(getActivity());
            mPager.setAdapter(adapter);
            if (showSnackbar) {
                View.OnClickListener oc = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getLatestComic();
                    }
                };
                FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.fab);
                //noinspection ResourceType (android studio won't let you set a custom snackbar length)
                Snackbar.make(fab, getActivity().getResources().getString(R.string.new_comic), 4000)
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

            case R.id.action_share:
                return shareComic();

            case R.id.action_random:
                return getRandomComic();
        }
        return super.onOptionsItemSelected(item);
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
            return newestComicNumber;
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

            if (!prefHelper.defaultZoom()) {
                pvComic.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                pvComic.setMaximumScale(10f);
            }

            if (position == lastComicNumber - 1 && fromSearch) {
                fromSearch = false;
                ActivityTransition.with(getActivity().getIntent()).duration(300).to(pvComic).start(null);
            }
            if (prefHelper.altByDefault())
                tvAlt.setVisibility(View.VISIBLE);

            comicMap.put(position + 1, new OfflineComic(position + 1, getActivity()));
            //Setup the title text view
            TextView tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);
            tvTitle.setText(comicMap.get(position + 1).getComicData()[0]);
            tvAlt.setText(comicMap.get(position + 1).getComicData()[1]);
            //load the image
            pvComic.setImageBitmap(((OfflineComic) comicMap.get(position + 1)).getBitmap());

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
            if (Arrays.binarySearch(mContext.getResources().getIntArray(R.array.large_comics), lastComicNumber) >= 0)
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
            if (prefHelper.scrollDisabledWhileZoom() && prefHelper.defaultZoom())
                pvComic.setOnMatrixChangeListener(new PhotoViewAttacher.OnMatrixChangedListener() {
                    @Override
                    public void onMatrixChanged(RectF rectF) {
                        if (pvComic.getScale() > 1.4) {
                            mPager.setLocked(true);
                        } else {
                            mPager.setLocked(false);
                        }
                    }
                });

            if (randomSelected && position == lastComicNumber - 1) {
                Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), android.R.anim.fade_in);
                itemView.setAnimation(animation);
                randomSelected = false;
            }

            if (position == lastComicNumber - 1) {
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

            if (position == lastComicNumber + 2) {
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

    /******************** Random Comics ***************************************/

    @Override
    public boolean getRandomComic() {
        if (newestComicNumber != 0) {
            randomSelected = true;
            return super.getRandomComic();
        }
        return true;
    }

    @Override
    public void getPreviousRandom() {
        if (newestComicNumber != 0) {
            randomSelected = true;
            super.getRandomComic();
        }
    }

    /************************* Favorite Modification ************************/

    private boolean ModifyFavorites(MenuItem item) {
        if (Favorites.checkFavorite(getActivity(), lastComicNumber)) {
            new DeleteComicImageTask().execute(false);
            item.setIcon(R.drawable.ic_favorite_outline);
        } else {
            //save image to internal storage
            new SaveComicImageTask().execute(false);
            item.setIcon(R.drawable.ic_action_favorite);
        }
        return true;
    }

    /************************************Sharing*********************************/

    protected boolean shareComic() {
        if (prefHelper.shareImage()) {
            shareComicImage(getURI());
            return true;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setItems(R.array.share_dialog, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        shareComicImage(getURI());
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

    private Uri getURI() {
        File sdCard = prefHelper.getOfflinePath();
        File dir = new File(sdCard.getAbsolutePath() + "/easy xkcd");
        File path = new File(dir, String.valueOf(lastComicNumber) + ".png");
        return Uri.fromFile(path);
    }

}
