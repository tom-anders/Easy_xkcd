package de.tap.easy_xkcd.fragments;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;
import com.bumptech.glide.request.target.Target;
import com.kogitune.activity_transition.ActivityTransition;
import com.tap.xkcd_reader.R;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import de.tap.easy_xkcd.Activities.MainActivity;
import de.tap.easy_xkcd.utils.Comic;
import de.tap.easy_xkcd.utils.Favorites;
import de.tap.easy_xkcd.utils.OfflineComic;
import uk.co.senab.photoview.PhotoView;

public class OfflineFragment extends ComicFragment {
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
            pager.setAdapter(adapter);
        }

        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
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

    @Override
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
                        saveComic(i, mBitmap);
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
            pager.setAdapter(adapter);
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

    private class OfflineBrowserPagerAdapter extends ComicAdapter {

        public OfflineBrowserPagerAdapter(Context context) {
            super(context);
        }

        @Override
        public int getCount() {
            return newestComicNumber;
        }

        @Override
        public Object instantiateItem(ViewGroup container, final int position) {
            View itemView = setupPager(container, position);
            PhotoView pvComic = (PhotoView) itemView.findViewById(R.id.ivComic);
            TextView tvAlt = (TextView) itemView.findViewById(R.id.tvAlt);
            TextView tvTitle = (TextView) itemView.findViewById(R.id.tvTitle);

            comicMap.put(position + 1, new OfflineComic(position + 1, getActivity()));

            tvTitle.setText(comicMap.get(position + 1).getComicData()[0]);
            tvAlt.setText(comicMap.get(position + 1).getComicData()[1]);
            if (fromSearch) {
                fromSearch = false;
                ActivityTransition.with(getActivity().getIntent()).duration(300).to(pvComic).start(null);
            }
            if (getGifId(position) != 0)
                Glide.with(getActivity())
                        .load(getGifId(position))
                        .into(new GlideDrawableImageViewTarget(pvComic));
            else
                pvComic.setImageBitmap(((OfflineComic) comicMap.get(position + 1)).getBitmap());

            if (randomSelected && position == lastComicNumber - 1) {
                Animation animation = AnimationUtils.loadAnimation(getActivity().getApplicationContext(), android.R.anim.fade_in);
                itemView.setAnimation(animation);
                randomSelected = false;
            }

            if (Arrays.binarySearch(mContext.getResources().getIntArray(R.array.large_comics), lastComicNumber) >= 0)
                pvComic.setMaximumScale(7.0f);

            if (position == lastComicNumber - 1)
                animateToolbar();

            container.addView(itemView);
            return itemView;
        }
    }

    /********************
     * Random Comics
     ***************************************/

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

    /*************************
     * Favorite Modification
     ************************/

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

    /************************************
     * Sharing
     *********************************/

    protected boolean shareComic() {
        if (prefHelper.shareImage()) {
            shareComicImage(getURI(lastComicNumber), comicMap.get(lastComicNumber));
            return true;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setItems(R.array.share_dialog, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        shareComicImage(getURI(lastComicNumber), comicMap.get(lastComicNumber));
                        break;
                    case 1:
                        shareComicUrl(comicMap.get(lastComicNumber));
                        break;
                }
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
        return true;
    }

}
